plugins {
    // No kotlin-android plugin: AGP 9 has built-in Kotlin support and
    // registers the `kotlin` extension itself. Applying both fails with
    // "Cannot add extension with name 'kotlin'".
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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

    testOptions {
        unitTests {
            // SessionStore logs discarded saves through android.util.Log, which
            // is a stub on the JVM classpath and throws by default. Returning
            // defaults lets the failure PATH be tested; nothing here asserts on
            // logging itself.
            isReturnDefaultValues = true
        }
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    // The 23 premade sheets ride in on :core:rules' main resources.
    implementation(project(":core:rules"))
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.kotlinx.serialization.json)

    // The screens are still verified on a device; this source set is for the
    // logic under them -- BeyonderSession's rules wiring and SessionStore's
    // file handling -- where two defects have already reached master.
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    // Test scope only: ThemeSchemesTest reflects over ColorScheme's roles.
    // Never `implementation` -- kotlin-reflect is ~3MB and the app needs none.
    testImplementation(libs.kotlin.reflect)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
