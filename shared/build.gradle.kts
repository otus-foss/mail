plugins {
    id("otus.kmp-app")
    alias(libs.plugins.kotlinSerialization)
    alias(appLibs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material)
            implementation(libs.compose.ui)
            implementation(libs.compose.resources)
            implementation(libs.navigation.event.compose)
            implementation(libs.kotlinx.datetime)
            api(libs.otus.jmap)
            api(libs.otus.avatar)
            api(libs.otus.dateformat)
            api(libs.otus.designsystem)
            api(libs.otus.settings)
            api(libs.otus.accounts)
            api(libs.otus.jmap.push)
            implementation(libs.otus.jscontact)
            implementation(libs.otus.jmap.contacts)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.io)
            implementation(appLibs.sqldelight.runtime)
        }
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(appLibs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.robolectric)
            implementation(appLibs.sqldelight.sqlite.driver)
        }
    }
}

// The device's copy of each login's mail. SqlMailCache in core/cache reads and writes it.
sqldelight {
    databases {
        create("MailDatabase") {
            packageName.set("com.otus.mail.core.cache")
        }
    }
}
