plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

val compileSdkApi = providers.gradleProperty("animetrack.compileSdk").get()

android {
    namespace = "com.aiexile.animetrack.baselineprofile"
    compileSdk {
        version = release(compileSdkApi.substringBefore('.').toInt()) {
            minorApiLevel = compileSdkApi.substringAfter('.', "0").toInt()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
}

// 指定被测应用的 benchmark 构建类型
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
