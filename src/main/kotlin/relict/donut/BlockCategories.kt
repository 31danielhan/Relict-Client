package relict.donut

/** The kinds of block that suggest a player has been somewhere. Matching is by block id, so it is the same on every Minecraft version. */
enum class Category(val label: String) {
    CONTAINER("containers"),
    HOPPER("hoppers"),
    MACHINE("machines"),
    REDSTONE("redstone"),
    TRAVEL("shaft gear (ladders, rails, torches)"),
    CRAFTED_DEEPSLATE("crafted deepslate (bricks, tiles)"),
    COBBLESTONE("cobblestone"),
    OBSIDIAN("obsidian"),
    BUILDING("building blocks"),
}

object BlockCategories {
    private val cache = HashMap<String, Category?>()

    @Synchronized
    fun of(id: String): Category? = cache.getOrPut(id) { classify(id.removePrefix("minecraft:")) }

    private fun classify(name: String): Category? = when {
        name == "hopper" -> Category.HOPPER
        name in CONTAINERS || name.endsWith("shulker_box") -> Category.CONTAINER
        name in MACHINES -> Category.MACHINE
        name in REDSTONE || name.endsWith("_button") || name.endsWith("_pressure_plate") -> Category.REDSTONE
        name in TRAVEL -> Category.TRAVEL
        CRAFTED_DEEPSLATE.any { name.startsWith(it) } -> Category.CRAFTED_DEEPSLATE
        name == "cobblestone" || name == "mossy_cobblestone" || name == "cobbled_deepslate" -> Category.COBBLESTONE
        name == "obsidian" || name == "crying_obsidian" -> Category.OBSIDIAN
        name in BUILDING || name.endsWith("_planks") || name.endsWith("_concrete") -> Category.BUILDING
        else -> null
    }

    private val CONTAINERS = setOf("chest", "trapped_chest", "barrel", "ender_chest", "furnace", "blast_furnace", "smoker", "brewing_stand")
    private val MACHINES = setOf("dropper", "dispenser", "observer", "piston", "sticky_piston", "comparator", "repeater", "crafter", "daylight_detector")
    private val REDSTONE = setOf("redstone_wire", "redstone_torch", "redstone_wall_torch", "redstone_block", "lever", "tripwire_hook", "target", "redstone_lamp")
    private val TRAVEL = setOf("ladder", "rail", "powered_rail", "detector_rail", "activator_rail", "scaffolding", "torch", "wall_torch", "lantern", "soul_torch", "soul_wall_torch")
    private val CRAFTED_DEEPSLATE = listOf("deepslate_bricks", "deepslate_brick_", "deepslate_tiles", "deepslate_tile_", "polished_deepslate", "chiseled_deepslate", "cracked_deepslate")
    private val BUILDING = setOf("stone_bricks", "bricks", "glass", "glass_pane", "smooth_stone", "sea_lantern")
}
