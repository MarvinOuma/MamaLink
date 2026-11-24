plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// Match Robolectric 4.14.1 DefaultSdkProvider (instrumentation revision i7).
// Resolve these with Gradle so tests never download SDKs through MavenArtifactFetcher.
val robolectricSdkVersions = listOf(
    "6.0.1_r3-robolectric-r1-i7", // API 23 (minimum)
    "9-robolectric-4913185-2-i7", // API 28 (Room)
    "15-robolectric-12650502-i7", // API 35 (target / resource loading)
)
// Separate configurations prevent Gradle selecting only the newest SDK version.
val robolectricSdks = robolectricSdkVersions.mapIndexed { index, version ->
    configurations.create("robolectricSdk$index") {
        isCanBeConsumed = false
        isTransitive = false
    }.also {
        dependencies.add(it.name, "org.robolectric:android-all-instrumented:$version")
    }
}
val prepareRobolectricSdks by tasks.registering(Sync::class) {
    from(robolectricSdks)
    into(layout.buildDirectory.dir("robolectric-sdks"))
    doLast {
        robolectricSdkVersions.forEach { version ->
            check(destinationDir.resolve("android-all-instrumented-$version.jar").isFile) {
                "Missing pinned Robolectric SDK: $version"
            }
        }
    }
}

android {
    namespace = "com.mamalink.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mamalink.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { viewBinding = true }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.dependsOn(prepareRobolectricSdks)
                it.testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                it.systemProperty("robolectric.offline", "true")
                it.systemProperty("robolectric.dependency.dir", layout.buildDirectory.dir("robolectric-sdks").get().asFile.absolutePath)
            }
        }
    }
    sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.material)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.arch.testing)
    testImplementation(libs.coroutines.test)
}
