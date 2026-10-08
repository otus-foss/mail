rootProject.name = "otus-mail"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // Shared Otus libraries. Downloading needs a token; see docs/shared-libraries.md.
        maven("https://maven.pkg.github.com/reazndev/otus-meta") {
            name = "otusMeta"
            // Gradle checks credentials before every build; without a token only -Potus.meta.path works.
            if (providers.gradleProperty("otusMetaPassword").isPresent) credentials(PasswordCredentials::class)
            mavenContent { includeGroup("com.otus") }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":shared")
include(":androidApp")

// Opt-in local development against an otus-meta checkout, such as -Potus.meta.path=../otus-meta.
providers.gradleProperty("otus.meta.path").orNull?.let { includeBuild(it) }
