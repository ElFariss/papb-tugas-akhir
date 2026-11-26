plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    java
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

application {
    mainClass.set("id.tunarasa.backend.ServerKt")
}

dependencies {
    implementation(project(":contracts"))
    implementation(project(":backend:platform-core"))
    implementation(project(":backend:realtime-chat"))
    implementation(project(":backend:rag-llm"))

    // Ktor Server Framework Dependencies
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.metrics.micrometer)
    implementation(libs.micrometer.registry.prometheus)

    // Logging
    implementation(libs.logback)
}
