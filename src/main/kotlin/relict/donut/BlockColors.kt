package relict.donut

import kotlin.math.abs

/**
 * What Block ESP matches and how it colours it. A block that comes in colours (shulker boxes, beds, banners, wool...) is one
 * family: asking for "shulker_box" matches every colour of it, and they all get the family's one colour, so the outlines
 * tell you what a block is rather than which dye it has.
 */
object BlockColors {
    /** Blocks that come in the sixteen dye colours. A name in this list also matches "light_blue_<name>" and the rest. */
    private val DYED = setOf(
        "shulker_box", "bed", "banner", "wool", "carpet", "concrete", "concrete_powder", "terracotta", "glazed_terracotta",
        "stained_glass", "stained_glass_pane", "candle",
    )

    private val DYES = listOf(
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown",
        "green", "red", "black",
    )

    private val FAMILY_COLORS = mapOf(
        "shulker_box" to 0xCE93D8, // light purple
        "chest" to 0xFFA726, "trapped_chest" to 0xEF5350, "ender_chest" to 0x7E57C2, "barrel" to 0x8D6E63,
        "hopper" to 0x90A4AE, "dispenser" to 0xB0BEC5, "dropper" to 0xB0BEC5, "furnace" to 0xBDBDBD, "blast_furnace" to 0xBDBDBD, "smoker" to 0xBDBDBD,
        "spawner" to 0xE53935, "trial_spawner" to 0xE53935, "beacon" to 0x4DD0E1, "bed" to 0xF06292, "banner" to 0xFFB74D,
        "enchanting_table" to 0x7E57C2, "bookshelf" to 0xA1887F, "anvil" to 0x78909C, "tnt" to 0xE53935,
        "diamond_block" to 0x4DD0E1, "emerald_block" to 0x2ECC71, "gold_block" to 0xFFD54F, "iron_block" to 0xCFD8DC,
        "netherite_block" to 0x6D4C41, "lapis_block" to 0x3F51B5, "redstone_block" to 0xE53935, "observer" to 0x9E9E9E, "piston" to 0xA1887F,
        "sticky_piston" to 0x9CCC65, "ender_portal_frame" to 0x26A69A, "end_portal_frame" to 0x26A69A,
    )

    /** The family a block id belongs to: the id itself, or for a dyed block the name without its colour. */
    fun family(id: String): String {
        val name = id.removePrefix("minecraft:")
        for (dye in DYES) if (name.startsWith(dye + "_") && name.removePrefix(dye + "_") in DYED) return name.removePrefix(dye + "_")
        return name
    }

    /** 0xRRGGBB for a block. Anything without a colour of its own gets a steady one made from its family name. */
    fun rgb(id: String): Int {
        val family = family(id)
        FAMILY_COLORS[family]?.let { return it }
        val hue = (abs(family.hashCode()) % 360) / 360f
        return java.awt.Color.HSBtoRGB(hue, 0.55f, 0.95f) and 0xFFFFFF
    }

    /** What the player typed in the settings: names separated by commas, spaces or semicolons. */
    class Matcher(text: String) {
        private val exact = HashSet<String>()
        private val families = HashSet<String>()

        init {
            for (raw in text.split(',', ' ', ';', '\n')) {
                val name = raw.trim().lowercase().removePrefix("minecraft:")
                if (name.isEmpty()) continue
                if (name in DYED) families += name else exact += "minecraft:$name"
            }
        }

        val isEmpty: Boolean get() = exact.isEmpty() && families.isEmpty()

        fun matches(id: String): Boolean = id in exact || (families.isNotEmpty() && family(id) in families)
    }
}
