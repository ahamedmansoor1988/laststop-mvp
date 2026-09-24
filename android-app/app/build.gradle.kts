import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

// Release signing credentials are read from local.properties (gitignored) so they never reach
// the repo. Set these four keys there, or supply the matching QUIKLOOK_* environment variables
// on a build machine, to produce a signed release build:
//
//   releaseStoreFile=/absolute/path/to/quiklook-release.jks
//   releaseStorePassword=...
//   releaseKeyAlias=quiklook
//   releaseKeyPassword=...
//
// Without them, release builds still assemble — just unsigned, so they cannot be uploaded.
val releaseSigningProps: Map<String, String> = run {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { props.load(it) }
    fun read(key: String, env: String) = props.getProperty(key) ?: System.getenv(env)
    val storeFile = read("releaseStoreFile", "QUIKLOOK_STORE_FILE")
    val storePassword = read("releaseStorePassword", "QUIKLOOK_STORE_PASSWORD")
    val keyAlias = read("releaseKeyAlias", "QUIKLOOK_KEY_ALIAS")
    val keyPassword = read("releaseKeyPassword", "QUIKLOOK_KEY_PASSWORD")
    if (storeFile != null && storePassword != null && keyAlias != null && keyPassword != null) {
        mapOf(
            "storeFile" to storeFile,
            "storePassword" to storePassword,
            "keyAlias" to keyAlias,
            "keyPassword" to keyPassword
        )
    } else {
        emptyMap()
    }
}

android {
    namespace = "com.quiklook.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.quiklook.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (releaseSigningProps.isNotEmpty()) {
            create("release") {
                storeFile = file(releaseSigningProps.getValue("storeFile"))
                storePassword = releaseSigningProps.getValue("storePassword")
                keyAlias = releaseSigningProps.getValue("keyAlias")
                keyPassword = releaseSigningProps.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Left off until a release build has been shrink-tested on a device — R8 is
            // fine with Compose in principle, but this has not been verified for this app.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.libraries.places:places:4.3.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.compose.material3:material3")
    implementation("io.github.rabehx:iconsax-compose:0.0.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

secrets {
    defaultPropertiesFileName = "local.defaults.properties"
}
