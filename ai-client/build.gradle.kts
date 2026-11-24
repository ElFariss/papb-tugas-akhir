import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

/** Fetches the MediaPipe model at build time so the 7.8 MB binary never enters git. */
abstract class DownloadModelTask : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val md5: Property<String>

    @get:Input
    abstract val fileName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        val target = outputDir.get().asFile.apply { mkdirs() }.resolve(fileName.get())
        val connection = URI(url.get()).toURL().openConnection().apply {
            connectTimeout = 30_000
            readTimeout = 60_000
        }
        val bytes = connection.getInputStream().use { it.readBytes() }
        val actual = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
        check(actual == md5.get()) { "Checksum mismatch for ${url.get()}: expected ${md5.get()}, got $actual" }
        target.writeBytes(bytes)
    }
}

val downloadHandLandmarker = tasks.register<DownloadModelTask>("downloadHandLandmarker") {
    group = "ai-client"
    description = "Downloads the MediaPipe Hand Landmarker model."
    url.set("https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task")
    md5.set("15318430ea3851670fe9914116a9cfad") // from the x-goog-hash header of the published object
    fileName.set("hand_landmarker.task")
}

android {
    namespace = "id.tunarasa.ai"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(downloadHandLandmarker, DownloadModelTask::outputDir)
    }
}

tasks.withType<Test>().configureEach {
    // Folder of real hand captures for the A-Z benchmark; see README "Benchmark".
    val dataDir = layout.projectDirectory.dir("benchmark-data")
    systemProperty("sibi.benchmark.dir", dataDir.asFile.absolutePath)
    systemProperty("sibi.benchmark.report", layout.buildDirectory.file("reports/sibi-benchmark.md").get().asFile.absolutePath)
    providers.gradleProperty("sibi.benchmark.min").orNull?.let { systemProperty("sibi.benchmark.min", it) }
    inputs.files(fileTree(dataDir)).withPropertyName("benchmarkData").optional()
    testLogging { showStandardStreams = providers.gradleProperty("showBenchmark").isPresent }
}

dependencies {
    // Types from these appear in the public API (StateFlow, Preview.SurfaceProvider).
    api(libs.kotlinx.coroutines.core)
    api(libs.androidx.camera.core)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.mediapipe.tasks.vision)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
