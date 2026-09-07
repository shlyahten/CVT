plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ru.shlyahten.cvt"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    val propVersionCode = project.findProperty("versionCode")?.toString()?.toIntOrNull() ?: 1
    val propVersionName = project.findProperty("versionName")?.toString() ?: "1.0"

    defaultConfig {
        applicationId = "ru.shlyahten.cvt"
        minSdk = 26
        targetSdk = 36
        versionCode = propVersionCode
        versionName = propVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = project.findProperty("KEYSTORE_FILE")?.toString()
                ?: System.getenv("KEYSTORE_FILE")
            val keystoreFile = keystorePath?.let { file(it) }

            if (keystoreFile != null && keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = project.findProperty("KEYSTORE_PASSWORD")?.toString()
                    ?: System.getenv("KEYSTORE_PASSWORD")
                keyAlias = project.findProperty("KEY_ALIAS")?.toString()
                    ?: System.getenv("KEY_ALIAS")
                keyPassword = project.findProperty("KEY_PASSWORD")?.toString()
                    ?: System.getenv("KEY_PASSWORD")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    lint {
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
