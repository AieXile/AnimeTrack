package com.aiexile.animetrack.data.network

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 公开（无鉴权）用户体系 API。
 *
 * 用于不要求登录的接口（如设备活跃上报）。与 [UserAuthApiService] 共享 baseUrl，
 * 但走 [RetrofitClient.publicUserAuthApi]（无 Authorization Bearer token 注入），
 * 因此请求不会因 401 被拦截。
 */
interface PublicUserAuthApiService {

    // ========== 设备活跃上报（匿名/已登录均可用） ==========

    /**
     * 上报设备当日活跃。服务端按 (device_id, activity_date) 去重写入。
     * 若 [request.userId] 非空，服务端会一并记录关联用户（便于注册转化率统计）。
     */
    @POST("device/activity")
    suspend fun reportDeviceActivity(@Body request: DeviceActivityRequest): ActivityReportResponse
}

/**
 * 设备活跃上报请求体
 * @param deviceId 设备唯一标识（UUID，App 端首次生成并持久化）
 * @param userId 已登录用户的 ID；未登录传 null
 */
data class DeviceActivityRequest(
    val deviceId: String,
    val userId: Long? = null
)
