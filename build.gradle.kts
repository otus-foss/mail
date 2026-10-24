import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

plugins {
    id("com.diffplug.spotless") version "8.10.3"
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.kover)
}

spotless {
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint("1.8.0")
    }
    format("projectText") {
        target("**/*.md", "**/*.yml", "**/*.yaml", "**/*.json", "**/*.xml", "**/*.plist", "**/*.toml")
        targetExclude("**/build/**", "**/.gradle/**", "**/.venv/**", "**/.git/**", "**/.idea/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "**/*.gradle.kts")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint("1.8.0")
    }
}

val detektTool = configurations.create("detektTool")
repositories { mavenCentral() }
dependencies { add(detektTool.name, "io.gitlab.arturbosch.detekt:detekt-cli:1.23.8") }

tasks.register<JavaExec>("detekt") {
    group = "verification"
    description = "Analyze hand-written main Kotlin source with zero allowed findings. Tests get ktlint only."
    classpath = detektTool
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    args(
        "--input",
        "shared/src,androidApp/src",
        "--config",
        "config/detekt.yml",
        "--build-upon-default-config",
        "--excludes",
        "**/build/**,**/src/test/**,**/src/*Test/**",
        "--report",
        "html:build/reports/detekt.html",
    )
    // Declared inputs and outputs let an unchanged tree skip detekt or restore it from the build cache.
    inputs
        .files(listOf("shared/src", "androidApp/src").map { fileTree(it) { exclude("test/**", "*Test/**") } })
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file("config/detekt.yml").withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file("build/reports/detekt.html")
    outputs.cacheIf { true }
}

tasks.register<Exec>("architectureCheck") {
    group = "verification"
    commandLine("python3", "tools/check_architecture.py")
}

// A linked worktree has no .venv of its own, so fall back to the main checkout's.
val qualityPython =
    providers.provider {
        val root = layout.projectDirectory.asFile
        val mainCheckout =
            root
                .resolve(".git")
                .takeIf { it.isFile }
                ?.readText()
                ?.substringAfter("gitdir:")
                ?.trim()
                ?.let { File(it).parentFile.parentFile.parentFile }
        listOfNotNull(root, mainCheckout)
            .map { it.resolve(".venv/bin/python") }
            .firstOrNull { it.exists() }
            ?.absolutePath ?: "python3"
    }

tasks.register<Exec>("architectureTest") {
    group = "verification"
    commandLine("python3", "-m", "unittest", "discover", "-s", "tools/tests")
}

tasks.register<Exec>("ruffCheck") {
    group = "verification"
    commandLine(qualityPython.get(), "-m", "ruff", "check", "tools", "push_bridge")
}

tasks.register<Exec>("ruffFormatCheck") {
    group = "verification"
    commandLine(qualityPython.get(), "-m", "ruff", "format", "--check", "tools", "push_bridge")
}

tasks.register<Exec>("ruffFix") {
    group = "formatting"
    commandLine(qualityPython.get(), "-m", "ruff", "check", "--fix", "tools", "push_bridge")
}

tasks.register<Exec>("ruffFormat") {
    group = "formatting"
    dependsOn("ruffFix")
    commandLine(qualityPython.get(), "-m", "ruff", "format", "tools", "push_bridge")
}

tasks.register("formatAll") {
    group = "formatting"
    description = "Format Kotlin, project text, and Python tooling."
    dependsOn("spotlessApply", "ruffFormat")
}

tasks.register<Exec>("pushBridgeTest") {
    group = "verification"
    commandLine(qualityPython.get(), "-m", "unittest", "discover", "-s", "push_bridge/tests")
}

tasks.register("checkAll") {
    group = "verification"
    description = "Run portable quality checks, Android lint, and debug compilation."
    dependsOn(
        "qualityCheck",
        "pushBridgeTest",
        ":androidApp:lintDebug",
        ":androidApp:assembleDebug",
        ":androidApp:testDebugUnitTest",
        ":shared:testAndroidHostTest",
    )
}

tasks.register("qualityCheck") {
    group = "verification"
    dependsOn("spotlessCheck", "detekt", "architectureCheck", "architectureTest", "ruffCheck", "ruffFormatCheck")
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(rootProject.tasks.named("qualityCheck"))
    }
}

dependencies { subprojects.forEach { kover(it) } }
// Instrumentation slows every test run, so collect coverage only when a Kover task runs.
val coverageRequested = gradle.startParameter.taskNames.any { "kover" in it }
subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
    if (!coverageRequested) {
        extensions.configure<KoverProjectExtension> { currentProject { instrumentation { disabledForAll.set(true) } } }
    }
}
