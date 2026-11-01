// Android data layer (LLD §1). M0: Room schema v1 + DAOs only; repositories, audio, net and work land in M2+.
plugins {
    id("vaak.android.library")
    id("vaak.hilt")
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
}

// LLD §2.1: exportSchema = true, schemas committed; every shipped version stays frozen in core/data/schemas.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
