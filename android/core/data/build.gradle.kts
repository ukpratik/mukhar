// Android data layer (LLD §1): Room, audio (recorder, VAD feed, AAC/ADTS), net (Worker API v1), repositories,
// WorkManager (analysis queue) and bundled content. Debug-only fakes live in src/debug (build-plan "Debug hooks").
plugins {
    id("vaak.android.library")
    id("vaak.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    buildFeatures.buildConfig = true

    buildTypes {
        // LLD §7: the API base URL comes from BuildConfig per build type. The Worker isn't deployed yet (Node and
        // keys are pending, build-plan P1/P2), so both point at a placeholder that never resolves.
        debug {
            buildConfigField("String", "API_BASE_URL", "\"https://api.vaak.invalid/v1/\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://api.vaak.invalid/v1/\"")
        }
    }

    testOptions.unitTests.isIncludeAndroidResources = true
}

// LLD §2.1: exportSchema = true, schemas committed; every shipped version stays frozen in core/data/schemas.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    api(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.okhttp.mockwebserver)
}
