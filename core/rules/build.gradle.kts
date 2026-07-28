plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("java-library")
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }

sourceSets {
    // The 23 published character sheets ship on the MAIN classpath: they are
    // the app's premade-import surface (spec section 8), not test data. The
    // test classpath still sees them through main's output.
    named("main") {
        resources.srcDir(rootProject.layout.projectDirectory.dir("fixtures"))
    }
}
