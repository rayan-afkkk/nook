import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

/** Reads a NOOK setting from local.properties first, then gradle.properties. */
fun nookProp(name: String): String {
    val local = Properties()
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { local.load(it) }
    val value = local.getProperty(name) ?: (findProperty(name) as String?) ?: ""
    return value.trim().replace("\"", "")
}

/** Upload-key signing for Play. Values come from keystore.properties (gitignored) or CI environment variables. */
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.nook.msgapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nook.msgapp"
        minSdk = 26
        targetSdk = 35
        versionCode = (findProperty("nook.versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = "0.1.0"

        buildConfigField("String", "WORKER_URL", "\"${nookProp("nook.workerUrl").trimEnd('/')}\"")
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${nookProp("nook.cloudinaryCloudName")}\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"${nookProp("nook.cloudinaryUploadPreset")}\"")
        buildConfigField("String", "GIPHY_API_KEY", "\"${nookProp("nook.giphyApiKey")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${nookProp("nook.googleWebClientId")}\"")
    }

    signingConfigs {
        val storeFilePath = signingValue("storeFile", "NOOK_KEYSTORE_FILE")
        if (storeFilePath != null) {
            create("upload") {
                storeFile = rootProject.file(storeFilePath)
                storePassword = signingValue("storePassword", "NOOK_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "NOOK_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "NOOK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Play requires a real upload key. Without keystore.properties the release build is left unsigned.
            signingConfigs.findByName("upload")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)
    implementation(libs.firebase.messaging)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.okhttp)
    implementation(libs.livekit)
}
