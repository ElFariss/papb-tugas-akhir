plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("id.tunarasa.backend.ServerKt")
}

// TODO(owner): choose Ktor or Spring Boot together (BE-1 decides) before adding framework dependencies.

dependencies {
    implementation(project(":contracts"))
    implementation(project(":backend:platform-core"))
    implementation(project(":backend:realtime-chat"))
    implementation(project(":backend:rag-llm"))
}
