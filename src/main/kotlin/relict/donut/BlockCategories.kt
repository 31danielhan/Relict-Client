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

/**
 * Things that lie on the ground in a farm: what its machines make and let fall. The items mobs drop on their own (bones, rotten
 * flesh, string, arrows, feathers...) are not here, and neither are plain blocks that happen to be loose, so one zombie's drops
 * or a dug-up stone do not look like a farm; it takes a pile of these.
 */
object FarmDrops {
    private val ITEMS = setOf(
        "bone_meal", "cobblestone", "basalt", "furnace", "obsidian", "iron_ingot", "gold_nugget", "gold_ingot", "sugar_cane", "bamboo",
        "kelp", "dried_kelp", "dried_kelp_block", "cactus", "wheat", "carrot", "potato", "beetroot", "melon_slice", "melon", "pumpkin",
        "sweet_berries", "glow_berries", "nether_wart", "cobbled_deepslate", "blackstone", "honeycomb", "honey_bottle", "slime_ball",
        "ender_pearl", "blaze_rod", "glowstone_dust", "netherite_scrap", "nautilus_shell", "prismarine_shard", "prismarine_crystals",
    )

    fun isFarmDrop(id: String): Boolean = id.removePrefix("minecraft:") in ITEMS

    /** How many farm drops these stacks hold in all. */
    fun count(items: Collection<relict.game.DroppedItem>): Int = items.sumOf { if (isFarmDrop(it.id)) it.count else 0 }
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

    /**
     * Blocks that only a player can have: the world never generates them anywhere, in any structure, so finding one means someone
     * crafted it and put it there. Planks, wool, stairs, fences, stone bricks and the like are left out on purpose: mineshafts,
     * strongholds, ancient cities and villages are built from them.
     */
    fun isCraftedOnly(id: String): Boolean = craftedOnlyCache.getOrPut(id) { craftedOnly(id.removePrefix("minecraft:")) }

    private val craftedOnlyCache = HashMap<String, Boolean>()

    private fun craftedOnly(name: String): Boolean =
        name in CRAFTED_ONLY || CRAFTED_ONLY_SUFFIXES.any { name.endsWith(it) } ||
            CRAFTED_ONLY_FAMILIES.any { name == it || name == "${it}_stairs" || name == "${it}_slab" || name == "${it}_wall" }

    private val CRAFTED_ONLY = setOf(
        "glass", "glass_pane", "smooth_stone", "bricks", "quartz_block", "quartz_bricks", "quartz_pillar", "chiseled_quartz_block", "smooth_quartz",
        "iron_block", "diamond_block", "emerald_block", "netherite_block", "lapis_block", "raw_iron_block", "raw_gold_block", "raw_copper_block",
        "slime_block", "honey_block", "dried_kelp_block", "beacon", "enchanting_table", "jukebox", "note_block", "lodestone", "respawn_anchor",
        "anvil", "chipped_anvil", "damaged_anvil", "crafting_table", "smithing_table", "fletching_table", "cartography_table", "loom",
        "grindstone", "stonecutter", "end_rod", "tinted_glass", "daylight_detector", "sticky_piston", "piston", "observer", "hopper", "dropper",
        "repeater", "comparator", "redstone_lamp", "redstone_block", "target", "lever", "tripwire_hook",
    )
    private val CRAFTED_ONLY_SUFFIXES = listOf("_concrete", "_concrete_powder", "_glazed_terracotta", "_stained_glass", "_stained_glass_pane", "_shulker_box")
    private val CRAFTED_ONLY_FAMILIES = listOf("polished_andesite", "polished_granite", "polished_diorite", "smooth_quartz", "brick", "smooth_stone")

    /** Redstone parts whose being switched on or off (a piston moving, a lamp lighting) shows that a machine is running. */
    fun isRedstonePart(id: String): Boolean = id.removePrefix("minecraft:").let { it in MOVING_PARTS || it.endsWith("_button") || it.endsWith("_pressure_plate") }

    private fun playerMade(name: String): Boolean =
        NATURAL_LOOKALIKES.none { it in name } && (
        name in PLAYER_MADE ||
            PLAYER_MADE_SUFFIXES.any { name.endsWith(it) } ||
            (classify(name) != null && classify(name) != Category.TRAVEL && classify(name) != Category.OBSIDIAN))

    /** Made-looking blocks that the Nether, the End and the ocean build their own structures from. */
    private val NATURAL_LOOKALIKES = listOf("nether_brick", "blackstone", "end_stone", "purpur", "prismarine", "sea_lantern")

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
    private val MACHINES = setOf("dropper", "dispenser", "observer", "piston", "sticky_piston", "comparator", "repeater", "crafter", "daylight_detector", "piston_head", "moving_piston")
    private val REDSTONE = setOf("redstone_wire", "redstone_torch", "redstone_wall_torch", "redstone_block", "lever", "tripwire_hook", "target", "redstone_lamp")
    private val TRAVEL = setOf("ladder", "rail", "powered_rail", "detector_rail", "activator_rail", "scaffolding", "torch", "wall_torch", "lantern", "soul_torch", "soul_wall_torch")
    private val CRAFTED_DEEPSLATE = listOf("deepslate_bricks", "deepslate_brick_", "deepslate_tiles", "deepslate_tile_", "polished_deepslate", "chiseled_deepslate", "cracked_deepslate")
    private val BUILDING = setOf("stone_bricks", "bricks", "glass", "glass_pane", "smooth_stone", "sea_lantern")

    private val MOVING_PARTS = REDSTONE + MACHINES + setOf("powered_rail", "note_block")
}
