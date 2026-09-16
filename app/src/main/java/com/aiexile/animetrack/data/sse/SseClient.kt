package com.aiexile.animetrack.data.sse

import com.aiexile.animetrack.data.auth.UserAuthManager
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.network.RetrofitClient
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Request
import java.io.IOException

/** SSE 事件类型（与服务端 eventBus.js 约定一致） */
object SseEventTypes {
    /** 管理员回复了用户反馈（反馈红点/胶囊即时刷新） */
    const val FEEDBACK_REPLY = "feedback_reply"

    /** 会话被撤销/设备被下线（立即标记 token 失效并显示横幅） */
    const val SESSION_KICKED = "session_kicked"

    /** 版本发布广播（触发更新检查） */
    const val APP_UPDATE = "app_update"
}

/** 服务端推送的 SSE 事件（负载字段按类型可选） */
data class SseEvent(
    val type: String,
    val sessionId: String? = null,
    val version: String? = null
)

/**
 * SSE 实时事件客户端。
 *
 * 生命周期：App 前台保持连接（MainActivity.onStart 启动），切后台断开
 * （onStop 停止），回前台重连。未登录或 token 已失效时不连接。
 *
 * 断线重连：指数退避（1s 起、60s 封顶）加随机抖动；连接存活超过 30 秒
 * 视为健康连接，断开后重置退避。服务端每 30 秒发送心跳注释行，客户端
 * 读超时设为 90 秒（RetrofitClient.sseOkHttpClient），心跳丢失即断开重连。
 *
 * 事件分发：[events] SharedFlow，由各消费方按 type 过滤处理
 * （踢下线在 Application、更新广播在 HomeViewModel、反馈红点在 UI 层）。
 */
class SseClient(private val userAuthManager: UserAuthManager) {

    companion object {
        private const val TAG = "SseClient"

        /** 初始重连退避 */
        private const val INITIAL_BACKOFF_MS = 1000L

        /** 重连退避上限 */
        private const val MAX_BACKOFF_MS = 60 * 1000L

        /** 连接存活超过该时长视为健康，断开后重置退避 */
        private const val HEALTHY_CONNECTION_MS = 30 * 1000L

        /** 未登录时的复查间隔 */
        private const val RECHECK_INTERVAL_MS = 5000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<SseEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    /** 服务端推送的实时事件流 */
    val events: SharedFlow<SseEvent> = _events.asSharedFlow()

    /** 是否已由前台生命周期启动（start/stop 幂等） */
    @Volatile
    private var started = false

    /** 登录状态观察协程（start 时创建、stop 时取消） */
    private var lifecycleJob: Job? = null

    /** 连接循环协程（登录期间保持，登出/停止时取消） */
    private var connectJob: Job? = null

    /** 当前进行中的 SSE 请求（断开时主动 cancel） */
    @Volatile
    private var currentCall: Call? = null

    /** 前台启动：观察登录状态并在已登录时维持连接 */
    fun start() {
        if (started) return
        started = true
        lifecycleJob = scope.launch {
            userAuthManager.isLoggedIn.collect { loggedIn ->
                if (loggedIn) startConnectLoop() else stopConnectLoop()
            }
        }
    }

    /** 切后台停止：断开连接并取消登录状态观察 */
    fun stop() {
        started = false
        lifecycleJob?.cancel()
        lifecycleJob = null
        stopConnectLoop()
    }

    private fun startConnectLoop() {
        if (connectJob?.isActive == true) return
        connectJob = scope.launch {
            var backoffMs = INITIAL_BACKOFF_MS
            while (isActive && started) {
                // token 已失效（被踢/过期）时不再连，等待重新登录
                if (userAuthManager.tokenExpired.first()) {
                    delay(RECHECK_INTERVAL_MS)
                    continue
                }
                val connectedAt = System.currentTimeMillis()
                val healthy = try {
                    connectOnce()
                    System.currentTimeMillis() - connectedAt >= HEALTHY_CONNECTION_MS
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogManager.w(TAG, "SSE 连接异常: ${e.message}")
                    false
                }
                if (!isActive || !started) break
                backoffMs = if (healthy) INITIAL_BACKOFF_MS else (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                // 抖动：±20%，避免大量设备同时重连冲击服务器
                val jitter = (backoffMs * 0.2 * (Math.random() * 2 - 1)).toLong()
                delay(backoffMs + jitter)
            }
        }
    }

    private fun stopConnectLoop() {
        connectJob?.cancel()
        connectJob = null
        currentCall?.cancel()
        currentCall = null
    }

    /**
     * 建立一次 SSE 连接并阻塞读取事件，直到连接断开。
     * 非流式响应（如 403 JSON）直接关闭按失败返回。
     */
    private suspend fun connectOnce() {
        val request = Request.Builder()
            .url(RetrofitClient.sseEventsUrl)
            .header("Accept", "text/event-stream")
            .build()
        val call = RetrofitClient.sseOkHttpClient.newCall(request)
        currentCall = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("SSE HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("SSE 空响应体")
                val reader = body.byteStream().bufferedReader(Charsets.UTF_8)
                AppLogManager.i(TAG, "SSE 已连接")
                while (started) {
                    val line = reader.readLine() ?: break // 服务端关闭
                    when {
                        line.startsWith(":") -> Unit // 心跳注释行，忽略
                        line.startsWith("data:") -> parseEvent(line.removePrefix("data:").trim())
                    }
                }
            }
        } finally {
            currentCall = null
        }
    }

    private fun parseEvent(data: String) {
        if (data.isEmpty()) return
        val json: JsonObject = try {
            JsonParser.parseString(data).asJsonObject
        } catch (_: Exception) {
            AppLogManager.w(TAG, "SSE 事件解析失败: $data")
            return
        }
        val type = json.get("type")?.takeIf { it.isJsonPrimitive }?.asString ?: return
        _events.tryEmit(
            SseEvent(
                type = type,
                sessionId = json.get("sessionId")?.takeIf { it.isJsonPrimitive }?.asString,
                version = json.get("version")?.takeIf { it.isJsonPrimitive }?.asString
            )
        )
        AppLogManager.i(TAG, "SSE 事件: $type")
    }
}
