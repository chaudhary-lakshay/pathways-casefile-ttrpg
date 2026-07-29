plugins {
    // No kotlin-android plugin: AGP 9 has built-in Kotlin support and
    // registers the `kotlin` extension itself. Applying both fails with
    // "Cannot add extension with name 'kotlin'".
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pathways.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pathways.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    // The 23 premade sheets ride in on :core:rules' main resources.
    implementation(project(":core:rules"))
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
}
