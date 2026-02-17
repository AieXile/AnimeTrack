plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

val keystorePath: String? = System.getenv("KEYSTORE_PATH") 
    ?: findProperty("KEYSTORE_PATH") as String?
    ?: if (File("${rootDir}/keystore.jks").exists()) "${rootDir}/keystore.jks" else null

val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD") 
    ?: findProperty("KEYSTORE_PASSWORD") as String?
    
val keyAlias: String? = System.getenv("KEY_ALIAS") 
    ?: findProperty("KEY_ALIAS") as String?
    
val keyPassword: String? = System.getenv("KEY_PASSWORD") 
    ?: findProperty("KEY_PASSWORD") as String?

val hasSigningConfig = keystorePath != null && keystorePassword != null 
    && keyAlias != null && keyPassword != null

android {
    namespace = "com.aiexile.animetrack"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aiexile.animetrack"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "v0.2.0-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    if (hasSigningConfig) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword!!
                keyAlias = keyAlias!!
                keyPassword = keyPassword!!
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
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
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    
    implementation(libs.datastore.preferences)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
