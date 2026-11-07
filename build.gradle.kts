plugins {
    id("otus.quality")
    alias(libs.plugins.kotlinSerialization) apply false
    alias(appLibs.plugins.sqldelight) apply false
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
