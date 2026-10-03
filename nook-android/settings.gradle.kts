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
        // LiveKit's audio-switch dependency is published on JitPack.
        maven("https://jitpack.io")
    }
}

rootProject.name = "NOOK"
include(":app", ":core")
