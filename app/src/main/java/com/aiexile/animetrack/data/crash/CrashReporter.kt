package com.aiexile.animetrack.data.crash

import android.content.Context
import com.aiexile.animetrack.BuildConfig
import com.aiexile.animetrack.data.auth.DeviceInfo
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.network.CrashReportRequest
import com.aiexile.animetrack.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.DataInputStream
import java.io.File
import java.io.IOException

/**
 * 崩溃捕获与上报（基于 xCrash）：
 * - 补齐自研日志捕获不了的 Native 崩溃与 ANR，tombstone 写入 [LOG_DIR]
 * - rethrow 保持默认 true：崩溃链为 xCrash 写 tombstone → AppLogManager 写崩溃日志 → 系统默认处理，
 *   两条记录通道互不影响
 * - 进程崩溃后无法当场联网，故在下次启动时静默上报未上传的 tombstone（无需登录）
 * - 上报成功删除本地文件；4xx（服务器明确拒绝）同样删除防止无限重试，网络失败/5xx 保留待重试
 */
object CrashReporter {

    private const val TAG = "CrashReporter"

    /** tombstone 目录（filesDir/tombstones，与 xCrash 初始化保持一致） */
    private const val LOG_DIR = "tombstones"

    /** 单次启动最多上报数量，避免崩溃风暴时启动即打满网络 */
    private const val MAX_UPLOAD_PER_LAUNCH = 5

    /** 单个 tombstone 上报内容上限，超出截断（服务端按同上限校验） */
    private const val MAX_CONTENT_BYTES = 1L * 1024 * 1024

    private lateinit var logDirFile: File

    /** Application.onCreate 中调用，须晚于 [AppLogManager.init]（保证 rethrow 链正确）；幂等由 xCrash 保证 */
    fun init(context: Context) {
        logDirFile = File(context.filesDir, LOG_DIR)
        val result = xcrash.XCrash.init(
            context,
            xcrash.XCrash.InitParameters()
                .setLogDir(logDirFile.absolutePath)
                // ANR tombstone 体积大（含完整 ART dump），保留份数少于崩溃日志
                .setAnrLogCountMax(5)
        )
        if (result != 0) {
            // 非 0 = native 捕获初始化失败（如 so 加载失败），Java 崩溃捕获不受影响
            AppLogManager.w(TAG, "xCrash native 初始化失败，错误码 $result（Java 崩溃捕获不受影响）")
        }
    }

    /** 启动后静默上报本地 tombstone，在 IO 线程执行；无 tombstone 或网络异常时静默结束 */
    suspend fun reportPendingTombstones() = withContext(Dispatchers.IO) {
        val files = listTombstones().take(MAX_UPLOAD_PER_LAUNCH)
        if (files.isEmpty()) return@withContext

        val deviceId = try {
            DeviceInfo.getDeviceId()
        } catch (_: Exception) {
            null
        }

        var uploaded = 0
        for (file in files) {
            val type = crashTypeOf(file.name) ?: continue
            val content = readContentCapped(file) ?: continue
            try {
                val response = RetrofitClient.publicUserAuthApi.reportCrash(
                    CrashReportRequest(
                        deviceId = deviceId,
                        fileName = file.name,
                        type = type,
                        appVersion = BuildConfig.VERSION_NAME,
                        content = content
                    )
                )
                if (response.success) {
                    uploaded++
                } else {
                    // 业务拒绝（如内容校验不通过），重试无意义
                    AppLogManager.w(TAG, "tombstone 上报被拒绝，已丢弃: ${file.name}, ${response.message}")
                }
                file.delete()
            } catch (e: HttpException) {
                if (e.code() in 400..499 && e.code() != 429) {
                    AppLogManager.w(TAG, "tombstone 上报失败(${e.code()})，已丢弃: ${file.name}")
                    file.delete()
                } else {
                    AppLogManager.w(TAG, "tombstone 上报失败(${e.code()})，待下次重试: ${file.name}")
                }
            } catch (e: IOException) {
                AppLogManager.w(TAG, "tombstone 上报网络失败，待下次重试: ${file.name}, ${e.message}")
            }
        }
        if (uploaded > 0) {
            AppLogManager.i(TAG, "已上报 $uploaded 个崩溃 tombstone")
        }
    }

    /** 列出待上报 tombstone：按文件名升序（文件名含 20 位时间戳，即时间序） */
    private fun listTombstones(): List<File> =
        logDirFile.listFiles()
            ?.filter { it.isFile && it.name.startsWith("tombstone_") && crashTypeOf(it.name) != null }
            ?.sortedBy { it.name }
            .orEmpty()

    /** 从文件名后缀解析崩溃类型，非 tombstone 主文件（emergency/placeholder/trace）返回 null */
    private fun crashTypeOf(fileName: String): String? = when {
        fileName.endsWith(".java.xcrash") -> "java"
        fileName.endsWith(".native.xcrash") -> "native"
        fileName.endsWith(".anr.xcrash") -> "anr"
        else -> null
    }

    /** 读取 tombstone 内容，超过 [MAX_CONTENT_BYTES] 截断并追加截断标记；读取失败返回 null */
    private fun readContentCapped(file: File): String? = try {
        if (file.length() > MAX_CONTENT_BYTES) {
            val buffer = ByteArray(MAX_CONTENT_BYTES.toInt())
            DataInputStream(file.inputStream()).use { it.readFully(buffer) }
            String(buffer, Charsets.UTF_8) + "\n\n[CrashReporter] content truncated at 1MB"
        } else {
            file.readBytes().toString(Charsets.UTF_8)
        }
    } catch (_: Exception) {
        null
    }
}
