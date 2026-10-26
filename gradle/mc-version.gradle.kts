// Applied first by both build scripts. ./gradlew build -PmcVersion=1.21.10 builds for another Minecraft version:
// versions/<mc>.properties overrides the defaults in gradle.properties (which target the newest Yarn-named version).
import java.util.Properties

(findProperty("mcVersion") as String?)?.let { mc ->
    val overrides = file("versions/$mc.properties")
    require(overrides.exists()) { "Unsupported mcVersion '$mc': there is no ${overrides.relativeTo(rootDir)}" }
    Properties().apply { overrides.inputStream().use(::load) }
        .forEach { (key, value) -> extra[key.toString()] = value.toString() }
}
