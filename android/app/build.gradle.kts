plugins {
    id("mukhar.android.application")
    id("mukhar.android.compose")
    id("mukhar.hilt")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "io.github.ukpratik.mukhar.app"

    defaultConfig {
        applicationId = "io.github.ukpratik.mukhar"
        versionCode = 1
        versionName = "1.0.0"
    }

    // D-044: one app ID for both. "fdroid" is the F-Droid build (no Play Billing); "play" is for Google Play later.
    flavorDimensions += "store"
    productFlavors {
        create("fdroid") { dimension = "store" }
        create("play") { dimension = "store" }
    }

    // Upload key for Google Play (Play App Signing re-signs for users), same pattern as Folio. Credentials live in
    // ~/.gradle/gradle.properties (MUKHAR_RELEASE_*), the keystore in ~/.android-keys/, never in the repo
    // (LLD §7, launch-checklist "Signing setup"). Without them the release build is signed with the debug key so it
    // still builds and installs locally (D-036); such an APK must never be uploaded (Play rejects debug-signed APKs).
    val releaseStore = providers.gradleProperty("MUKHAR_RELEASE_STORE_FILE").orNull
    signingConfigs {
        if (releaseStore != null) {
            create("upload") {
                storeFile = file(releaseStore)
                storePassword = providers.gradleProperty("MUKHAR_RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("MUKHAR_RELEASE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("MUKHAR_RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // R8 full mode (AGP default). The mapping file is kept at build/outputs/mapping/release/mapping.txt and
            // archived with each build in ~/Desktop/PersonalProjects/builds/mukhar/<version>/ (NFR-005 crash triage).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // D-044: without the upload key the release APK stays unsigned (F-Droid builds from source and signs it
            // itself). Pass -Pmukhar.debugSignRelease=true to sign a local test build with the debug key instead.
            // Keep this on one line with spaces: F-Droid's build server deletes the signingConfigs block and any line
            // shaped like "signingConfig = x" (fdroidserver remove_signing_keys), which would leave a split line dangling.
            val debugSign = providers.gradleProperty("mukhar.debugSignRelease").orNull == "true"
            signingConfig = signingConfigs.findByName("upload") ?: (if (debugSign) signingConfigs.getByName("debug") else null)
        }
    }

    buildFeatures.buildConfig = true

    // F-Droid: don't embed the Google-encrypted dependency metadata block in the APK/AAB (its scanner flags it).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // Robolectric Compose tests need merged resources.
    testOptions.unitTests.isIncludeAndroidResources = true
}

// NFR-002: the Baseline Profile (startup → Today → Talk) is generated on a device by :baselineprofile and committed
// under src/release/generated/baselineProfiles; profileinstaller compiles it on first launch for sideloads too.
baselineProfile {
    // Generation needs a connected device or emulator; normal builds just use the committed profile.
    automaticGenerationDuringBuild = false
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    // ViewModel tests build a real SettingsStore on a temp DataStore.
    testImplementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.compose.ui.test.manifest)
}
