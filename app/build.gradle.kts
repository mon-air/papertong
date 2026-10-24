import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.papertong.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.papertong.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.1.8"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            /*
             * 没有发布用的 keystore，就用 debug 签名。
             *
             * 为什么：未签名的 APK 装不上，R8 到底有没有把功能裁坏就无法验证——
             * 而"能编译过"和"运行时正常"是两件事。用 debug key 签名的包可以直接
             * adb install 到手机上实测；代价是它不能上架，也不能覆盖已装的正式版。
             *
             * 将来真要发布时：在这里加一个从环境变量/keystore.properties 读取
             * 正式签名的 signingConfig，并把本段改回去。仓库里不放任何密钥文件。
             */
            signingConfig = signingConfigs.getByName("debug")
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
            // 多个库自带许可证文件，合并时会冲突
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // 协程与序列化（M3 起使用）
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // 网络
    // 不使用 retrofit2-kotlinx-serialization-converter：本项目只有 1 个接口，
    // 且该库 1.0.0 起包名有变动，直接手写 JSON body 更稳、更少一个依赖。
    implementation(libs.retrofit)
    implementation(libs.okhttp)

    // 设置存储（M2 起使用）
    implementation(libs.androidx.datastore.preferences)

    // PDF 文字层抽取。
    // 注意：pdfbox-android 会连带引入 bouncycastle（bcprov 约 9MB + bcpkix/bcutil 约 1.7MB），
    // 这是它的解析依赖，不是可选项；当前体积可接受，若后续 APK 过大再考虑按 ABI 拆分。
    implementation(libs.pdfbox.android)

    // OCR：bundled 版，模型随 APK 分发，识别完全离线，不依赖 Play Services 运行时下载
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)
}
