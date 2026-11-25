pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Isolate the large test SDK artifacts from Maven Central's rate-limited host.
        exclusiveContent {
            forRepository {
                maven {
                    name = "robolectricSdkMirror"
                    url = uri("https://maven-central.storage-download.googleapis.com/maven2")
                }
            }
            filter { includeModule("org.robolectric", "android-all-instrumented") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "MamaLink"
include(":app")
