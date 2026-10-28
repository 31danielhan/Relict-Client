package relict.donut

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockColorsTest {
    @Test
    fun aDyedFamilyMatchesEveryColour() {
        val m = BlockColors.Matcher("shulker_box")
        assertTrue(m.matches("minecraft:shulker_box"))
        assertTrue(m.matches("minecraft:light_blue_shulker_box"))
        assertTrue(m.matches("minecraft:purple_shulker_box"))
        assertFalse(m.matches("minecraft:chest"))
    }

    @Test
    fun aPlainNameMatchesOnlyThatBlock() {
        val m = BlockColors.Matcher("chest, minecraft:spawner")
        assertTrue(m.matches("minecraft:chest"))
        assertTrue(m.matches("minecraft:spawner"))
        assertFalse(m.matches("minecraft:trapped_chest"))
        assertFalse(m.matches("minecraft:ender_chest"))
    }

    @Test
    fun everyColourOfAFamilyGetsTheSameOutlineColour() {
        val light = BlockColors.rgb("minecraft:light_gray_shulker_box")
        assertEquals(light, BlockColors.rgb("minecraft:red_shulker_box"))
        assertEquals(light, BlockColors.rgb("minecraft:shulker_box"))
        assertEquals(0xCE93D8, light)
        assertEquals(BlockColors.rgb("minecraft:white_bed"), BlockColors.rgb("minecraft:black_bed"))
    }

    @Test
    fun differentKindsOfBlockGetDifferentColours() {
        assertTrue(BlockColors.rgb("minecraft:chest") != BlockColors.rgb("minecraft:trapped_chest"))
        assertTrue(BlockColors.rgb("minecraft:chest") != BlockColors.rgb("minecraft:shulker_box"))
    }

    @Test
    fun anUnlistedBlockStillGetsASteadyColour() {
        assertEquals(BlockColors.rgb("minecraft:lodestone"), BlockColors.rgb("minecraft:lodestone"))
    }

    @Test
    fun anEmptyListMatchesNothing() {
        assertTrue(BlockColors.Matcher("  , ;").isEmpty)
    }
}
