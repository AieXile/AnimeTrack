import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
}

val ksPath: String? = System.getenv("KEYSTORE_PATH") 
    ?: project.findProperty("KEYSTORE_PATH")?.toString()
val ksPass: String? = System.getenv("KEYSTORE_PASSWORD") 
    ?: project.findProperty("KEYSTORE_PASSWORD")?.toString()
val kAlias: String? = System.getenv("KEY_ALIAS") 
    ?: project.findProperty("KEY_ALIAS")?.toString()
val kPass: String? = System.getenv("KEY_PASSWORD") 
    ?: project.findProperty("KEY_PASSWORD")?.toString()

val isSigningReady = !ksPath.isNullOrBlank() && !ksPass.isNullOrBlank() 
    && !kAlias.isNullOrBlank() && !kPass.isNullOrBlank()

val githubToken: String = project.findProperty("GITHUB_TOKEN")?.toString() ?: ""

android {
    namespace = "com.aiexile.animetrack"
    // compileSdk 37：backdrop 2.0.0（液态玻璃）要求 minCompileSdk 37
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aiexile.animetrack"
        minSdk = 26
        targetSdk = 34
        versionCode = 32
        versionName = "v0.6.2-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "GITHUB_TOKEN", "\"$githubToken\"")

        manifestPlaceholders["JPUSH_PKGNAME"] = applicationId!!
        manifestPlaceholders["JPUSH_APPKEY"] = "b9ce1be738b374cd17feed0c"
        manifestPlaceholders["JPUSH_CHANNEL"] = "developer-default"
        manifestPlaceholders["MEIZU_APPKEY"] = ""
        manifestPlaceholders["MEIZU_APPID"] = ""
        manifestPlaceholders["OPPO_APPKEY"] = ""
        manifestPlaceholders["OPPO_APPID"] = ""
        manifestPlaceholders["OPPO_APPSECRET"] = ""
        manifestPlaceholders["VIVO_APPKEY"] = ""
        manifestPlaceholders["VIVO_APPID"] = ""
        manifestPlaceholders["HONOR_APPID"] = ""
        manifestPlaceholders["NIO_APPID"] = ""
    }

    signingConfigs {
        if (isSigningReady) {
            create("release") {
                storeFile = project.rootProject.file(ksPath!!)
                storePassword = ksPass
                keyAlias = kAlias
                keyPassword = kPass
            }
        }
    }

    buildTypes {
        // benchmark 构建类型：继承 release 配置，供 macrobenchmark / baseline profile 录制使用。
        // 可调试、不混淆，与正式包隔离，不影响正式 release 产物。
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            proguardFiles("benchmark-rules.pro")
        }
        getByName("release") {
            // 完全关闭 R8：代码不混淆、不优化、不移除
            // 注意：isShrinkResources 必须配合 isMinifyEnabled=true 使用（AGP 硬性约束），
            //       故资源瘦身一并关闭。如未来需要资源瘦身，需重新启用 minify 并配置 -dontobfuscate。
            isMinifyEnabled = false
            isShrinkResources = false
            if (isSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // ABI 分包：assembleRelease 时同时产出 arm64-v8a / armeabi-v7a 单架构包与 universal 全包含包
    // （替代原 ndk.abiFilters：abiFilters 会限制所有产物的 ABI，与 splits 冲突）
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.windowsizeclass)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.compose.ratingbar)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation("androidx.compose.animation:animation")
    implementation(libs.coil.compose)
    implementation(libs.graphics.shapes)
    implementation(libs.haze)
    implementation(libs.backdrop)
    implementation(libs.kyant.shapes)
    // ProfileInstaller：安装时将打包的 baseline-prof.txt 写入系统，触发热路径 AOT 预编译。
    implementation(libs.androidx.profileinstaller)
    
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    
    implementation(libs.datastore.preferences)
    implementation(libs.material)
    implementation(libs.sardine) {
        // Sardine 传递依赖 xpp3 / kxml2，其内部打包的 org.xmlpull.v1.XmlPullParser
        // 与 Android 平台库 android.content.res.XmlResourceParser（实现该接口）冲突，
        // 导致 R8 报 "Library class implements program class" 错误。
        // Android SDK 已自带 org.xmlpull.v1 实现，故排除这两个传递依赖。
        exclude(group = "xpp3", module = "xpp3")
        exclude(group = "net.sf.kxml", module = "kxml2")
        exclude(group = "xmlpull", module = "xmlpull")
    }
    implementation(libs.splashscreen)
    implementation(libs.zxing.core)
    implementation(libs.work.runtime.ktx)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.session)
    // libass 特效字幕引擎（native 渲染，绕开 Java 堆限制；ass-media 为其 media3 桥接库）
    implementation(libs.ass.media)
    // 设置搜索：中文转拼音，支持拼音全拼/首字母模糊匹配
    // （Maven Central 上的 pinyin4j，无传递依赖；TinyPinyin 在 JitPack 上构建已失效不可用）
    implementation(libs.pinyin4j)
    implementation(project(":jiguang"))
    implementation(files("../jiguang/libs/jcore-android-5.5.0.aar"))
    implementation(files("../jiguang/libs/com.heytap.msp_V3.9.8.aar"))
    implementation(files("../jiguang/libs/HiPushSDK-10.0.13.305.aar"))
    implementation(files("../jiguang/libs/push-internal-5.0.5.aar"))
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // baselineProfile 依赖：让 :baselineprofile 模块产出的 profile 被本模块消费。
    baselineProfile(project(":baselineprofile"))
}
