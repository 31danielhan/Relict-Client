// Build for Minecraft 26.x, which ships unobfuscated (Mojang names, no Yarn, no remapping) and needs Java 25.
// settings.gradle.kts picks this script instead of build.gradle.kts when -PmcVersion is 26.*.
// Shared logic lives in gradle/*.gradle.kts; keep the two build scripts' version handling in step.
import com.github.gradle.node.npm.task.NpmTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom") version "1.19.3"
    kotlin("jvm") version "2.4.21"
    id("com.github.node-gradle.node") version "7.1.0"
}

apply(from = "gradle/mc-version.gradle.kts")

val minecraftVersion = property("minecraft_version") as String
val compatDirs = (property("compat_dirs") as String).split(",").map(String::trim)

version = property("mod_version") as String
group = property("maven_group") as String

base {
    archivesName.set("${property("archives_base_name")}-mc$minecraftVersion")
}

sourceSets.main {
    compatDirs.forEach { kotlin.srcDir("src/compat/$it/kotlin") }
    kotlin.srcDir("src/compat/$minecraftVersion/kotlin")
}

repositories {
    mavenCentral()
    maven("https://maven.dimaskama.net/releases") { name = "DimasKama" } // MCEF Modern
}

// No mappings and no mod* configurations: nothing is remapped on 26.x.
dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    implementation("${property("mcef_coordinate")}:${property("mcef_version")}")
    testImplementation(kotlin("test"))
    compileOnly("me.friwi:jcef-api:${property("jcef_api_version")}")
}

tasks.test {
    useJUnitPlatform()
}

fabricApi {
    configureTests {
        createSourceSet = true
        modId = "relict-gametest"
        enableGameTests = false
        enableClientGameTests = true
        eula = true
        clearRunDirectory = false // keeps the downloaded Chromium between runs
    }
}

sourceSets.matching { it.name == "gametest" }.configureEach {
    compatDirs.forEach { kotlin.srcDir("src/gametest-compat/$it/kotlin") }
}

// The web UI (ui/) is built with a Node that Gradle downloads into .gradle/, so nothing needs installing.
node {
    download.set(true)
    version.set("24.22.1")
    nodeProjectDir.set(layout.projectDirectory.dir("ui"))
}

tasks.register<NpmTask>("buildUi") {
    dependsOn(tasks.named("npmInstall"))
    args.set(listOf("run", "build"))
    inputs.dir("ui/src")
    inputs.files("ui/index.html", "ui/package.json", "ui/package-lock.json", "ui/vite.config.ts", "ui/svelte.config.js", "ui/tsconfig.json")
    outputs.dir(layout.buildDirectory.dir("ui"))
}

apply(from = "gradle/resources.gradle.kts")

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}
