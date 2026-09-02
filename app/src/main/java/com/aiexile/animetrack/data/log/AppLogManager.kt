package com.aiexile.animetrack.data.log

import android.content.Context
import android.os.Build
import android.util.Log
import com.aiexile.animetrack.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 应用文件日志系统（反馈界面「附带 App 日志」的数据源）：
 * - 单线程异步写入 files/logs/app-yyyyMMdd.log，按天轮转，仅保留最近 [RETAIN_DAYS] 天
 * - 单日文件超过 [MAX_LOG_FILE_BYTES] 后停止写入，防止异常刷屏占满磁盘
 * - 捕获未处理崩溃，崩溃堆栈同步落盘（进程即将退出，不能依赖异步队列）
 * - [exportFeedbackLog] 将环境信息 + 最近日志打包为 zip，供反馈界面以 log 类型附件上传
 *
 * 日志仅记录运行行为与错误信息，不写入用户反馈内容、账号凭证等敏感数据。
 */
object AppLogManager {

    private const val TAG = "AppLog"

    /** 日志目录（filesDir/logs），按天一个文件 */
    private const val LOG_DIR = "logs"
    private const val FILE_PREFIX = "app-"

    /** 日志保留天数 */
    private const val RETAIN_DAYS = 3

    /** 单日日志文件大小上限，超限后当日停止写入 */
    private const val MAX_LOG_FILE_BYTES = 2L * 1024 * 1024

    /** 导出原始文本上限（zip 后远小于附件 20MB 限制），超限优先保留最新日志 */
    private const val MAX_EXPORT_RAW_BYTES = 4L * 1024 * 1024

    /** 导出 zip 的临时目录与压缩包内文件名 */
    private const val EXPORT_DIR = "feedback_log"
    private const val EXPORT_FILE = "app_log.zip"
    private const val EXPORT_ENTRY = "app_log.txt"

    private val lineTimeFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")
    private val headerTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private lateinit var appContext: Context

    /** 单线程异步落盘：不阻塞调用线程，且保证同一文件的写入天然串行 */
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "app-log").apply { isDaemon = true }
    }

    /** 当前写入状态：经 [lock] 串行访问（异步写线程 + 崩溃同步写入） */
    private var currentFile: File? = null
    private var currentFileSize = 0L
    private var currentDay: String? = null
    private var truncatedToday = false

    private val lock = Any()

    @Volatile
    private var initialized = false

    /** Application.onCreate 中调用，须早于其他组件使用；幂等 */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        i(
            TAG,
            "App 启动: v${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) " +
                "Android ${Build.VERSION.RELEASE}(API ${Build.VERSION.SDK_INT})"
        )
        installCrashHandler()
    }

    // ===== 写入 API（未初始化时静默丢弃；同时镜像到 logcat，调用点一处即可双通道输出） =====

    fun d(tag: String, message: String) = write("D", tag, message, null)

    fun i(tag: String, message: String) = write("I", tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable? = null) = write("W", tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) = write("E", tag, message, throwable)

    private fun write(level: String, tag: String, message: String, throwable: Throwable?) {
        if (!initialized) return
        mirrorToLogcat(level, tag, message, throwable)
        val line = formatLine(level, tag, message, throwable)
        executor.execute { appendLocked(line) }
    }

    /** 同步镜像到 logcat，保持原本 Log.x 的实时调试能力 */
    private fun mirrorToLogcat(level: String, tag: String, message: String, throwable: Throwable?) {
        val priority = when (level) {
            "D" -> Log.DEBUG
            "I" -> Log.INFO
            "W" -> Log.WARN
            else -> Log.ERROR
        }
        val text = if (throwable != null) {
            "$message\n${Log.getStackTraceString(throwable)}"
        } else {
            message
        }
        Log.println(priority, tag, text)
    }

    // ===== 导出（反馈日志附件） =====

    /**
     * 导出反馈日志附件：环境信息 + 最近日志 → cacheDir/feedback_log/app_log.zip。
     * 超过 [MAX_EXPORT_RAW_BYTES] 时从最旧的文件开始舍弃（优先保留最新日志）。
     * 每次导出覆盖上一次产物；失败或无日志时返回 null。
     */
    suspend fun exportFeedbackLog(): File? = withContext(Dispatchers.IO) {
        if (!initialized) return@withContext null
        try {
            // 等待异步队列中的日志落盘，保证导出包含最新内容
            executor.submit { }.get(5, TimeUnit.SECONDS)

            val logChunks = synchronized(lock) {
                File(appContext.filesDir, LOG_DIR).listFiles()
                    ?.filter { it.name.startsWith(FILE_PREFIX) }
                    ?.sortedBy { it.name } // 文件名含日期，即按时间升序
                    .orEmpty()
                    .map { it.readBytes() } // 锁内读取，避免与写入竞争
            }

            val chunks = ArrayDeque<ByteArray>()
            var total = 0L
            for (bytes in logChunks.asReversed()) {
                if (total + bytes.size > MAX_EXPORT_RAW_BYTES) break
                chunks.addFirst(bytes)
                total += bytes.size
            }

            val outDir = File(appContext.cacheDir, EXPORT_DIR)
            outDir.mkdirs()
            outDir.listFiles()?.forEach { it.delete() }
            val outFile = File(outDir, EXPORT_FILE)
            ZipOutputStream(FileOutputStream(outFile)).use { zip ->
                zip.putNextEntry(ZipEntry(EXPORT_ENTRY))
                zip.write(environmentHeader())
                for (bytes in chunks) zip.write(bytes)
                zip.closeEntry()
            }
            outFile.takeIf { it.length() > 0 }
        } catch (_: Throwable) {
            null
        }
    }

    /** 导出日志头部：设备与应用环境信息，便于服务端定位问题 */
    private fun environmentHeader(): ByteArray = buildString {
        appendLine("===== AnimeTrack App Log =====")
        appendLine("导出时间: ${LocalDateTime.now().format(headerTimeFormat)}")
        appendLine("应用版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("系统版本: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("设备: ${Build.BRAND} ${Build.MODEL}")
        appendLine("日志范围: 最近 $RETAIN_DAYS 天")
        appendLine("===============================")
        appendLine()
    }.toByteArray()

    // ===== 内部实现 =====

    /** 异步写入一行日志（含日期轮转与大小保护） */
    private fun appendLocked(line: String) {
        synchronized(lock) {
            rollIfNeededLocked()
            val file = currentFile ?: return
            if (currentFileSize >= MAX_LOG_FILE_BYTES) {
                if (!truncatedToday) {
                    truncatedToday = true
                    val notice = formatLine("W", TAG, "单日日志超过上限，当日已截断")
                    appendBytesLocked(file, notice)
                }
                return
            }
            appendBytesLocked(file, line)
        }
    }

    /** 追加字节到当前文件并维护大小计数，IO 异常静默忽略（日志失败不应影响主流程） */
    private fun appendBytesLocked(file: File, line: String) {
        try {
            val bytes = line.toByteArray()
            FileOutputStream(file, true).use { it.write(bytes) }
            currentFileSize += bytes.size
        } catch (_: Throwable) {
        }
    }

    /** 日期变化时切换文件（同日重启续写），并清理过期日志 */
    private fun rollIfNeededLocked() {
        val today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        if (currentDay == today && currentFile != null) return
        currentDay = today
        truncatedToday = false
        val file = File(File(appContext.filesDir, LOG_DIR), "$FILE_PREFIX$today.log")
        file.parentFile?.mkdirs()
        currentFile = file
        currentFileSize = if (file.exists()) file.length() else 0L
        cleanExpiredLocked()
    }

    /** 仅保留最近 [RETAIN_DAYS] 个日志文件（按文件名即日期排序） */
    private fun cleanExpiredLocked() {
        val files = File(appContext.filesDir, LOG_DIR).listFiles()
            ?.filter { it.name.startsWith(FILE_PREFIX) }
            ?: return
        files.sortedBy { it.name }.dropLast(RETAIN_DAYS).forEach { it.delete() }
    }

    /** 记录未处理崩溃后交回原 Handler，保持系统崩溃流程（如系统崩溃对话框）不变 */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val line = formatLine("E", "CRASH", "未处理异常（线程 ${thread.name}）", throwable)
                synchronized(lock) {
                    rollIfNeededLocked()
                    val file = currentFile
                    if (file != null && currentFileSize < MAX_LOG_FILE_BYTES) {
                        val bytes = line.toByteArray()
                        FileOutputStream(file, true).use { it.write(bytes) }
                        currentFileSize += bytes.size
                    }
                }
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 单行日志格式：MM-dd HH:mm:ss.SSS L/Tag: message + 可选堆栈 */
    private fun formatLine(level: String, tag: String, message: String, throwable: Throwable? = null): String {
        val time = LocalDateTime.now().format(lineTimeFormat)
        val stack = throwable?.let { tr ->
            StringWriter().also { tr.printStackTrace(PrintWriter(it)) }.toString().trimEnd()
        }
        return buildString {
            append(time).append(' ').append(level).append('/').append(tag).append(": ").append(message)
            stack?.let {
                append('\n').append(it)
            }
            append('\n')
        }
    }
}
