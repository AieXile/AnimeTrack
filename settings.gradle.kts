pluginManagement {
    // GitHub Actions 等 CI 环境默认注入 CI=true，本地开发不设置该变量
    // 注意：pluginManagement 块由 Kotlin DSL 预编译，引用不到脚本顶层变量，须在块内声明
    val isCI = System.getenv("CI") == "true"
    repositories {
        // 本地开发走阿里云镜像（避免国内访问官方源超时），CI 直接走官方源
        if (!isCI) {
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    val isCI = System.getenv("CI") == "true"
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 本地开发走阿里云镜像，CI 直接走官方源
        if (!isCI) {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        flatDir { dirs("${rootProject.projectDir}/jiguang/libs") }
    }
}

rootProject.name = "AnimeTrack"
include(":app")
include(":jiguang")
include(":baselineprofile")
