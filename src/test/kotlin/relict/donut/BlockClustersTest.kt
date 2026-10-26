package relict.donut

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockClustersTest {
    private fun block(id: String, x: Int, y: Int, z: Int) = BlockRef(id, BlockPos3(x, y, z))

    @Test
    fun nearbyBlocksFormOneClusterAndFarOnesDont() {
        val blocks = listOf(
            block("minecraft:hopper", 0, -50, 0), block("minecraft:hopper", 3, -50, 2), block("minecraft:chest", 5, -49, 4),
            block("minecraft:chest", 100, -50, 100), block("minecraft:hopper", 103, -50, 100),
        )
        val clusters = BlockClusters.build(blocks)
        assertEquals(2, clusters.size)
        val near = clusters.first { it.minX == 0 }
        assertEquals(listOf(0, -50, 0, 5, -49, 4), listOf(near.minX, near.minY, near.minZ, near.maxX, near.maxY, near.maxZ))
        assertEquals(2, near.counts[Category.HOPPER])
        assertEquals(1, near.counts[Category.CONTAINER])
    }

    @Test
    fun aChainOfBlocksLinksIntoOneCluster() {
        val chain = (0..10).map { block("minecraft:hopper", it * 5, -40, 0) }
        assertEquals(1, BlockClusters.build(chain, gap = 6).size)
    }

    @Test
    fun clustersAreSortedByScoreAndDescribedLikeTheTooltip() {
        val big = (0..9).map { block("minecraft:hopper", it, -40, 0) } + (0..4).map { block("minecraft:chest", it, -41, 0) }
        val small = listOf(block("minecraft:chest", 500, -40, 500))
        val clusters = BlockClusters.build(big + small)
        assertEquals(2, clusters.size)
        assertTrue(clusters[0].score > clusters[1].score)
        assertEquals(Kind.STASH, clusters[0].kind)
        assertEquals("10 hoppers, 5 containers", clusters[0].summary())
    }

    @Test
    fun negativeCoordinatesGroupCorrectly() {
        val blocks = listOf(block("minecraft:hopper", -1, -50, -1), block("minecraft:hopper", 1, -50, 1), block("minecraft:hopper", -7, -50, -7))
        assertEquals(1, BlockClusters.build(blocks, gap = 8).size)
    }

    @Test
    fun noBlocksNoClusters() {
        assertTrue(BlockClusters.build(emptyList()).isEmpty())
    }
}
