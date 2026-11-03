// Pure Kotlin JVM: stdlib + java.time only (LLD §1). No Android, no I/O.
plugins {
    id("mukhar.jvm.library")
}

dependencies {
    // Tests read the shared JSON vectors (also consumed by the Worker's Vitest suite).
    testImplementation(libs.kotlinx.serialization.json)
}

val testVectors = rootProject.layout.projectDirectory.dir("../shared/test-vectors")

tasks.test {
    inputs.dir(testVectors).withPropertyName("testVectors").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("mukhar.testVectors", testVectors.asFile.absolutePath)
}
