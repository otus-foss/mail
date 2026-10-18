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
    description = "Analyze all hand-written Kotlin source with zero allowed findings."
    classpath = detektTool
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    args(
        "--input",
        "shared/src,androidApp/src",
        "--config",
        "config/detekt.yml",
        "--build-upon-default-config",
        "--excludes",
        "**/build/**",
        "--report",
        "html:build/reports/detekt.html",
    )
}

tasks.register<Exec>("architectureCheck") {
    group = "verification"
    commandLine("python3", "tools/check_architecture.py")
}

val qualityPython =
    providers.provider {
        val localPython = layout.projectDirectory.file(".venv/bin/python").asFile
        if (localPython.exists()) localPython.absolutePath else "python3"
    }

tasks.register<Exec>("architectureTest") {
    group = "verification"
    commandLine("python3", "-m", "unittest", "discover", "-s", "tools/tests")
}

tasks.register<Exec>("ruffCheck") {
    group = "verification"
    commandLine(qualityPython.get(), "-m", "ruff", "check", "tools")
}

tasks.register<Exec>("ruffFormatCheck") {
    group = "verification"
    commandLine(qualityPython.get(), "-m", "ruff", "format", "--check", "tools")
}

tasks.register<Exec>("ruffFix") {
    group = "formatting"
    commandLine(qualityPython.get(), "-m", "ruff", "check", "--fix", "tools")
}

tasks.register<Exec>("ruffFormat") {
    group = "formatting"
    dependsOn("ruffFix")
    commandLine(qualityPython.get(), "-m", "ruff", "format", "tools")
}

tasks.register("formatAll") {
    group = "formatting"
    description = "Format Kotlin, project text, and Python tooling."
    dependsOn("spotlessApply", "ruffFormat")
}

tasks.register("checkAll") {
    group = "verification"
    description = "Run portable quality checks, Android lint, and debug compilation."
    dependsOn(
        "qualityCheck",
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
subprojects { apply(plugin = "org.jetbrains.kotlinx.kover") }
