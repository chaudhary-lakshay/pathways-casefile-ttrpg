// A standalone build: it compiles the app's own core sources (../../core) on
// the JVM and writes the parity vectors the TypeScript port is tested against.
// The Android build never sees this directory.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories { mavenCentral() }
}

rootProject.name = "pathways-parity"
