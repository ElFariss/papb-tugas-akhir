pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}



dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "tunarasa"

// Owner of each module: see contracts/ownership.map
include(":app")                     // FE
include(":ai-client")               // AI-1
include(":contracts")               // shared, changed only with every affected owner's approval
include(":backend:server")          // BE-1 (bootstrap: wires the three modules below)
include(":backend:platform-core")   // BE-1
include(":backend:realtime-chat")   // BE-2
include(":backend:rag-llm")         // AI-2
