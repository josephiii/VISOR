import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}


val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(FileInputStream(localPropertiesFile))
    }
}

android {
    namespace = "ucf.visor"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
        compose = true
    }

    androidResources {
        noCompress += listOf("onnx", "bin", "tflite", "ort")
    }

    defaultConfig {
        applicationId = "edu.ucf.visor"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Meta Wearables Device Access Toolkit Setup
        // Set them in local.properties — which is gitignored, so they stay out of
        // version control:
        //   mwdat_application_id=<from Wearables Developer Center>
        //   mwdat_client_token=<from Wearables Developer Center>
        // Unset, they fall back to "0": MWDAT 1.0's documented Developer Mode
        // placeholder (attestation is skipped in Developer Mode). Release
        // channels and "Hey Meta" voice launches need the real values, with
        // Developer Mode off — see documentation/developer/mwdat-1.0-upgrade.md.
        manifestPlaceholders["mwdat_application_id"] =
            providers.gradleProperty("mwdat_application_id").orNull
                ?: localProperties.getProperty("mwdat_application_id", "0")
        manifestPlaceholders["mwdat_client_token"] =
            providers.gradleProperty("mwdat_client_token").orNull
                ?: localProperties.getProperty("mwdat_client_token", "0")

        // Stamped into every head-motion lab recording, so the analysis knows
        // which SDK produced it — taken from the catalog so it cannot go stale.
        buildConfigField("String", "MWDAT_VERSION", "\"${libs.versions.mwdat.get()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    // MWDAT 1.0's inline DatResult helpers (onSuccess/onFailure/fold) are
    // compiled for JVM 11+, and cannot be inlined into 1.8 bytecode.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.1" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    signingConfigs {
        getByName("debug") {
            storeFile = file("sample.keystore")
            storePassword = "sample"
            keyAlias = "sample"
            keyPassword = "sample"
        }
    }
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation.runtime.ktx)
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation(libs.kotlinx.collections.immutable)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    // Compose Tooling & Preview Support (for dev purposes)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Compose Tooling & Preview Support (for dev purposes)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation(files("lib/sherpa-onnx-1.13.5.aar"))
    // MWDAT (Maven Central from 1.0.0)
    implementation(libs.mwdat.core)
    implementation(libs.mwdat.camera)
    implementation(libs.mwdat.display)
    // Experimental in 1.0: dev and beta release channels only, not production.
    implementation(libs.mwdat.motion)
    implementation(libs.mwdat.mockdevice)
}