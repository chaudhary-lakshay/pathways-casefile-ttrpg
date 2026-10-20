plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    application
}

kotlin { jvmToolchain(17) }

val core = rootDir.resolve("../../core")

// The app's pure files: the session and its saves, with no Compose UI in them.
// Copied, because a source directory cannot name single files.
val appFiles = listOf(
    "BeyonderSession", "Characters", "EditedCharacter", "ModifierRange",
    "PathwayText", "SessionStore", "SheetLines",
)
val appSources = tasks.register<Sync>("appSources") {
    from(rootDir.resolve("../../app/src/main/kotlin/com/pathways/app")) {
        appFiles.forEach { include("$it.kt") }
    }
    into(layout.buildDirectory.dir("app-src"))
}

sourceSets {
    main {
        kotlin.srcDirs(
            core.resolve("model/src/main/kotlin"),
            core.resolve("rules/src/main/kotlin"),
            "src/main/kotlin",
            "src/stubs/kotlin",
            appSources,
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
