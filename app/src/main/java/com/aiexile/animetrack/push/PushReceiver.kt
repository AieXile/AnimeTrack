package com.aiexile.animetrack.push

import android.content.Context
import cn.jpush.android.api.NotificationMessage
import cn.jpush.android.service.JPushMessageReceiver
import com.aiexile.animetrack.data.log.AppLogManager

/**
 * JPush 消息接收器：仅保留每日番剧更新推送（通知栏消息）相关回调。
 * 原透传（CustomMessage）链路已由 SSE 实时事件（data/sse/SseClient）替代。
 */
class PushReceiver : JPushMessageReceiver() {

    companion object {
        private const val TAG = "PushReceiver"
    }

    override fun onConnected(context: Context, isConnected: Boolean) {
        super.onConnected(context, isConnected)
        AppLogManager.i(TAG, "JPush connected: $isConnected")
    }

    override fun onRegister(context: Context, registrationId: String) {
        super.onRegister(context, registrationId)
        AppLogManager.i(TAG, "JPush registrationId: $registrationId")
    }

    override fun onNotifyMessageArrived(context: Context, message: NotificationMessage) {
        super.onNotifyMessageArrived(context, message)
        AppLogManager.i(TAG, "Notification arrived: ${message.notificationContent}")
    }

    override fun onNotifyMessageOpened(context: Context, message: NotificationMessage) {
        super.onNotifyMessageOpened(context, message)
        AppLogManager.i(TAG, "Notification opened: ${message.notificationContent}")
    }
}
