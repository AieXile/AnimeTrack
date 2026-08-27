package com.aiexile.animetrack.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/** 自建更新服务器接口（https://www.aiexile.top/） */
interface UpdateApi {

    /** 检查更新，返回最新版本信息；abi 为设备架构（arm64-v8a / armeabi-v7a / universal），服务器据此返回对应分包 */
    @GET("update")
    suspend fun getUpdate(@Query("abi") abi: String): ServerUpdateResponse
}
