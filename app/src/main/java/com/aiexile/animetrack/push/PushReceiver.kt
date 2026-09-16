package com.aiexile.animetrack.push

import android.content.Context
import android.util.Log
import cn.jpush.android.api.NotificationMessage
import cn.jpush.android.service.JPushMessageReceiver

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
        Log.d(TAG, "JPush connected: $isConnected")
    }

    override fun onRegister(context: Context, registrationId: String) {
        super.onRegister(context, registrationId)
        Log.d(TAG, "JPush registrationId: $registrationId")
    }

    override fun onNotifyMessageArrived(context: Context, message: NotificationMessage) {
        super.onNotifyMessageArrived(context, message)
        Log.d(TAG, "Notification arrived: ${message.notificationContent}")
    }

    override fun onNotifyMessageOpened(context: Context, message: NotificationMessage) {
        super.onNotifyMessageOpened(context, message)
        Log.d(TAG, "Notification opened: ${message.notificationContent}")
    }
}
