// Pure Kotlin on purpose: the JVM plugin, not com.android.library, is what
// makes "the domain has no Android dependency" a compiler error rather than a
// review comment. See plan/10-architecture/modules.md.
plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Flow appears in the port signatures, so it is part of the public API.
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
