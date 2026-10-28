import com.github.gradle.node.npm.task.NpmTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("fabric-loom") version "1.19.3"
    kotlin("jvm") version "2.4.21"
    id("com.github.node-gradle.node") version "7.1.0"
}

// Build for Minecraft 1.21.11 and older (Yarn names). 26.x has its own script: see build.mojmap.gradle.kts.
apply(from = "gradle/mc-version.gradle.kts")

val minecraftVersion = property("minecraft_version") as String
// Which src/compat/<name> folders this Minecraft version compiles, e.g. "modern,yarn,modern-yarn".
val compatDirs = (property("compat_dirs") as String).split(",").map(String::trim)

version = property("mod_version") as String
group = property("maven_group") as String

base {
    archivesName.set("${property("archives_base_name")}-mc$minecraftVersion")
}

// Code that differs between Minecraft versions lives in src/compat/<name>/kotlin, shared by every version that
// lists <name> in compat_dirs, and for the rare one-version difference in src/compat/<version>/kotlin.
sourceSets.main {
    compatDirs.forEach { kotlin.srcDir("src/compat/$it/kotlin") }
    kotlin.srcDir("src/compat/$minecraftVersion/kotlin")
}

repositories {
    mavenCentral()
    maven("https://maven.dimaskama.net/releases") { name = "DimasKama" } // MCEF Modern
    maven("https://mcef-download.cinemamod.com/repositories/releases") { name = "CinemaMod" } // MCEF (older Minecraft versions)
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")

    modImplementation("${property("mcef_coordinate")}:${property("mcef_version")}")
    testImplementation(kotlin("test"))
    compileOnly("me.friwi:jcef-api:${property("jcef_api_version")}")
}

tasks.test {
    useJUnitPlatform()
}

// ./gradlew runClientGametest starts a real game, creates a world and drives the mod; see src/gametest.
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

// The game test's network synchroniser sometimes decides, wrongly, that the mod touched packets and stops the run. The test
// waits with waitTicks anyway, so it runs without it.
tasks.matching { it.name == "runClientGameTest" }.configureEach {
    (this as JavaExec).systemProperty("fabric.client.gametest.disableNetworkSynchronizer", "true")
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

// Node is downloaded once into .gradle/. Gradle would otherwise read all of its thousands of files on every build to see
// that nothing changed, which is slow and fails on folders that a cloud drive syncs ("name 2" copies, evicted files).
tasks.named("nodeSetup") { doNotTrackState("Node is downloaded once into .gradle/nodejs") }
tasks.named("npmSetup") { doNotTrackState("npm comes with Node") }

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
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}
