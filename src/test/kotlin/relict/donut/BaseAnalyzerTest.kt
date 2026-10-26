package relict.donut

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaseAnalyzerTest {
    private fun section(minY: Int, vararg counts: Pair<String, Int>) = SectionSnapshot(minY, counts.toMap())
    private fun chunk(vararg sections: SectionSnapshot, x: Int = 0, z: Int = 0) = ChunkSnapshot(x, z, sections.toList())

    @Test
    fun anOrdinaryChunkIsNotReported() {
        val c = chunk(section(-64, "minecraft:stone" to 3000, "minecraft:deepslate" to 900, "minecraft:diamond_ore" to 4))
        assertNull(BaseAnalyzer.analyze(c))
    }

    @Test
    fun aHopperAndChestRoomIsAStash() {
        val c = chunk(
            section(-64, "minecraft:hopper" to 33, "minecraft:chest" to 10, "minecraft:stone" to 2000),
            section(-48, "minecraft:barrel" to 4, "minecraft:deepslate_bricks" to 40),
        )
        val r = assertNotNull(BaseAnalyzer.analyze(c))
        assertEquals(Kind.STASH, r.kind)
        assertTrue(r.confidence in 90..99, "confidence ${r.confidence}")
        assertEquals(2, r.evidenceSections)
        assertEquals(-64, r.minY)
        assertEquals(-33, r.maxY)
    }

    @Test
    fun redstoneAndPistonsAreAMachine() {
        val c = chunk(section(-60, "minecraft:piston" to 12, "minecraft:observer" to 12, "minecraft:redstone_wire" to 30))
        assertEquals(Kind.MACHINE, assertNotNull(BaseAnalyzer.analyze(c)).kind)
    }

    @Test
    fun laddersAndTorchesWithoutStorageAreADugShaft() {
        val c = chunk(section(-40, "minecraft:ladder" to 14, "minecraft:torch" to 8, "minecraft:air" to 600))
        val r = assertNotNull(BaseAnalyzer.analyze(c, AnalysisSettings(minScore = 20)))
        assertEquals(Kind.DUG_SHAFT, r.kind)
    }

    @Test
    fun aSingleTorchIsJustALitCave() {
        assertNull(BaseAnalyzer.analyze(chunk(section(-40, "minecraft:torch" to 1, "minecraft:rail" to 1))))
    }

    @Test
    fun onlyBlocksBelowTheHeightLimitCount() {
        val surface = chunk(section(64, "minecraft:hopper" to 50, "minecraft:chest" to 20))
        assertNull(BaseAnalyzer.analyze(surface)) // above Y 0: not scanned
        val r = assertNotNull(BaseAnalyzer.analyze(surface, AnalysisSettings(maxY = 200)))
        assertEquals(Kind.STASH, r.kind)
    }

    @Test
    fun theEvidenceExplainsEveryPoint() {
        val c = chunk(section(-64, "minecraft:cobblestone" to 246, "minecraft:obsidian" to 753, "minecraft:deepslate_tiles" to 35))
        val r = assertNotNull(BaseAnalyzer.analyze(c))
        assertEquals(r.score, r.evidence.sumOf { it.points })
        assertEquals(listOf("obsidian 753", "cobblestone 246", "crafted deepslate 35"), r.evidence.map { it.text })
        assertEquals(listOf(10, 8, 6), r.evidence.map { it.points })
    }

    @Test
    fun neighbouringFlaggedChunksRaiseTheScore() {
        val hot = { x: Int -> chunk(section(-64, "minecraft:hopper" to 10, "minecraft:chest" to 5), x = x) }
        val alone = assertNotNull(BaseAnalyzer.analyze(hot(0)))
        val both = BaseAnalyzer.withNeighbourBonus(listOfNotNull(BaseAnalyzer.analyze(hot(0)), BaseAnalyzer.analyze(hot(1))))
        assertEquals(alone.score + 6, both[0].score)
        assertTrue(both[0].evidence.any { "next door" in it.text })
        val far = BaseAnalyzer.withNeighbourBonus(listOfNotNull(BaseAnalyzer.analyze(hot(0)), BaseAnalyzer.analyze(hot(5))))
        assertEquals(alone.score, far[0].score)
    }

    @Test
    fun fingerprintChangesWhenBlocksChange() {
        val a = chunk(section(-64, "minecraft:hopper" to 3))
        val same = chunk(section(-64, "minecraft:hopper" to 3))
        val changed = chunk(section(-64, "minecraft:hopper" to 4))
        assertEquals(a.fingerprint, same.fingerprint)
        assertNotEquals(a.fingerprint, changed.fingerprint)
    }

    @Test
    fun blockCategoriesCoverVariantsByName() {
        assertEquals(Category.CONTAINER, BlockCategories.of("minecraft:blue_shulker_box"))
        assertEquals(Category.CRAFTED_DEEPSLATE, BlockCategories.of("minecraft:deepslate_brick_stairs"))
        assertEquals(Category.REDSTONE, BlockCategories.of("minecraft:stone_button"))
        assertEquals(Category.BUILDING, BlockCategories.of("minecraft:oak_planks"))
        assertNull(BlockCategories.of("minecraft:deepslate"))
        assertNull(BlockCategories.of("minecraft:diamond_ore"))
    }

    @Test
    fun confidenceNeverClaimsCertainty() {
        assertEquals(99, BaseAnalyzer.confidence(100_000))
        assertTrue(BaseAnalyzer.confidence(20) in 50..60)
    }
}
