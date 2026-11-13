import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// 签名私钥与密码仅从本机文件读取，不进入版本库。
val releasePropertiesFile = rootProject.file("signing.properties")
val releaseProperties = Properties().apply {
    if (releasePropertiesFile.isFile) releasePropertiesFile.inputStream().use { load(it) }
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

    signingConfigs {
        if (releasePropertiesFile.isFile) {
            create("localRelease") {
                storeFile = rootProject.file(requireNotNull(releaseProperties.getProperty("storeFile")))
                storePassword = requireNotNull(releaseProperties.getProperty("storePassword"))
                keyAlias = requireNotNull(releaseProperties.getProperty("keyAlias"))
                keyPassword = requireNotNull(releaseProperties.getProperty("keyPassword"))
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "NFC 卡模拟器 · 调试")
        }
        release {
            if (releasePropertiesFile.isFile) signingConfig = signingConfigs.getByName("localRelease")
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

// 发布任务必须有独立签名，避免导出无法安装或误用调试签名的产物。
val requireReleaseSigning = tasks.register("requireReleaseSigning") {
    doLast { check(releasePropertiesFile.isFile) { "请先配置 signing.properties，参见 docs/RELEASE-SIGNING.md" } }
}
tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(requireReleaseSigning)
}
tasks.register<Copy>("exportReleaseApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.projectDirectory.dir("artifacts"))
    rename { "NfcCardSimulator-v1.0.0-release.apk" }
}
