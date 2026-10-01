plugins {
    id("com.android.application")
}

android {
    namespace = "com.serava.companion"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.serava.companion"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.9.2-ui-fix"
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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
