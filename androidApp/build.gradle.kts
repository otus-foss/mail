plugins {
    id("otus.android-app")
    id("otus.screenshots")
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(appLibs.androidx.work.runtime)
    implementation(appLibs.unifiedpush.connector)
    implementation(appLibs.unifiedpush.fcm)
    implementation(appLibs.sqldelight.android.driver)
    implementation(libs.compose.foundation)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(appLibs.androidx.work.testing)
    testImplementation(libs.compose.material)
    testImplementation(libs.androidx.compose.ui.test)
    testImplementation(libs.kotlinx.datetime)
    testImplementation(libs.ktor.client.mock)
}

// Dev builds show the commit they came from. A checkout without Git history shows "unknown".
val commit =
    providers
        .exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText
        .map { it.trim().ifEmpty { "unknown" } }

android {
    defaultConfig {
        // The nightly workflow increments the release number for changes in Mail or Core.
        versionCode = providers.gradleProperty("otus.versionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("otus.versionName").getOrElse("1.0")
        buildConfigField("String", "CHANNEL", "\"Dev\"")
        buildConfigField("String", "COMMIT", "\"${commit.get()}\"")
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        getByName("release") {
            buildConfigField("String", "CHANNEL", "\"Stable\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        // The dev app for daily use on a phone: debug's id and key, so it installs over a debug build,
        // but not debuggable, so Compose runs near release speed. Unshrunk, so it builds about as fast.
        create("dev") {
            initWith(getByName("debug"))
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "debug"
        }
        // Its own app id, so it installs beside a debug build signed with a different key. Gradle leaves
        // it unsigned; the nightly workflow signs it with apksigner, so the key never reaches Gradle.
        create("nightly") {
            initWith(getByName("release"))
            applicationIdSuffix = ".nightly"
            buildConfigField("String", "CHANNEL", "\"Nightly\"")
        }
    }
    // The dev build is the debug app for daily use, so it shows debug's name, "Otus Mail Dev".
    sourceSets.getByName("dev").res.srcDir("src/debug/res")
}

tasks.withType<Test>().configureEach {
    // Keep native UI-test memory bounded on shared machines.
    maxParallelForks = 2
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.security=ALL-UNNAMED",
        "--add-opens=java.base/java.text=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    )
}
