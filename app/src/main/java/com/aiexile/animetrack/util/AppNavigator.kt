package com.aiexile.animetrack.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object AppNavigator {

    private const val QQ_GROUP_URL =
        "https://qun.qq.com/universal-share/share?ac=1&authKey=tjvRUO11WgV3IYxi3aioSu2TvRZOaePAu7zxL6rHGwJGY%2Fs%2F4PiixXrZFlB6foyE&busi_data=eyJncm91cENvZGUiOiI5NTEwNTkxNzgiLCJ0b2tlbiI6IlBNcGo3OE9OQ0pjV3FaVVJkdDh4TU1NdHR4aHdpVnNwVXA3aHZBcVpkOTFkaElWUysyUDl3RDRwV09PV0lKRWUiLCJ1aW4iOiIxMjE5NTc2NDA4In0%3D&data=aT9_QRFbXvkTG-xsOgKnazrZf66Qbxd44RaO7nz4mKuIMKHLzdJagmZFJWa_FbFFxMW03uxmz79KBSmvBygp4Q&svctype=4&tempid=h5_group_info"

    fun joinAnimeTrackGroup(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(QQ_GROUP_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "未安装 QQ 或无法跳转", Toast.LENGTH_SHORT).show()
        }
    }
}