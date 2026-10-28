package relict.donut

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaseAnalyzerTest {
    private fun section(minY: Int, vararg counts: Pair<String, Int>) = SectionSnapshot(minY, counts.toMap())
    private fun chunk(vararg sections: SectionSnapshot, x: Int = 0, z: Int = 0) = ChunkSnapshot(x, z, sections.toList())

    @Test
    fun anOrdinaryChunkIsNotReported() {
        val c = chunk(section(-64, "minecraft:deepslate" to 3900, "minecraft:deepslate_diamond_ore" to 4, "minecraft:tuff" to 90))
        assertNull(BaseAnalyzer.analyze(c))
    }

    @Test
    fun plainStoneDeepInTheDeepslateLayerGivesAwayPlacedBlocks() {
        val natural = chunk(section(-48, "minecraft:deepslate" to 4000))
        val placed = chunk(section(-48, "minecraft:deepslate" to 3700, "minecraft:stone" to 300))
        assertNull(BaseAnalyzer.analyze(natural))
        val r = assertNotNull(BaseAnalyzer.analyze(placed))
        assertTrue(r.evidence.any { it.text.startsWith("stone in the deepslate layer") }, "${r.evidence}")
    }

    @Test
    fun stoneNearTheSurfaceIsNormal() {
        // Above the deepslate layer plain stone is what the world is made of.
        assertNull(BaseAnalyzer.analyze(chunk(section(-16, "minecraft:stone" to 3000, "minecraft:deepslate" to 100)), AnalysisSettings(maxY = 10)))
    }

    @Test
    fun deepslateHighUpIsNotNatural() {
        val c = chunk(section(-48, "minecraft:deepslate" to 4000), section(32, "minecraft:stone" to 3000, "minecraft:deepslate" to 400))
        val r = assertNotNull(BaseAnalyzer.analyze(c, AnalysisSettings(maxY = 64)))
        assertTrue(r.evidence.any { it.text.startsWith("deepslate above Y 8") }, "${r.evidence}")
    }

    @Test
    fun craftedBlocksAreALikelyBaseAndAMixOfKindsMoreSo() {
        val few = chunk(section(-40, "minecraft:deepslate" to 3000, "minecraft:white_concrete" to 20, "minecraft:glass" to 20))
        val many = chunk(section(-40, "minecraft:deepslate" to 3000, "minecraft:white_concrete" to 20, "minecraft:glass" to 20, "minecraft:smooth_stone" to 9, "minecraft:polished_andesite" to 8, "minecraft:quartz_block" to 6, "minecraft:crafting_table" to 1))
        val fewResult = assertNotNull(BaseAnalyzer.analyze(few))
        assertTrue(fewResult.evidence.any { it.text.contains("crafted blocks, 2 kinds") }, "${fewResult.evidence}")
        assertTrue(fewResult.confidence >= 60, "${fewResult.confidence}")
        val r = assertNotNull(BaseAnalyzer.analyze(many))
        assertTrue(r.evidence.any { it.text.contains("crafted blocks, 6 kinds") }, "${r.evidence}")
        assertTrue(r.confidence > fewResult.confidence)
        assertNull(BaseAnalyzer.analyze(chunk(section(-40, "minecraft:deepslate" to 3000, "minecraft:glass" to 2))), "two panes are not a base")
    }

    @Test
    fun blocksTheWorldGeneratesItselfAreNotCraftedBlocks() {
        for (id in listOf("oak_planks", "white_wool", "spruce_stairs", "oak_fence", "stone_bricks", "iron_bars", "ladder", "bookshelf", "deepslate_tiles", "nether_bricks", "cobblestone"))
            assertFalse(BlockCategories.isCraftedOnly("minecraft:$id"), id)
        for (id in listOf("glass", "red_concrete", "smooth_stone", "polished_diorite", "polished_andesite_stairs", "quartz_pillar", "crafting_table", "brick_slab"))
            assertTrue(BlockCategories.isCraftedOnly("minecraft:$id"), id)
        // A mineshaft's planks and fences, with no cobwebs to give it away, are not a base.
        assertNull(BaseAnalyzer.analyze(chunk(section(-40, "minecraft:deepslate" to 3000, "minecraft:oak_planks" to 120, "minecraft:oak_fence" to 80, "minecraft:white_wool" to 30))))
    }

    @Test
    fun aVillageIsNotABaseEvenWhenItIsAboveGround() {
        val village = chunk(section(64, "minecraft:grass_block" to 200, "minecraft:bell" to 1, "minecraft:glass_pane" to 30, "minecraft:white_concrete" to 12, "minecraft:smoker" to 2, "minecraft:barrel" to 4, "minecraft:blast_furnace" to 1, "minecraft:lectern" to 1))
        assertNull(BaseAnalyzer.analyze(village, AnalysisSettings(maxY = 320)))
    }

    @Test
    fun theNethersOwnBricksAreNotCraftedBlocks() {
        assertNull(BaseAnalyzer.analyze(chunk(section(-40, "minecraft:netherrack" to 3000, "minecraft:nether_bricks" to 200, "minecraft:polished_blackstone_bricks" to 80, "minecraft:purpur_block" to 30))))
    }

    @Test
    fun repeatersAndComparatorsCountAsAPlayerBuild() {
        val c = chunk(section(-40, "minecraft:deepslate" to 3000, "minecraft:repeater" to 6, "minecraft:comparator" to 3, "minecraft:redstone_wire" to 14))
        val r = assertNotNull(BaseAnalyzer.analyze(c))
        assertTrue(r.evidence.any { it.text.endsWith("redstone") }, "${r.evidence}")
        assertTrue(BlockCategories.isPlayerMade("minecraft:repeater"))
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

    // ---- the game's own structures and plants must not look like a base ----

    @Test
    fun anAncientCityIsNotABase() {
        val city = chunk(section(-48, "minecraft:deepslate" to 2800, "minecraft:reinforced_deepslate" to 6, "minecraft:sculk" to 400, "minecraft:chest" to 9, "minecraft:deepslate_tiles" to 300, "minecraft:deepslate_bricks" to 200, "minecraft:soul_lantern" to 12, "minecraft:white_wool" to 30, "minecraft:candle" to 8))
        assertNull(BaseAnalyzer.analyze(city))
    }

    @Test
    fun aBaseBuiltInsideAnAncientCityIsStillFound() {
        val c = chunk(section(-48, "minecraft:deepslate" to 2800, "minecraft:reinforced_deepslate" to 6, "minecraft:chest" to 9, "minecraft:hopper" to 40, "minecraft:comparator" to 8))
        val r = assertNotNull(BaseAnalyzer.analyze(c))
        assertTrue(r.evidence.any { it.text.contains("hoppers") }, "${r.evidence}")
        assertTrue(r.evidence.any { it.text == "looks like a natural ancient city" })
    }

    @Test
    fun anAbandonedMineshaftIsNotABase() {
        val mine = chunk(section(-32, "minecraft:deepslate" to 3000, "minecraft:cobweb" to 40, "minecraft:rail" to 60, "minecraft:oak_fence" to 80, "minecraft:oak_planks" to 120, "minecraft:wall_torch" to 10, "minecraft:torch" to 6, "minecraft:ladder" to 4))
        assertNull(BaseAnalyzer.analyze(mine))
    }

    @Test
    fun aStrongholdIsNotABase() {
        val sh = chunk(section(-16, "minecraft:stone" to 800, "minecraft:stone_bricks" to 300, "minecraft:mossy_stone_bricks" to 40, "minecraft:cracked_stone_bricks" to 40, "minecraft:chiseled_stone_bricks" to 10, "minecraft:iron_bars" to 20, "minecraft:chest" to 6, "minecraft:bookshelf" to 60, "minecraft:ladder" to 10))
        assertNull(BaseAnalyzer.analyze(sh, AnalysisSettings(maxY = 10)))
    }

    @Test
    fun aDungeonAndATrialChamberAreNotBases() {
        assertNull(BaseAnalyzer.analyze(chunk(section(-16, "minecraft:stone" to 900, "minecraft:spawner" to 1, "minecraft:mossy_cobblestone" to 20, "minecraft:cobblestone" to 40, "minecraft:chest" to 2), x = 1)))
        assertNull(BaseAnalyzer.analyze(chunk(section(-40, "minecraft:deepslate" to 2000, "minecraft:trial_spawner" to 2, "minecraft:vault" to 1, "minecraft:tuff_bricks" to 400, "minecraft:copper_bulb" to 10, "minecraft:dispenser" to 12, "minecraft:chest" to 3), x = 2)))
    }

    @Test
    fun scenerySuchAsKelpGrassDripstoneAndGeodesIsNotABase() {
        val scenery = chunk(
            section(-16, "minecraft:deepslate" to 2000, "minecraft:pointed_dripstone" to 300, "minecraft:dripstone_block" to 400, "minecraft:amethyst_block" to 90, "minecraft:budding_amethyst" to 8, "minecraft:amethyst_cluster" to 20, "minecraft:calcite" to 60, "minecraft:smooth_basalt" to 70, "minecraft:glow_lichen" to 40, "minecraft:moss_block" to 200, "minecraft:kelp" to 500, "minecraft:seagrass" to 300, "minecraft:short_grass" to 800, "minecraft:cave_air" to 900),
        )
        assertNull(BaseAnalyzer.analyze(scenery))
    }

    @Test
    fun plantsWithMachinesAroundThemAreAFarm() {
        val farm = chunk(section(-20, "minecraft:deepslate" to 2000, "minecraft:kelp" to 60, "minecraft:kelp_plant" to 200, "minecraft:observer" to 16, "minecraft:piston" to 16, "minecraft:hopper" to 4, "minecraft:dispenser" to 2))
        val r = assertNotNull(BaseAnalyzer.analyze(farm))
        assertEquals(Kind.FARM, r.kind)
        assertTrue(r.evidence.any { it.text.startsWith("farm:") }, "${r.evidence}")
    }

    @Test
    fun theFingerprintIgnoresGrowthButNoticesBuilding() {
        fun fp(vararg counts: Pair<String, Int>) = ChunkSnapshot(0, 0, listOf(SectionSnapshot(-48, counts.toMap()))).activityFingerprint
        val before = fp("minecraft:deepslate" to 3000, "minecraft:kelp" to 100, "minecraft:short_grass" to 50, "minecraft:cave_air" to 400)
        assertEquals(before, fp("minecraft:deepslate" to 3000, "minecraft:kelp" to 130, "minecraft:short_grass" to 90, "minecraft:cave_air" to 410), "plants and a little air changing")
        assertNotEquals(before, fp("minecraft:deepslate" to 3000, "minecraft:kelp" to 100, "minecraft:short_grass" to 50, "minecraft:cave_air" to 400, "minecraft:oak_planks" to 5), "a player placing planks")
        assertEquals(before, fp("minecraft:deepslate" to 3000, "minecraft:kelp" to 100, "minecraft:short_grass" to 50, "minecraft:cave_air" to 700), "air alone (water, mobs, explosions) is not a player")
    }

    @Test
    fun aBigNaturalCaveIsNotABaseButATunnelWithShaftGearIs() {
        val bigCave = chunk(section(-40, "minecraft:deepslate" to 3300, "minecraft:air" to 700), section(-24, "minecraft:deepslate" to 3000, "minecraft:air" to 1000))
        assertNull(BaseAnalyzer.analyze(bigCave, AnalysisSettings(minScore = 5)), "plain air underground is what noise caves are made of")
        val carved = chunk(section(-40, "minecraft:deepslate" to 3300, "minecraft:cave_air" to 700))
        assertNull(BaseAnalyzer.analyze(carved, AnalysisSettings(minScore = 5)))

        val tunnel = chunk(section(-40, "minecraft:deepslate" to 3300, "minecraft:air" to 700, "minecraft:torch" to 8, "minecraft:ladder" to 12))
        val r = assertNotNull(BaseAnalyzer.analyze(tunnel))
        assertEquals(Kind.DUG_SHAFT, r.kind)
        assertTrue(r.evidence.any { it.text.startsWith("dug-out tunnels") }, "${r.evidence}")
    }

    @Test
    fun redstoneRunningAndFarmDropsMakeALikelyBase() {
        val machine = chunk(section(-40, "minecraft:deepslate" to 3300, "minecraft:piston" to 2, "minecraft:redstone_wire" to 3))
        val still = assertNotNull(BaseAnalyzer.analyze(machine, AnalysisSettings(minScore = 5)))
        val running = assertNotNull(BaseAnalyzer.analyze(machine, AnalysisSettings(minScore = 5), redstoneSignals = 2))
        assertTrue(running.confidence > still.confidence)
        assertTrue(running.evidence.any { it.text.startsWith("redstone is running here") }, "${running.evidence}")
        assertNull(BaseAnalyzer.analyze(chunk(section(-40, "minecraft:deepslate" to 3300, "minecraft:air" to 100)), redstoneSignals = 3), "no redstone, no machine to be running")

        val drops = assertNotNull(BaseAnalyzer.analyze(chunk(section(-40, "minecraft:deepslate" to 3300)), farmDrops = 40))
        assertEquals(Kind.FARM, drops.kind)
        assertNull(BaseAnalyzer.analyze(chunk(section(-40, "minecraft:deepslate" to 3300)), farmDrops = 3), "a few loose items are not a farm")
    }

    @Test
    fun onlyAFarmsOwnDropsCountAsFarmDrops() {
        assertTrue(FarmDrops.isFarmDrop("minecraft:bone_meal"))
        assertTrue(FarmDrops.isFarmDrop("minecraft:basalt"))
        assertTrue(FarmDrops.isFarmDrop("minecraft:furnace"))
        assertTrue(FarmDrops.isFarmDrop("minecraft:cobblestone"))
        assertFalse(FarmDrops.isFarmDrop("minecraft:rotten_flesh"))
        assertFalse(FarmDrops.isFarmDrop("minecraft:bone"))
        assertFalse(FarmDrops.isFarmDrop("minecraft:string"))
        assertFalse(FarmDrops.isFarmDrop("minecraft:dirt"))
    }

    @Test
    fun theAirInsideAGeodeOrAGameStructureIsNotADugTunnel() {
        val geode = chunk(section(-40, "minecraft:deepslate" to 3000, "minecraft:air" to 500, "minecraft:budding_amethyst" to 6, "minecraft:amethyst_block" to 80, "minecraft:calcite" to 90))
        assertNull(BaseAnalyzer.analyze(geode, AnalysisSettings(minScore = 10)))
        val city = chunk(section(-48, "minecraft:deepslate" to 3000, "minecraft:air" to 900, "minecraft:reinforced_deepslate" to 4))
        assertNull(BaseAnalyzer.analyze(city, AnalysisSettings(minScore = 10)))
    }

    @Test
    fun sectionsThatAppearOrVanishAreNotComparedAsChanges() {
        val withUnderground = ChunkSnapshot(0, 0, listOf(SectionSnapshot(-48, mapOf("minecraft:hopper" to 10)), SectionSnapshot(64, mapOf("minecraft:oak_planks" to 5))))
        val surfaceOnly = ChunkSnapshot(0, 0, listOf(SectionSnapshot(64, mapOf("minecraft:oak_planks" to 5))))
        assertNotEquals(withUnderground.activityFingerprint, surfaceOnly.activityFingerprint)
        // Over the section both have, nothing changed.
        assertEquals(withUnderground.activityFingerprintOver(setOf(64)), surfaceOnly.activityFingerprintOver(setOf(64)))
    }

    @Test
    fun aSectionThatIsMostlyOpenAirIsSkyNotATunnel() {
        // A flat world's lowest sections hold ground and a lot of open air above it.
        val flat = chunk(section(-64, "minecraft:bedrock" to 256, "minecraft:dirt" to 512, "minecraft:deepslate" to 128, "minecraft:air" to 3200))
        assertNull(BaseAnalyzer.analyze(flat, AnalysisSettings(minScore = 10)))
    }
}
