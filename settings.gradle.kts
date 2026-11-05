rootProject.name = "otus-mail"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Mail builds otus-meta from source: the sibling checkout ../otus-meta, or the checkout that the
// otus.meta.path Gradle property names. See docs/shared-libraries.md.
pluginManagement {
    val otusMeta = file(providers.gradleProperty("otus.meta.path").getOrElse("../otus-meta"))
    require(otusMeta.resolve("build-logic").isDirectory) {
        "Mail builds otus-meta from source. Clone it to $otusMeta or set otus.meta.path."
    }
    // Gradle resolves the convention plugins' own dependencies from these repositories.
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
    includeBuild(otusMeta.resolve("build-logic").path)
}

plugins {
    id("otus.settings")
}

val otusMeta = file(providers.gradleProperty("otus.meta.path").getOrElse("../otus-meta"))
includeBuild(otusMeta) { name = "otus-meta" }

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") { from(files(otusMeta.resolve("gradle/libs.versions.toml"))) }
        create("appLibs") { from(files("gradle/app.versions.toml")) }
    }
}

include(":shared")
include(":androidApp")
