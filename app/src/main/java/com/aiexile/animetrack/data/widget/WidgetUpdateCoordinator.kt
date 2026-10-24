package com.aiexile.animetrack.data.widget

import android.content.Context
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * 组件事件驱动刷新入口。
 *
 * app 进程存活期间观察 anime 表：任何本地写入（进度、状态、删除、同步落库）都会
 * 在防抖后触发一次组件重绘。批量写入（如更新检查的批量事务）只会产生一次刷新。
 * 进程被杀后不再观察，由每日同步与午夜刷新兜底。
 */
object WidgetUpdateCoordinator {

    private const val DEBOUNCE_MS = 500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var debounceJob: Job? = null

    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            WidgetSyncScheduler.requestWidgetUpdates(appContext)
        }
    }

    @OptIn(FlowPreview::class)
    fun observeAnimeChanges(context: Context) {
        val appContext = context.applicationContext
        if (!WidgetSyncScheduler.isMainProcess(appContext)) return
        scope.launch {
            // 只订阅表变更通知，不整表读取：避免每次写库都反序列化全部番剧
            AppContainer.getAnimeDatabase().invalidationTracker.createFlow("anime")
                .debounce(DEBOUNCE_MS)
                .collect {
                    WidgetSyncScheduler.requestWidgetUpdates(appContext)
                }
        }
    }
}
