import com.github.gradle.node.npm.task.NpmTask

plugins {
    kotlin("jvm") version "2.4.21"
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.github.node-gradle.node") version "7.1.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.formdev:flatlaf:3.7.2")
    implementation("com.formdev:flatlaf-extras:3.7.2") // draws the SVG logo (only for --export-icon)
    implementation("org.xerial:sqlite-jdbc:3.50.3.0") // reads the Modrinth App's profile list
    implementation("com.google.code.gson:gson:2.15.0")
    testImplementation(kotlin("test"))
}

// The window is a web page in a JavaFX WebView. Gradle picks the JavaFX files for the computer it runs on, which is why
// each installer is built on its own system (see .github/workflows/release.yml).
javafx {
    version = "25.0.5"
    modules = listOf("javafx.web")
}

// The page (ui/src/installer) is built with the same Node the main build downloads into ../.gradle/.
node {
    download.set(true)
    version.set("24.22.1")
    workDir.set(file("../.gradle/nodejs"))
    npmWorkDir.set(file("../.gradle/npm"))
    nodeProjectDir.set(file("../ui"))
}

// The downloaded Node folder can hold files Gradle can't read (cloud-synced folders add "name 2" copies), so its setup isn't tracked.
tasks.named("nodeSetup") { doNotTrackState("Node is downloaded once into ../.gradle/nodejs") }
tasks.named("npmSetup") { doNotTrackState("npm comes with Node") }

val buildWeb = tasks.register<NpmTask>("buildWeb") {
    dependsOn(tasks.named("npmInstall"))
    args.set(listOf("run", "build:installer"))
    inputs.dir("../ui/src")
    inputs.files("../ui/installer.html", "../ui/package.json", "../ui/package-lock.json", "../ui/vite.installer.config.ts", "../ui/svelte.config.js", "../ui/tsconfig.json")
    outputs.dir(layout.buildDirectory.dir("generated/web"))
}

// Compiled for Java 21 by whichever JDK runs Gradle (21 or newer), so CI doesn't need a separate Java 21 install.
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}

application {
    mainClass.set("relict.installer.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

// The installer carries the Relict jars inside itself; build them first with scripts/build-all.sh.
val bundleJars = tasks.register<Sync>("bundleJars") {
    from("../dist") { include("relict-mc*.jar") }
    into(layout.buildDirectory.dir("generated/bundled/jars"))
}

// The logo lives in branding/ and is shown in the installer's window.
val bundleBranding = tasks.register<Copy>("bundleBranding") {
    from("../branding") { include("relict-logo.svg", "relict-256.png") }
    into(layout.buildDirectory.dir("generated/bundled"))
}

// The installer wears the same version as the client, so there is one number to bump (mod_version in ../gradle.properties).
val writeInstallerInfo = tasks.register("writeInstallerInfo") {
    val modVersion = providers.fileContents(layout.projectDirectory.file("../gradle.properties")).asText.map { text ->
        Regex("^mod_version=(\\S+)", RegexOption.MULTILINE).find(text)?.groupValues?.get(1) ?: error("mod_version is missing from gradle.properties")
    }
    val out = layout.buildDirectory.file("generated/info/installer.properties")
    inputs.property("version", modVersion)
    outputs.file(out)
    doLast { out.get().asFile.apply { parentFile.mkdirs(); writeText("version=${modVersion.get()}\n") } }
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated/info"))
    resources.srcDir(layout.buildDirectory.dir("generated/bundled"))
    resources.srcDir(layout.buildDirectory.dir("generated/web")) // the page: web/index.html
}

tasks.processResources {
    dependsOn(bundleJars, bundleBranding, buildWeb, writeInstallerInfo)
}

// One double-clickable jar with everything inside (also the input for jpackage).
tasks.register<Jar>("fatJar") {
    archiveFileName.set("RelictInstaller.jar")
    destinationDirectory.set(layout.buildDirectory.dir("fat"))
    manifest { attributes["Main-Class"] = "relict.installer.MainKt" }
    from(sourceSets.main.get().output)
    from({ configurations.runtimeClasspath.get().map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    // sqlite-jdbc carries a native library for every system; keep only the one this installer runs on.
    exclude("org/sqlite/native/Linux*/**", "org/sqlite/native/FreeBSD/**")
    if (org.gradle.internal.os.OperatingSystem.current().isWindows) exclude("org/sqlite/native/Mac/**", "org/sqlite/native/Windows/aarch64/**", "org/sqlite/native/Windows/x86/**")
    else exclude("org/sqlite/native/Windows/**", "org/sqlite/native/Mac/x86_64/**")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
