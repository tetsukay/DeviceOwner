plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.tetsukay.deviceowner"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.tetsukay.deviceowner"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        // debug ビルドのみ testOnly=true にして `dpm remove-active-admin` で外せるようにする
        manifestPlaceholders["testOnly"] = "false"
    }

    signingConfigs {
        // どの PC でビルドしても同じ署名になるよう、リポジトリ同梱の debug.keystore を使う
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            manifestPlaceholders["testOnly"] = "true"
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
}
