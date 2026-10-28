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

/**
 * Blocks that grow, and so make a farm when machines are built around them. On their own they are scenery: kelp, grass and
 * dripstone are everywhere in a fresh world, so none of this is evidence until the machine parts are there too.
 */
object Growth {
    private val GROWING = setOf(
        "kelp", "kelp_plant", "sugar_cane", "bamboo", "cactus", "wheat", "carrots", "potatoes", "beetroots", "nether_wart",
        "melon", "pumpkin", "melon_stem", "pumpkin_stem", "sweet_berry_bush", "pointed_dripstone", "budding_amethyst",
        "amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud", "small_amethyst_bud", "farmland", "composter",
    )

    fun isGrowth(id: String): Boolean = id.removePrefix("minecraft:") in GROWING

    /** The parts a farm is built from, besides the plants: they collect, push, watch and drop things. */
    fun isFarmMachine(id: String): Boolean = id.removePrefix("minecraft:") in FARM_MACHINES

    private val FARM_MACHINES = setOf("hopper", "observer", "piston", "sticky_piston", "dispenser", "dropper", "cauldron", "water_cauldron")
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

    /**
     * True for blocks that only a player would have put underground: planks, wool, stairs and slabs, doors, signs, beds,
     * glass, bricks, crafting stations, storage blocks of metals... Used to count how many different kinds of them a
     * chunk holds, because a base is a mix of many kinds where a natural cave has none.
     */
    fun isPlayerMade(id: String): Boolean = playerMadeCache.getOrPut(id) { playerMade(id.removePrefix("minecraft:")) }

    private val playerMadeCache = HashMap<String, Boolean>()

    private fun playerMade(name: String): Boolean =
        name in PLAYER_MADE ||
            PLAYER_MADE_SUFFIXES.any { name.endsWith(it) } ||
            (classify(name) != null && classify(name) != Category.TRAVEL && classify(name) != Category.OBSIDIAN)

    private val PLAYER_MADE = setOf(
        "crafting_table", "smithing_table", "fletching_table", "cartography_table", "loom", "grindstone", "stonecutter", "anvil",
        "chipped_anvil", "damaged_anvil", "enchanting_table", "bookshelf", "lectern", "jukebox", "note_block", "beacon", "conduit",
        "iron_block", "gold_block", "diamond_block", "emerald_block", "netherite_block", "lapis_block", "raw_iron_block",
        "raw_gold_block", "raw_copper_block", "iron_bars", "chain", "ladder", "campfire", "soul_campfire", "cauldron",
        "composter", "end_rod", "glowstone", "sea_lantern", "bricks", "stone_bricks", "smooth_stone", "polished_andesite",
        "polished_granite", "polished_diorite", "tnt", "slime_block", "honey_block", "hay_block", "dried_kelp_block",
    )

    private val PLAYER_MADE_SUFFIXES = listOf(
        "_planks", "_stairs", "_slab", "_wall", "_fence", "_fence_gate", "_door", "_trapdoor", "_sign", "_wall_sign", "_hanging_sign",
        "_bed", "_carpet", "_wool", "_concrete", "_concrete_powder", "_glazed_terracotta", "_stained_glass", "_stained_glass_pane",
        "_shulker_box", "_banner", "_cut_copper", "_copper_block", "_bricks", "_tiles",
    )

    private val CONTAINERS = setOf("chest", "trapped_chest", "barrel", "ender_chest", "furnace", "blast_furnace", "smoker", "brewing_stand")
    private val MACHINES = setOf("dropper", "dispenser", "observer", "piston", "sticky_piston", "comparator", "repeater", "crafter", "daylight_detector")
    private val REDSTONE = setOf("redstone_wire", "redstone_torch", "redstone_wall_torch", "redstone_block", "lever", "tripwire_hook", "target", "redstone_lamp")
    private val TRAVEL = setOf("ladder", "rail", "powered_rail", "detector_rail", "activator_rail", "scaffolding", "torch", "wall_torch", "lantern", "soul_torch", "soul_wall_torch")
    private val CRAFTED_DEEPSLATE = listOf("deepslate_bricks", "deepslate_brick_", "deepslate_tiles", "deepslate_tile_", "polished_deepslate", "chiseled_deepslate", "cracked_deepslate")
    private val BUILDING = setOf("stone_bricks", "bricks", "glass", "glass_pane", "smooth_stone", "sea_lantern")
}
