plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}

// A source and resolved-dependency guard, separate from the ordinary compiler check.
val verifyFoundationBoundary by tasks.registering {
    group = "verification"
    description = "Reject Android, app, database, or model/runtime coupling in foundation-contracts."
    doLast {
        check(plugins.none { it.javaClass.name.contains("Android", ignoreCase = true) }) {
            "foundation-contracts must remain a JVM-only module"
        }
        val forbiddenGroups = listOf(
            "com.android", "androidx", "org.tensorflow", "ai.onnxruntime",
            "org.pytorch", "com.google.ai", "io.realm", "org.xerial"
        )
        configurations.filter { it.isCanBeResolved && it.name in setOf("compileClasspath", "runtimeClasspath") }
            .forEach { config ->
                val modules = config.incoming.resolutionResult.allComponents.mapNotNull { it.moduleVersion }
                check(modules.none { module -> forbiddenGroups.any { module.group == it || module.group.startsWith("$it.") } }) {
                    "Forbidden Android, persistence, or neural runtime dependency on ${config.name}"
                }
            }
        val sourceFiles = fileTree("src/main") { include("**/*.kt", "**/*.java") }
        val forbiddenImports = Regex("(?m)^\\s*import\\s+(android\\.|androidx\\.|com\\.android\\.|org\\.tensorflow\\.|ai\\.onnxruntime\\.|org\\.pytorch\\.|io\\.realm\\.)")
        check(sourceFiles.none { forbiddenImports.containsMatchIn(it.readText()) }) {
            "Foundation source imports a device or runtime API"
        }
    }
}

tasks.named("check") { dependsOn(verifyFoundationBoundary) }
