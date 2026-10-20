plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.anplomer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.anplomer"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The default debug keystore lives in ~/.android, which is not writable in
    // every build sandbox, so the project ships its own debug keystore.
    signingConfigs {
        // AGP already creates a "debug" signing config; point it at the
        // project-local keystore instead of ~/.android/debug.keystore.
        getByName("debug") {
            storeFile = rootProject.file(".keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
