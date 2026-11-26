plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val ktorVersion = libs.versions.ktor.get()

dependencies {
    implementation(project(":contracts"))
    implementation(project(":backend:platform-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Ktor server (engine is owned by :backend:server)
    api(libs.ktor.server.core)
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-sse:$ktorVersion")

    // Exposed (Database is part of the public ChatDependencies surface)
    api(libs.exposed.core)
    api(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)

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
