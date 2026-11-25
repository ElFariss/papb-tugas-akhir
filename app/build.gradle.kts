plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "id.tunarasa.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.tunarasa.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":ai-client"))
    implementation(project(":contracts"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    // Tambahan FE (langsung di app/build.gradle.kts agar tidak menyentuh shared gradle/)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // CameraX PreviewView (wajib untuk :ai-client)
    implementation("androidx.camera:camera-view:1.4.1")

    // DataStore (simpan sessionId anonim & JWT admin)
    implementation("androidx.datastore:datastore-preferences:1.1.2")

    // Ktor Client + JSON Serialization (sesuai ekosistem Kotlin & :contracts)
    val ktorVersion = "3.0.3"
    implementation("io.ktor:ktor-client-android:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets:$ktorVersion")

    // QR Scanner (Google Code Scanner - ringan, tanpa setup kamera manual)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    // TODO(FE): Ktor or Retrofit, DataStore, camera-view (PreviewView), QR scanner
}
