// app 模块：Kotlin + Jetpack Compose，minSdk 26 / compileSdk 34
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.vibeqwen.glasses"
    compileSdk = 34

    val gitCount = try {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .directory(project.rootDir)
            .start()
        process.inputStream.bufferedReader().readText().trim().toIntOrNull() ?: 72
    } catch (e: Exception) {
        72
    }

    val gitHash = try {
        val process = ProcessBuilder("git", "log", "-1", "--format=%h")
            .directory(project.rootDir)
            .start()
        process.inputStream.bufferedReader().readText().trim().ifEmpty { "dev" }
    } catch (e: Exception) {
        "dev"
    }

    val ciRunNumber = 200 + gitCount

    defaultConfig {
        applicationId = "com.vibeqwen.glasses"
        minSdk = 26
        targetSdk = 34
        versionCode = ciRunNumber
        versionName = "1.0.0-$ciRunNumber-$gitHash"
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/release.keystore")
            storePassword = "vibeqwen_pass"
            keyAlias = "vibeqwen"
            keyPassword = "vibeqwen_pass"
        }
    }

    buildTypes {
        debug {
            // debug 也统一使用固定 keystore 签名，保证与 release 及历史版本签名 100% 一致
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            // minify 关闭，避免混淆带来额外风险（MVP）
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 固定 keystore 签名：避免 CI 每次生成不同 debug 证书导致覆盖安装签名冲突
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // MVP 阶段放宽 lint：避免非致命告警阻断 CI
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Kotlin 编译目标（Kotlin 2.0 DSL，需在 android 块之外）
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // 基础
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Compose（BOM 统一版本）
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 协程 + JSON（协议层纯 Kotlin，可单测）
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // 单元测试（协议层）
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}