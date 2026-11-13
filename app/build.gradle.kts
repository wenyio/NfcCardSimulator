plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.iscolt.cardsimulator"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.iscolt.cardsimulator"
        minSdk = 26
        targetSdk = 31
        versionCode = 19
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "NFC 卡模拟器 · 调试")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint {
        // Android 12 target is intentional for local APK distribution, not Google Play.
        disable += "ExpiredTargetSdkVersion"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation("androidx.viewpager:viewpager:1.0.0")
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation("org.json:json:20240303")
}

tasks.register<Copy>("exportDebugApk") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(rootProject.layout.projectDirectory.dir("artifacts"))
    rename { "NfcCardSimulator-v1.0.0-debug.apk" }
}

// 当前未配置发布证书，保留未签名标识，避免将调试签名误作正式发布签名。
tasks.register<Copy>("exportReleaseApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release-unsigned.apk"))
    into(rootProject.layout.projectDirectory.dir("artifacts"))
    rename { "NfcCardSimulator-v1.0.0-release-unsigned.apk" }
}
