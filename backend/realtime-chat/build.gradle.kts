plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

kotlin {
    jvmToolchain(21)
}

// Pinned here (not in gradle/libs.versions.toml, which is shared and needs approval).
// Move to the catalog once BE-1 settles the framework / version policy.
val ktorVersion = "3.6.2"
val exposedVersion = "1.6.1"

dependencies {
    implementation(project(":contracts"))
    implementation(project(":backend:platform-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Ktor server (engine is owned by :backend:server)
    api("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-sse:$ktorVersion")

    // Exposed (Database is part of the public ChatDependencies surface)
    api("org.jetbrains.exposed:exposed-core:$exposedVersion")
    api("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    // QR / PDF
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.zxing:javase:3.5.4")
    implementation("com.github.librepdf:openpdf:3.0.6")

    // Redis session store (platform-core owns the connection; we only need the sync API types)
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("io.lettuce:lettuce-core:7.9.0.RELEASE")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("com.h2database:h2:2.4.240")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

tasks.test {
    useJUnit()
}
