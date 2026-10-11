plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val facebookAppId: String = (project.findProperty("facebookAppId") as String?).orEmpty()

android {
    namespace = "com.clouddesk.storymaker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.clouddesk.storymaker"
        minSdk = 29          // Android 10+
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "FACEBOOK_APP_ID", "\"$facebookAppId\"")
    }

    // Fixed key so every new APK installs over the previous one (updates keep your data).
    // Fine for personal use. Before publishing on Play Store, create a private key and keep it out of git.
    signingConfigs {
        create("app") {
            storeFile = file("storymaker.jks")
            storePassword = "storymaker"
            keyAlias = "storymaker"
            keyPassword = "storymaker"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("app")
        }
        debug {
            signingConfig = signingConfigs.getByName("app")
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

    lint { abortOnError = false }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    val media3 = "1.9.4"
    implementation("androidx.media3:media3-transformer:$media3")
    implementation("androidx.media3:media3-effect:$media3")
    implementation("androidx.media3:media3-common:$media3")

    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.core:core-ktx:1.16.0")
}
