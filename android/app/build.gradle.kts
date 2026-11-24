plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.myipwidget"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.myipwidget"
        minSdk = 26
        targetSdk = 34
        versionCode = 77
        versionName = "1.21.11"
    }

    // A fixed key, so each new version installs over the previous one
    // (the build machine would otherwise invent a new key every time).
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    // the file is named after the app and its version (no "app-debug")
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "Mostafa-MultiWidget-v${versionName}.apk"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
