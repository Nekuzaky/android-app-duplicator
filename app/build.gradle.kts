plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.nekuz.dualspace"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.nekuz.dualspace"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

// Volontairement AUCUNE dépendance : pas de bibliothèque tierce, pas de pub, pas d'analytics.
dependencies {
}
