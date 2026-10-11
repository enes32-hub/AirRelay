plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

import java.util.Properties

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

// Brand-consistent property names. The legacy aynatv.* keys are still accepted for
// one release so existing local.properties files and build scripts keep working.
fun gradleProp(name: String): Provider<String> =
    providers.gradleProperty("airrelay.$name").orElse(providers.gradleProperty("aynatv.$name"))

fun localProp(name: String): String? =
    localProps.getProperty("airrelay.$name") ?: localProps.getProperty("aynatv.$name")

// The normal development target is the Android TV emulator. A release build selects its real-TV
// ABIs explicitly, e.g. -Pairrelay.abis=arm64-v8a,armeabi-v7a.
//
// The fallback depends on what was requested: defaulting a *release* invocation to x86_64 silently
// produced an emulator-only artifact, which cannot be installed on a real TV at all.
val releaseRequested = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
val defaultAbis = if (releaseRequested) "arm64-v8a,armeabi-v7a" else "x86_64"
val allAbis = gradleProp("abis")
    .orElse(defaultAbis)
    .get()
    .split(',')
    .map(String::trim)
    .filter(String::isNotEmpty)

val pythonExecutable = gradleProp("python")
    .orElse(localProp("python") ?: "python3")
    .get()
val patchProgram = rootProject.file("tools/patch.cmd").absolutePath.replace('\\', '/')
val msysRoot = gradleProp("msys.root")
    .orElse("C:/msys64")
    .get()
val cmakeCommand = gradleProp("cmake.command")
    .orElse(localProp("cmake.command") ?: "")
    .get()

android {
    // Keep the upstream source namespace until its Kotlin packages are
    // deliberately renamed. Keep the installed package ID for AirRelay update compatibility.
    namespace = "io.github.jqssun.airplay"
    compileSdk = 36
    // Local validation uses the installed NDK; upstream pins an earlier NDK 27 release.
    ndkVersion = "29.0.14206865"

    if (localProps.containsKey("storeFile")) {
        signingConfigs {
            create("release") {
                storeFile = file(localProps.getProperty("storeFile"))
                storePassword = localProps.getProperty("storePassword")
                keyAlias = localProps.getProperty("keyAlias")
                keyPassword = localProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.airrelay.receiver"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.4"
        testInstrumentationRunner = "io.github.jqssun.airplay.IntegrationInstrumentation"

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                arguments += "-DPython_EXECUTABLE=$pythonExecutable"
                arguments += "-DPATCH_PROGRAM=$patchProgram"
                arguments += "-DAIRRELAY_MSYS_ROOT=$msysRoot"
                if (cmakeCommand.isNotBlank()) arguments += "-DAIRRELAY_CMAKE_COMMAND=$cmakeCommand"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    // All 16 languages must remain available for the in-app language selector.
    bundle { language { enableSplit = false } }

    buildTypes {
        debug {
            ndk { abiFilters += allAbis }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
            ndk { abiFilters += allAbis }
        }
        // debuggable build with HWASan (arm64) + UBSan in native code
        create("sanitize") {
            initWith(getByName("debug"))
            matchingFallbacks += "debug"
            ndk {
                abiFilters.clear()
                abiFilters += "arm64-v8a"
            }
            externalNativeBuild {
                cmake { arguments += "-DSANITIZE=ON" }
            }
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
        prefab = true
    }
}

// The vendored UxPlay sources are compiled in their applied form. They are NOT pristine
// upstream: patches/UxPlay/0001-0007 record how the tree was produced, and
// patches/UxPlay/README.md states the exact chain (see also the reproduction steps there).
// Keeping the applied form in this distribution makes a clean source archive reproducible
// without depending on a nested Git worktree.

tasks.withType<Zip>().configureEach {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.datastore.prefs)
    implementation(libs.androidx.media)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui.compose.material3)
    implementation(libs.media3.ui)
    implementation(libs.media3.transformer)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.oboe)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
}

