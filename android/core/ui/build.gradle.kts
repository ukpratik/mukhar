// Design system in Compose (design-system.md §9). No dependency on :core:data (LLD §1).
plugins {
    id("vaak.android.library")
    id("vaak.android.compose")
}

android {
    // M6: Robolectric tests render the bundled fonts, so they need merged resources.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    // M6: Compose test for mixed-script rendering (NFR-012).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
