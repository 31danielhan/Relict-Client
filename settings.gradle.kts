pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "relict"

// Minecraft 26.x is unobfuscated and needs a different Loom plugin, so it has its own build script.
if (providers.gradleProperty("mcVersion").orNull?.startsWith("26.") == true) {
    rootProject.buildFileName = "build.mojmap.gradle.kts"
}
