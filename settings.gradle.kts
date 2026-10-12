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
        val candidateRepository = providers.gradleProperty("otus.meta.repository").orNull
        if (candidateRepository != null) {
            // Validate published artifacts without downloading or substituting Otus source projects.
            exclusiveContent {
                forRepository {
                    maven(candidateRepository) { name = "otusMetaCandidate" }
                }
                filter { includeGroup("com.otus") }
            }
        } else {
            // Shared Otus libraries. Downloading needs a token; see docs/shared-libraries.md.
            // Gradle checks credentials before every build; without a token only -Potus.meta.path works.
            val hasToken = providers.gradleProperty("otusMetaPassword").isPresent
            // Exclusive content keeps Maven Central from serving a look-alike com.otus artifact.
            exclusiveContent {
                forRepository {
                    maven("https://maven.pkg.github.com/reazndev/otus-meta") {
                        name = "otusMeta"
                        if (hasToken) credentials(PasswordCredentials::class)
                    }
                }
                filter { includeGroup("com.otus") }
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":shared")
include(":androidApp")

// Opt-in local development against an otus-meta checkout, such as -Potus.meta.path=../otus-meta.
// An explicit artifact repository takes precedence over a machine-wide composite setting.
if (!providers.gradleProperty("otus.meta.repository").isPresent) {
    providers.gradleProperty("otus.meta.path").orNull?.let { includeBuild(it) }
}
