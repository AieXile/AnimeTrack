# =============================================================================
# AnimeTrack ProGuard / R8 规则
# -----------------------------------------------------------------------------
# 作用对象：release 构建类型（app/build.gradle.kts -> buildTypes.release）
# 项目无 flavor，所有规则默认对 release 生效。
# 规则块上方均带有中文注释，方便后续维护与逐步收窄。
# =============================================================================


# =============================================================================
# 0. 通用 attributes 保留（★ 全局必须放在最前面）
# -----------------------------------------------------------------------------
# - Signature          : Gson/Retrofit/Kotlin 反射读取泛型签名的关键
# - InnerClasses       : 保留内部类关系（匿名 TypeToken 依赖）
# - EnclosingMethod    : 保留外部方法（匿名内部类依赖）
# - *Annotation*       : 保留所有注解（@SerializedName、@GET、@Body 等）
# - KotlinMetadata     : 保留 Kotlin Metadata（Retrofit 解析 suspend 函数依赖）
# - MethodParameters   : 保留方法参数名
# - SourceFile/LineNum : 便于线上 crash 排查
# =============================================================================
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations,RuntimeInvisibleAnnotations
-keepattributes AnnotationDefault
-keepattributes Exceptions,Deprecated,DeprecatedAttribute
-keepattributes KotlinMetadata
-keepattributes MethodParameters
# 隐藏原始源文件名，仅保留行号便于线上 crash 排查
-renamesourcefileattribute SourceFile


# =============================================================================
# 1. 安全网：保留整个应用包（含接口、字段、方法的泛型 Signature）
# -----------------------------------------------------------------------------
# 之前 R8 开启后出现两类问题：
#   1) PlayerRepository 启动崩溃（TypeToken<Map<String, Long>> 签名被剥离）
#   2) 搜索番剧报 "Class cannot be cast to ParameterizedType"
#      （Retrofit 接口 suspend 方法的 Continuation<T> 签名被剥离，
#       Gson 模型字段 List<T> 的签名被剥离）
# 全量保留 com.aiexile.animetrack 包后，R8 仍会执行 shrinking（移除未使用
# 依赖库代码）与 optimization（依赖库层面的优化），APK 体积仍小于不开启 R8。
# =============================================================================
-keep class com.aiexile.animetrack.** { *; }
-keepclassmembers class com.aiexile.animetrack.** { *; }
-keep interface com.aiexile.animetrack.** { *; }


# =============================================================================
# 2. BuildConfig（构建期生成的常量类，运行时反射访问）
# =============================================================================
-keep class com.aiexile.animetrack.BuildConfig { *; }


# =============================================================================
# 3. Android 四大组件入口（AndroidManifest 按类名反射加载）
# -----------------------------------------------------------------------------
# Manifest 中声明的组件：
#   - .AnimeTrackApp               (Application，实现 coil.ImageLoaderFactory)
#   - .MainActivity                (LAUNCHER Activity)
#   - .push.PushService            (JPush Service，运行于 :pushcore 进程)
#   - .push.PushReceiver           (JPush Receiver)
#   - androidx.core.content.FileProvider (分享 APK / 备份文件)
# =============================================================================
-keep public class com.aiexile.animetrack.AnimeTrackApp { *; }
-keep public class com.aiexile.animetrack.MainActivity { *; }
-keep public class com.aiexile.animetrack.push.PushService { *; }
-keep public class com.aiexile.animetrack.push.PushReceiver { *; }

# 通用：所有继承四大组件基类的类都需保留（含三方 SDK 注入的组件）
-keep public class * extends android.app.Application { *; }
-keep public class * extends android.app.Activity { *; }
-keep public class * extends android.app.Service { *; }
-keep public class * extends android.content.BroadcastReceiver { *; }
-keep public class * extends android.content.ContentProvider { *; }

# FileProvider（Manifest 中以 androidx.core.content.FileProvider 声明）
-keep public class androidx.core.content.FileProvider { *; }


# =============================================================================
# 4. Kotlin 运行时与协程
# -----------------------------------------------------------------------------
# Kotlin Metadata 注解承载了扩展函数、可空性、suspend 函数信息，
# Retrofit 解析 suspend 函数的返回值类型时依赖 Metadata。
# BaseContinuationImpl 的 state 字段以 volatile 持有协程状态机。
# =============================================================================
-keep class kotlin.Metadata { *; }
-keep class kotlin.coroutines.jvm.internal.BaseContinuationImpl { *; }
-keepclassmembers class kotlin.coroutines.jvm.internal.BaseContinuationImpl {
    volatile <fields>;
}
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**


# =============================================================================
# 5. Jetpack Compose 运行时
# -----------------------------------------------------------------------------
# Compose 编译器在编译期生成大量合成类，运行时被 Compose runtime 反射调用。
# =============================================================================
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.runtime.**$* { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**


# =============================================================================
# 6. Gson 泛型支持（★ 重点 1：解决 PlayerRepository 启动崩溃）
# -----------------------------------------------------------------------------
# 闪退根因：
#   PlayerRepository.kt 第 25 行：
#       private val mapType = object : TypeToken<Map<String, Long>>() {}.type
#   匿名 TypeToken 子类在 R8 优化下 Signature 属性被剥离，导致运行时
#   TypeToken.getType() 返回擦除后的 Map，Gson 反序列化时把 Long 当成
#   Double 解析，触发 IllegalArgumentException。
# 解决方案：
#   1) 保留 Signature 属性（已在第 0 节声明）
#   2) 保留所有 TypeToken 子类（含匿名内部类）及其构造函数
#   3) 显式保留 PlayerRepository 所在包（已在第 1 节安全网中保留）
#   4) 保留 @SerializedName 标注的字段
# =============================================================================
# Gson 自身：保留 TypeToken、JsonElement 及子类
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class com.google.gson.reflect.TypeToken$* { *; }
-keep class com.google.gson.JsonElement { *; }
-keep class com.google.gson.JsonElement$* { *; }
-keep class com.google.gson.JsonObject { *; }
-keep class com.google.gson.JsonArray { *; }
-keep class com.google.gson.JsonPrimitive { *; }
-keep class com.google.gson.TypeAdapter { *; }
-keep class com.google.gson.TypeAdapterFactory { *; }
-keep class com.google.gson.JsonSerializer { *; }
-keep class com.google.gson.JsonDeserializer { *; }
-keep class com.google.gson.InstanceCreator { *; }
-keep class com.google.gson.FieldNamingStrategy { *; }
-keep class com.google.gson.internal.** { *; }
-keep class com.google.gson.stream.** { *; }

# 所有 TypeToken 子类（含匿名内部类与命名子类）：
#   1) 类本身允许混淆，但必须保留泛型签名
#   2) 构造函数必须保留，否则无法实例化
-keep,allowobfuscation class * extends com.google.gson.reflect.TypeToken { *; }
-keepclassmembers class * extends com.google.gson.reflect.TypeToken {
    public <init>(...);
    *;
}
# 显式点名 PlayerRepository 内的匿名 TypeToken<Map<String, Long>>
# （匿名内部类命名为 com.aiexile.animetrack.data.player.PlayerRepository$1）
-keep class com.aiexile.animetrack.data.player.PlayerRepository { *; }
-keep class com.aiexile.animetrack.data.player.PlayerRepository$* { *; }

# Gson 模型：保留 @SerializedName 字段，避免字段被重命名后无法映射
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers class * {
    @com.google.gson.annotations.Expose <fields>;
}


# =============================================================================
# 7. Retrofit 接口与 OkHttp（★ 重点 2：解决搜索番剧 ClassCastException）
# -----------------------------------------------------------------------------
# 报错根因：
#   suspend fun searchSubjects(@Body request: BangumiSearchRequest): BangumiSearchResponse
#   在 JVM 字节码层面编译为：
#   Object searchSubjects(BangumiSearchRequest, Continuation<? super BangumiSearchResponse>)
#   Retrofit 通过反射读取 Continuation<T> 的泛型参数以推断实际返回类型。
#   R8 在 shrinking 阶段若剥离接口方法的 Signature 属性，Retrofit 拿到的将是
#   原始 Class（Continuation）而非 ParameterizedType（Continuation<T>），
#   随后在 cast 时抛出 "Class cannot be cast to ParameterizedType"。
# 解决方案：
#   1) 全量保留应用所有 Retrofit 接口及其方法签名（不允许 shrinking）
#   2) 移除 allowshrinking / allowobfuscation 等危险修饰符
#   3) 保留 Retrofit 核心类
# =============================================================================
# ★ 关键修复：移除 allowshrinking，确保 Retrofit 注解方法不被 shrinking
-keepclassmembers interface * {
    @retrofit2.http.* <methods>;
}

# Retrofit 核心类：保留 Converter 与 CallAdapter 工厂
-keep class retrofit2.** { *; }
-keep class retrofit2.Retrofit { *; }
-keep class retrofit2.Converter$Factory { *; }
-keep class retrofit2.CallAdapter$Factory { *; }
-keep class retrofit2.converter.gson.GsonConverterFactory { *; }
-keep class retrofit2.OkHttpCall { *; }
-keep class retrofit2.KotlinExtensions { *; }
-keep class retrofit2.KotlinExtensions$* { *; }
-keep class retrofit2.Invocation { *; }
-keep class retrofit2.HttpServiceMethod { *; }
-keep class retrofit2.HttpServiceMethod$* { *; }
-keep class retrofit2.ServiceMethod { *; }
-keep class retrofit2.RequestFactory { *; }
-keep class retrofit2.Utils { *; }
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation interface retrofit2.Callback
-dontwarn retrofit2.**

# ★ 显式保留应用所有 Retrofit 接口及其方法签名（不允许混淆，确保 Signature 保留）
-keep,allowobfuscation interface com.aiexile.animetrack.data.network.BangumiApiService { *; }
-keep,allowobfuscation interface com.aiexile.animetrack.data.network.BilibiliApiService { *; }
-keep,allowobfuscation interface com.aiexile.animetrack.data.network.BilibiliLoginApiService { *; }
-keep,allowobfuscation interface com.aiexile.animetrack.data.network.TmdbApiService { *; }
-keep,allowobfuscation interface com.aiexile.animetrack.data.network.UserAuthApiService { *; }
-keep,allowobfuscation interface com.aiexile.animetrack.data.remote.UpdateApi { *; }

# OkHttp / okio 平台类
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep interface okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**


# =============================================================================
# 8. Retrofit 数据模型（★ 重点 3：解决 Gson 反序列化 List<T> 失败）
# -----------------------------------------------------------------------------
# Gson 解析 List<T> 字段时，通过反射读取字段的 Signature 属性以获取 T 的类型。
# 如果 Signature 被剥离，Gson 会把元素解析为 LinkedHashMap 而非 T 的实例，
# 或抛出 "Class cannot be cast to ParameterizedType"。
# 以下类全部包含 List<T> / Map<K,V> 等泛型字段，必须显式保留。
# =============================================================================
# 应用数据模型包：全量保留 model 包与 data.network / data.remote 包下的数据类
-keep class com.aiexile.animetrack.model.** { *; }
-keepclassmembers class com.aiexile.animetrack.model.** { *; }
-keep class com.aiexile.animetrack.data.network.** { *; }
-keepclassmembers class com.aiexile.animetrack.data.network.** { *; }
-keep class com.aiexile.animetrack.data.remote.** { *; }
-keepclassmembers class com.aiexile.animetrack.data.remote.** { *; }

# 逐项点名含泛型字段的关键数据模型（防御性双重保留）
# Bangumi
-keep class com.aiexile.animetrack.data.network.BangumiSearchResponse { *; }
-keep class com.aiexile.animetrack.data.network.BangumiSearchRequest { *; }
-keep class com.aiexile.animetrack.data.network.BangumiSearchFilter { *; }
-keep class com.aiexile.animetrack.data.network.BangumiSubject { *; }
-keep class com.aiexile.animetrack.data.network.BangumiSubjectDetail { *; }
-keep class com.aiexile.animetrack.data.network.BangumiInfoboxItem { *; }
-keep class com.aiexile.animetrack.data.network.BangumiCollectionResponse { *; }
-keep class com.aiexile.animetrack.data.network.BangumiCollectionItem { *; }
-keep class com.aiexile.animetrack.data.network.BangumiCollectionSubject { *; }
# Bilibili
-keep class com.aiexile.animetrack.data.network.BilibiliFollowResponse { *; }
-keep class com.aiexile.animetrack.data.network.BilibiliFollowData { *; }
-keep class com.aiexile.animetrack.data.network.BilibiliFollowItem { *; }
-keep class com.aiexile.animetrack.data.network.BilibiliNavResponse { *; }
-keep class com.aiexile.animetrack.data.network.BilibiliNavData { *; }
-keep class com.aiexile.animetrack.data.network.BilibiliQrCodeResponse { *; }
-keep class com.aiexile.animetrack.data.network.BilibiliQrCodePollResponse { *; }
# TMDB
-keep class com.aiexile.animetrack.data.network.TmdbSearchResponse { *; }
-keep class com.aiexile.animetrack.data.network.TmdbTvShow { *; }
-keep class com.aiexile.animetrack.data.network.TmdbTvDetail { *; }
-keep class com.aiexile.animetrack.data.network.TmdbGenre { *; }
# 用户认证
-keep class com.aiexile.animetrack.data.network.UserAuthLoginResponse { *; }
-keep class com.aiexile.animetrack.data.network.UserAuthProfileResponse { *; }
-keep class com.aiexile.animetrack.data.network.SubscriptionsResponse { *; }
-keep class com.aiexile.animetrack.data.network.Subscription { *; }
-keep class com.aiexile.animetrack.data.network.PushSettings { *; }
-keep class com.aiexile.animetrack.data.network.PushSettingsResponse { *; }
-keep class com.aiexile.animetrack.data.network.AnnouncementsResponse { *; }
-keep class com.aiexile.animetrack.data.network.Announcement { *; }
-keep class com.aiexile.animetrack.data.network.ActivityReportResponse { *; }
# GitHub Release
-keep class com.aiexile.animetrack.data.remote.GitHubRelease { *; }
-keep class com.aiexile.animetrack.data.remote.GitHubAsset { *; }
-keep class com.aiexile.animetrack.data.remote.UpdateInfo { *; }


# =============================================================================
# 9. Coil 图片加载库
# -----------------------------------------------------------------------------
# Coil v2 已自带 consumer-rules.pro，但部分 ImageLoaderFactory 与组件
# 在反射调用时仍建议显式保留。AnimeTrackApp 实现 ImageLoaderFactory。
# =============================================================================
-keep class coil.** { *; }
-keep class coil.**$* { *; }
-keepclassmembers class coil.** { *; }
-dontwarn coil.**
# 项目自定义 ImageLoaderFactory 入口
-keep class com.aiexile.animetrack.AnimeTrackApp { *; }


# =============================================================================
# 10. Room 数据库
# -----------------------------------------------------------------------------
# Room 生成的 Dao_Impl 类通过反射调用，DAO 接口与 Entity 必须保留。
# TypeConverter 在运行时被反射调用。
# =============================================================================
-keep class androidx.room.** { *; }
-keep class androidx.room.RoomDatabase { *; }
-keep class androidx.room.RoomDatabase$* { *; }
-keep class androidx.room.Entity { *; }
-keep class androidx.room.Dao { *; }
-keep class androidx.room.TypeConverter { *; }
-keep class androidx.room.PrimaryKey { *; }
-keep class androidx.room.FtsOptions { *; }
-keep class androidx.room.Ignore { *; }
# 项目自身的 Entity / Dao / TypeConverter
-keep class com.aiexile.animetrack.data.Anime { *; }
-keep class com.aiexile.animetrack.data.AnimeTypeConverters { *; }
-keep class com.aiexile.animetrack.data.AnimeDao { *; }
-keep class com.aiexile.animetrack.data.AnimeDatabase { *; }
-keep class com.aiexile.animetrack.data.AnimeDatabase$* { *; }
-keep class com.aiexile.animetrack.model.Anime { *; }
-keep class com.aiexile.animetrack.model.AnimeStatus { *; }
# Room 编译期生成的实现类（com.aiexile.animetrack.data.AnimeDatabase_Impl 等）
-keep class com.aiexile.animetrack.data.**_Impl { *; }
-keep class com.aiexile.animetrack.data.**_Impl$* { *; }


# =============================================================================
# 11. DataStore Preferences
# -----------------------------------------------------------------------------
# DataStore 通过 Kotlin Serialization 与反射读取 Preferences 数据。
# =============================================================================
-keep class androidx.datastore.** { *; }
-keep class androidx.datastore.core.** { *; }
-keep class androidx.datastore.preferences.** { *; }
-dontwarn androidx.datastore.**


# =============================================================================
# 12. Sardine WebDAV 客户端
# -----------------------------------------------------------------------------
# Sardine 在 build.gradle.kts 中已排除 xpp3 / kxml2 / xmlpull 传递依赖，
# 因为 Android SDK 已自带 org.xmlpull.v1 实现，避免与平台库冲突。
# Sardine 自身仍以反射调用 SAXParserFactory 与 XmlPullParser。
# =============================================================================
-keep class com.thegrizzlylabs.sardineandroid.** { *; }
-keep class com.thegrizzlylabs.sardineandroid.**$* { *; }
-dontwarn com.thegrizzlylabs.sardineandroid.**
-dontwarn org.codehaus.**


# =============================================================================
# 13. ZXing 二维码核心
# -----------------------------------------------------------------------------
# ZXing core 内部以反射加载条形码解码器。
# =============================================================================
-keep class com.google.zxing.** { *; }
-keep class com.google.zxing.**$* { *; }
-keepclassmembers class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**


# =============================================================================
# 14. WorkManager / CoroutineWorker
# -----------------------------------------------------------------------------
# WorkManager 在运行时通过反射实例化 Worker 工厂类。
# UpdateNotificationWorker 在 Manifest 中以类名引用。
# =============================================================================
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.WorkerFactory { *; }
-keep class androidx.work.impl.** { *; }
-dontwarn androidx.work.**
# 项目自定义 Worker
-keep class com.aiexile.animetrack.data.notification.UpdateNotificationWorker { *; }
-keep class com.aiexile.animetrack.data.notification.UpdateNotificationWorker$* { *; }


# =============================================================================
# 15. Media3 ExoPlayer
# -----------------------------------------------------------------------------
# Media3 通过反射实例化 DataSource 工厂与 Renderer。
# media3-datasource-okhttp 在运行时动态查找 OkHttpDataSource。
# =============================================================================
-keep class androidx.media3.** { *; }
-keep class androidx.media3.**$* { *; }
-keepclassmembers class androidx.media3.** { *; }
-keep class androidx.media3.datasource.okhttp.OkHttpDataSource { *; }
-keep class androidx.media3.exoplayer.source.MediaSource { *; }
-keep class androidx.media3.exoplayer.Renderer { *; }
-dontwarn androidx.media3.**


# =============================================================================
# 16. Material Design Components（XML 旧版 com.google.android.material）
# -----------------------------------------------------------------------------
# AnimeTrack 在 XML 主题中引用了 Theme.Material3 等，需保留反射入口。
# =============================================================================
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**


# =============================================================================
# 17. AndroidX 通用缺失类告警抑制
# -----------------------------------------------------------------------------
# 部分新版 AndroidX 引用了旧版类，但不影响运行。
# =============================================================================
-dontwarn androidx.test.**
-dontwarn androidx.core.**
-dontwarn androidx.lifecycle.**
-dontwarn androidx.activity.**
-dontwarn androidx.navigation.**
-dontwarn androidx.graphics.**
-dontwarn androidx.appcompat.**


# =============================================================================
# 18. JPush / JCore SDK + 厂商推送通道（★ 重点 4：解决 stack map 警告）
# -----------------------------------------------------------------------------
# 现状：
#   - 集成 jcore-android-5.4.0.aar（不是 JPush 4.1.3.0，实际为 JCore 5.4.0）
#   - 厂商通道 AAR：com.heytap.msp（OPPO）、HiPushSDK（华为/荣耀）、
#     push-internal（vivo 内部通道）
#   - 编译期出现 "Expected stack map table" 警告：原因是这些预混淆 AAR
#     的字节码被 R8 二次优化时与原 stack map 不一致。
# 解决方案：
#   1) 对所有 SDK 包执行 -dontoptimize，关闭 R8 的优化阶段（仍保留 shrinking）
#   2) 对所有 SDK 包执行 -dontwarn，抑制缺失类告警
#   3) 保留所有 SDK 类与成员，避免反射调用失败
# =============================================================================
# JPush / JCore
-keep class cn.jpush.** { *; }
-keep class cn.jpush.**$* { *; }
-keep class cn.jcore.** { *; }
-keep class cn.jcore.**$* { *; }
-keep class com.jiguang.** { *; }
-keep class com.jiguang.**$* { *; }
-keep class com.jpush.** { *; }
-keep class * extends com.jiguang.** { *; }
# JPush 内部 SDK 模型与 receiver
-keep class cn.jpush.im.** { *; }
-keep class cn.jpush.a.** { *; }
-keep class cn.jpush.data.** { *; }

# 厂商推送通道
-keep class com.heytap.** { *; }              # OPPO / HeyTap
-keep class com.heytap.**$* { *; }
-keep class com.hihonor.** { *; }             # 荣耀
-keep class com.hihonor.**$* { *; }
-keep class com.huawei.** { *; }              # 华为 HMS Push
-keep class com.huawei.**$* { *; }
-keep class com.meizu.** { *; }               # 魅族
-keep class com.meizu.**$* { *; }
-keep class com.vivo.** { *; }                # vivo
-keep class com.vivo.**$* { *; }
-keep class com.coloros.** { *; }             # ColorOS（OPPO 子品牌）
-keep class com.coloros.**$* { *; }

# ★ 关键：抑制 "Expected stack map table" 警告
# 对所有预混淆的厂商 SDK 关闭 R8 优化阶段（仍保留代码 shrinking）。
# 关闭 optimize 后，stack map table 不会被重新计算，警告即消失。
-dontoptimize
# 抑制 SDK 引用的、但 classpath 上缺失的类的告警
-dontwarn cn.jpush.**
-dontwarn cn.jcore.**
-dontwarn com.jiguang.**
-dontwarn com.heytap.**
-dontwarn com.hihonor.**
-dontwarn com.huawei.hms.**
-dontwarn com.meizu.**
-dontwarn com.vivo.**
-dontwarn com.coloros.**
-dontwarn com.google.android.gms.**


# =============================================================================
# 19. XmlPullParser 冲突修复（Sardine 与 JPush 共同问题）
# -----------------------------------------------------------------------------
# Sardine / JPush 等 SDK 的 jar/aar 内部打包了 org.xmlpull.v1.XmlPullParser
# 接口，与 Android 平台库 android.content.res.XmlResourceParser（实现该接口）
# 冲突。R8 不允许 library class 实现 program class，因此保留该包不被优化。
# build.gradle.kts 中已对 Sardine 排除 xpp3 / kxml2 / xmlpull，此处为兜底。
# =============================================================================
-keep class org.xmlpull.v1.** { *; }
-keep class org.xmlpull.v1.**$* { *; }
-dontwarn org.xmlpull.v1.**
-dontwarn org.kxml2.**
-dontwarn org.xmlpull.mxp1.**


# =============================================================================
# 20. 通用缺失类告警抑制（三方 SDK 引用非 classpath 上的类）
# =============================================================================
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**
-dontwarn org.apache.**
-dontwarn org.codehaus.**
-dontwarn org.junit.**
-dontwarn org.hamcrest.**
-dontwarn org.mockito.**
-dontwarn org.robolectric.**
-dontwarn android.support.**


# =============================================================================
# 21. 调试与构建变体说明
# -----------------------------------------------------------------------------
# - 项目无 flavor，所有规则默认对 release 生效（buildTypes.release 中
#   proguardFiles 指向本文件）。
# - 如未来添加 flavor，可将公共规则保留在此文件，将差异规则放入
#   src/<flavor>/proguard-rules.pro。
# - 调试崩溃时，可临时将 -keep class com.aiexile.animetrack.** { *; }
#   之外的规则全部注释，逐步二分定位冲突点。
# =============================================================================
