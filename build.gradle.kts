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

// CI runs each part of checkAll as its own parallel job.
tasks.register("checkTooling") {
    group = "verification"
    dependsOn("qualityCheck", "pushBridgeTest")
}

tasks.register("checkAndroidBuild") {
    group = "verification"
    dependsOn(":androidApp:lintDebug", ":androidApp:assembleDebug")
}

tasks.register("checkTests") {
    group = "verification"
    dependsOn(":androidApp:testDebugUnitTest", ":shared:testAndroidHostTest")
}

tasks.register("checkAll") {
    group = "verification"
    description = "Run portable quality checks, Android lint, and debug compilation."
    dependsOn("checkTooling", "checkAndroidBuild", "checkTests")
}

tasks.register("qualityCheck") {
    group = "verification"
    dependsOn("spotlessCheck", "detekt", "architectureCheck", "architectureTest", "ruffCheck", "ruffFormatCheck")
}

// CI splits the tests across jobs with -Potus.testShard=<index>/<count>. A test class runs in the
// shard its name hashes to, so each class runs in exactly one job.
val testShard =
    providers
        .gradleProperty("otus.testShard")
        .orNull
        ?.split('/')
        ?.map(String::toInt)

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(rootProject.tasks.named("qualityCheck"))
    }
    if (testShard != null) {
        val (index, count) = testShard
        tasks.withType<Test>().configureEach {
            exclude { file ->
                val testClass = file.relativePath.pathString.substringBefore('$')
                !file.isDirectory && testClass.hashCode().mod(count) != index - 1
            }
        }
    }
}

dependencies { subprojects.forEach { kover(it) } }
// Instrumentation slows every test run, so collect coverage only when a Kover task runs.
val coverageRequested = gradle.startParameter.taskNames.any { "kover" in it }
// The coverage variant runs each test suite once: Shared's host tests and the app's debug unit tests,
// which hold the Robolectric screenshot tests. The total variant also runs the app's tests for every
// other build type. The coverage workflow runs koverXmlReportCoverage and koverHtmlReportCoverage.
kover { currentProject { createVariant("coverage") {} } }
subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
    extensions.configure<KoverProjectExtension> {
        currentProject {
            createVariant("coverage") { add("android", "debug", optional = true) }
            if (!coverageRequested) instrumentation { disabledForAll.set(true) }
        }
    }
}
