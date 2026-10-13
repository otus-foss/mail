import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.material)
    testImplementation(libs.androidx.compose.ui.test)
    testImplementation(libs.kotlinx.datetime)
    testImplementation(libs.ktor.client.mock)
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
    lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = true
    }
    namespace = "com.otus.mail"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.otus.mail"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        // The nightly workflow numbers each build by its commit count on main.
        versionCode = providers.gradleProperty("otus.versionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("otus.versionName").getOrElse("1.0")
        buildConfigField("boolean", "NIGHTLY", "false")
    }
    buildFeatures { buildConfig = true }
    // CI decodes the keystore from a secret. Local nightly builds without it come out unsigned.
    val nightlyKeystore = providers.environmentVariable("OTUS_NIGHTLY_KEYSTORE").orNull
    val nightlySigning =
        nightlyKeystore?.let { path ->
            signingConfigs.create("nightly") {
                val password = providers.environmentVariable("OTUS_NIGHTLY_KEYSTORE_PASSWORD").get()
                storeFile = file(path)
                storePassword = password
                keyAlias = "otus-nightly"
                keyPassword = password
            }
        }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        // Its own app id, so it installs beside a debug build signed with a different key.
        create("nightly") {
            initWith(getByName("release"))
            applicationIdSuffix = ".nightly"
            buildConfigField("boolean", "NIGHTLY", "true")
            signingConfig = nightlySigning
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
        jvmTarget = JvmTarget.JVM_11
    }
}

tasks.withType<Test>().configureEach {
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
