plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.android)
}

import java.util.Properties

// Release signing credentials live in keystore.properties (gitignored) so the
// keystore never enters version control. See scripts/release.sh for the flow.
val keystoreProperties = Properties().apply {
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        keystoreFile.inputStream().use { load(it) }
    }
}

// Debug-only SMS test senders: comma-separated phone numbers in local.properties
// (gitignored) as TEST_SENDERS=+91XXXXXXXXXX,+91YYYYYYYYYY. Baked into
// BuildConfig so DebugTestSmsParser can claim them; empty = parser inert.
val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}
val testSenders = (localProperties["TEST_SENDERS"] as String?).orEmpty().trim()
    .replace("\"", "")

android {
    namespace = "com.example.spendwise"

    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.spendwise"
        minSdk = 29
        targetSdk = 36
        versionCode = 4
        // X.Y.Z form so scripts/release.sh can auto-bump the patch segment.
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // In-app updater manifest served from the R2 release bucket
        // (scripts/release.sh writes version.json on every release).
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"https://apk.spendwise-api.rohithcheryala.dev/version.json\""
        )
        // Debug SMS test-sender allowlist (local.properties TEST_SENDERS,
        // comma-separated). Empty string when unset — parser stays inert.
        buildConfigField("String", "TEST_SENDERS", "\"$testSenders\"")
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties["KEY_ALIAS"] != null) {
                storeFile = file(keystoreProperties["STORE_FILE"] as String)
                storePassword = keystoreProperties["STORE_PASSWORD"] as String
                keyAlias = keystoreProperties["KEY_ALIAS"] as String
                keyPassword = keystoreProperties["KEY_PASSWORD"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt)
//    implementation(libs.core.ktx)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
    // In-app updater: fetch version.json + download release APK (same as Veena)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Scan & Pay: live camera preview + on-device QR decoding.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}