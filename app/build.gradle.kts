plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aiexile.animetrack"
        minSdk = 26
        targetSdk = 34
        versionCode = 21
        versionName = "v0.4.3-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "GITHUB_TOKEN", "\"$githubToken\"")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

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
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation("androidx.compose.animation:animation")
    implementation(libs.coil.compose)
    
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    
    implementation(libs.datastore.preferences)
    implementation(libs.material)
    implementation(libs.sardine)
    implementation(libs.splashscreen)
    implementation(libs.zxing.core)
    implementation(libs.work.runtime.ktx)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui.compose)
    implementation(project(":jiguang"))
    implementation(files("../jiguang/libs/jcore-android-5.4.0.aar"))
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
}
