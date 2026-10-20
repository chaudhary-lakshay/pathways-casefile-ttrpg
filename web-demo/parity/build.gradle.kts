plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    application
}

kotlin { jvmToolchain(17) }

val core = rootDir.resolve("../../core")

sourceSets {
    main {
        kotlin.srcDirs(
            core.resolve("model/src/main/kotlin"),
            core.resolve("rules/src/main/kotlin"),
            "src/main/kotlin",
        )
        resources.srcDirs(core.resolve("rules/src/main/resources"), rootDir.resolve("../../fixtures"))
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation(kotlin("reflect"))
}

application { mainClass.set("parity.MainKt") }

tasks.named<JavaExec>("run") {
    args(rootDir.resolve("../src/test/vectors").absolutePath)
}
