import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Provider-independent JMAP client (RFC 8620) and login storage. No UI and no app code, so Mail and
// Calendar can both depend on it. Every public declaration is explicit.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    explicitApi()
    compilerOptions { allWarningsAsErrors.set(true) }
    iosArm64()
    iosSimulatorArm64()
    android {
        namespace = "com.otus.jmap"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
        withHostTest {}
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        // Real network for the opt-in server integration test.
        getByName("androidHostTest").dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.robolectric)
        }
    }
}
