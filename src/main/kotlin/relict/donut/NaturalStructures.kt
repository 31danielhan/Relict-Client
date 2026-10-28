package relict.donut

/**
 * Structures the game generates on its own that are full of blocks which, anywhere else, would be a player's work:
 * chests, planks, rails, deepslate bricks, bookshelves. A fresh world is full of them, so without this they get flagged
 * as bases. Each one is recognised by a block no player can get, or a combination no player would build, and then the
 * evidence it explains is left out of the score (the evidence that only a player produces, such as hoppers, repeaters
 * and comparators, still counts, so a base built inside one is still found).
 */
enum class Natural(val label: String, val explains: Set<Category>) {
    ANCIENT_CITY("ancient city", setOf(Category.CONTAINER, Category.CRAFTED_DEEPSLATE, Category.BUILDING, Category.TRAVEL)),
    MINESHAFT("abandoned mineshaft", setOf(Category.TRAVEL, Category.BUILDING, Category.CONTAINER)),
    STRONGHOLD("stronghold", setOf(Category.BUILDING, Category.CONTAINER, Category.TRAVEL, Category.COBBLESTONE)),
    TRIAL_CHAMBER("trial chamber", setOf(Category.MACHINE, Category.CONTAINER, Category.BUILDING, Category.TRAVEL)),
    DUNGEON("dungeon", setOf(Category.COBBLESTONE, Category.CONTAINER)),
    VILLAGE("village", setOf(Category.CONTAINER, Category.BUILDING, Category.TRAVEL, Category.COBBLESTONE)),
}

object NaturalStructures {
    /** [counts] is how many of each block the whole chunk holds, in every section. */
    fun detect(counts: Map<String, Int>): Set<Natural> {
        fun has(id: String) = (counts["minecraft:$id"] ?: 0) > 0
        fun count(id: String) = counts["minecraft:$id"] ?: 0
        fun anyEnding(suffix: String) = counts.keys.any { it.endsWith(suffix) }

        val found = HashSet<Natural>()
        // Nobody can get reinforced deepslate in survival, and sculk shriekers and catalysts only belong to ancient cities.
        if (has("reinforced_deepslate") || has("sculk_shrieker") || has("sculk_catalyst")) found += Natural.ANCIENT_CITY
        // Cobwebs through rails and fences: the mark of a mineshaft's corridors.
        if (count("cobweb") >= 3 && (has("rail") || anyEnding("_fence"))) found += Natural.MINESHAFT
        if (has("end_portal_frame") || ((has("mossy_stone_bricks") || has("cracked_stone_bricks")) && has("chiseled_stone_bricks") && has("iron_bars"))) found += Natural.STRONGHOLD
        if (has("trial_spawner") || has("vault") || anyEnding("copper_bulb") || anyEnding("copper_grate")) found += Natural.TRIAL_CHAMBER
        if (has("spawner") && count("mossy_cobblestone") + count("cobblestone") >= 3 && count("chest") <= 3) found += Natural.DUNGEON
        // A village: a bell, or the workstations its villagers use (two kinds or more), or a lot of dirt path.
        val workstations = listOf("lectern", "fletching_table", "cartography_table", "smithing_table", "stonecutter", "loom", "grindstone", "blast_furnace", "smoker", "composter").count { has(it) }
        if (has("bell") || workstations >= 2 || count("dirt_path") >= 8) found += Natural.VILLAGE
        return found
    }
}
