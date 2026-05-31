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
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aiexile.animetrack"
        minSdk = 26
        targetSdk = 34
        versionCode = 14
        versionName = "v0.3.5-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "GITHUB_TOKEN", "\"$githubToken\"")
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
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
