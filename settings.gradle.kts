pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
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
