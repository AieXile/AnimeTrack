package com.aiexile.animetrack.push

import android.content.Context
import android.util.Log
import cn.jpush.android.api.NotificationMessage
import cn.jpush.android.service.JPushMessageReceiver

class PushReceiver : JPushMessageReceiver() {

    override fun onConnected(context: Context, isConnected: Boolean) {
        super.onConnected(context, isConnected)
        Log.d("PushReceiver", "JPush connected: $isConnected")
    }

    override fun onRegister(context: Context, registrationId: String) {
        super.onRegister(context, registrationId)
        Log.d("PushReceiver", "JPush registrationId: $registrationId")
    }

    override fun onNotifyMessageArrived(context: Context, message: NotificationMessage) {
        super.onNotifyMessageArrived(context, message)
        Log.d("PushReceiver", "Notification arrived: ${message.notificationContent}")
    }

    override fun onNotifyMessageOpened(context: Context, message: NotificationMessage) {
        super.onNotifyMessageOpened(context, message)
        Log.d("PushReceiver", "Notification opened: ${message.notificationContent}")
    }
}
