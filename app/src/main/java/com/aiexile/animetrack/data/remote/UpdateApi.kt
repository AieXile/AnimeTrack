package com.aiexile.animetrack.data.remote

import retrofit2.http.GET

/** 自建更新服务器接口（https://www.aiexile.top/） */
interface UpdateApi {

    /** 检查更新，返回最新版本信息 */
    @GET("update")
    suspend fun getUpdate(): ServerUpdateResponse
}
