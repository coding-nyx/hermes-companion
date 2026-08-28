plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.hermes.companion.node"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Adapters see the domain and the Android SDK, and nothing else — no
    // transport, no Room, no DI. That constraint is what makes them testable.
    api(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":transport:discovery"))
    implementation(libs.kotlinx.coroutines.core)
    implementation("androidx.annotation:annotation:1.8.0")
    // T7: outbound POSTs to the active gateway from HermesNotificationListenerService.
    implementation(libs.okhttp)

    // Elevated tier (Shizuku / root)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.libsu.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core.ktx)
}
