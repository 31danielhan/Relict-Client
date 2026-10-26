plugins {
    kotlin("jvm") version "2.4.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.formdev:flatlaf:3.7.2")
    implementation("com.formdev:flatlaf-extras:3.7.2") // draws the SVG logo
    implementation("org.xerial:sqlite-jdbc:3.50.3.0") // reads the Modrinth App's profile list
    implementation("com.google.code.gson:gson:2.15.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("relict.installer.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

// The installer carries the Relict jars inside itself; build them first with scripts/build-all.sh.
val bundleJars = tasks.register<Copy>("bundleJars") {
    from("../dist") { include("relict-mc*.jar") }
    into(layout.buildDirectory.dir("generated/bundled/jars"))
}

// The logo lives in branding/ and is shown in the installer's window.
val bundleBranding = tasks.register<Copy>("bundleBranding") {
    from("../branding") { include("relict-logo.svg") }
    into(layout.buildDirectory.dir("generated/bundled"))
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated/bundled"))
}

tasks.processResources {
    dependsOn(bundleJars, bundleBranding)
}

// One double-clickable jar with everything inside (also the input for jpackage).
tasks.register<Jar>("fatJar") {
    archiveFileName.set("RelictInstaller.jar")
    destinationDirectory.set(layout.buildDirectory.dir("fat"))
    manifest { attributes["Main-Class"] = "relict.installer.MainKt" }
    from(sourceSets.main.get().output)
    from({ configurations.runtimeClasspath.get().map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
