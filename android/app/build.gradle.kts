plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aditya.cabletv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aditya.cabletv"
        minSdk = 30
        targetSdk = 30 // the one TV this ships to runs Android 11 (D-002, D-023)
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        create("bench") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    lint {
        // Sideloaded onto one Android 11 TV only; Play's target-API rule doesn't apply (D-023).
        disable += "ExpiredTargetSdkVersion"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
