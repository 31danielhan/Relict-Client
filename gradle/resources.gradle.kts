// Applied by both build scripts: packs the built web UI and the mod metadata into the jar.
// (The buildUi task itself is in each build script, because it needs the Node plugin's classes.)

// `property(...)` inside a task block would look on the task, so read what fabric.mod.json needs out here.
val modMetadata = mapOf(
    "version" to project.version,
    "minecraft_dep" to property("minecraft_dep") as String,
    "java_dep" to property("java_dep") as String,
    "mcef_id" to property("mcef_id") as String,
)

tasks.named<ProcessResources>("processResources") {
    dependsOn("buildUi")
    from(layout.buildDirectory.dir("ui")) { into("assets/relict/html") }
    // Without these as inputs Gradle reuses another version's fabric.mod.json.
    modMetadata.forEach { (key, value) -> inputs.property(key, value) }
    filesMatching("fabric.mod.json") { expand(modMetadata) }
}
