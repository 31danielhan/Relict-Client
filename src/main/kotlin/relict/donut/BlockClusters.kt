package relict.donut

import kotlin.math.abs

/** A group of nearby storage/machine blocks, drawn as one box. */
data class Cluster(
    val minX: Int, val minY: Int, val minZ: Int,
    val maxX: Int, val maxY: Int, val maxZ: Int,
    val counts: Map<Category, Int>,
    val score: Int,
) {
    val kind: Kind get() = when {
        (counts[Category.CONTAINER] ?: 0) >= 4 || (counts[Category.HOPPER] ?: 0) >= 6 -> Kind.STASH
        (counts[Category.MACHINE] ?: 0) + (counts[Category.REDSTONE] ?: 0) >= 20 -> Kind.MACHINE
        else -> Kind.UNKNOWN
    }

    /** "172 machines, 146 redstone", as in the Base ESP tooltip. */
    fun summary(): String = counts.entries.sortedByDescending { it.value }.take(3).joinToString(", ") { "${it.value} ${it.key.label.substringBefore(' ')}" }
}

/** Groups blocks that are within [gap] blocks of each other (in every direction) into clusters. */
object BlockClusters {
    fun build(blocks: List<BlockRef>, gap: Int = 6): List<Cluster> {
        if (blocks.isEmpty()) return emptyList()
        val parent = IntArray(blocks.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }
            return x
        }

        // Bucket blocks into gap-sized cells, so only neighbouring cells need comparing.
        val cells = HashMap<Triple<Int, Int, Int>, MutableList<Int>>()
        blocks.forEachIndexed { i, b -> cells.getOrPut(Triple(Math.floorDiv(b.pos.x, gap), Math.floorDiv(b.pos.y, gap), Math.floorDiv(b.pos.z, gap))) { ArrayList() } += i }
        for ((cell, members) in cells) {
            for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                val other = cells[Triple(cell.first + dx, cell.second + dy, cell.third + dz)] ?: continue
                for (a in members) for (b in other) {
                    if (a < b && near(blocks[a].pos, blocks[b].pos, gap)) parent[find(a)] = find(b)
                }
            }
        }

        return blocks.indices.groupBy { find(it) }.values.map { members ->
            val group = members.map { blocks[it] }
            val counts = HashMap<Category, Int>()
            for (b in group) BlockCategories.of(b.id)?.let { counts.merge(it, 1, Int::plus) }
            Cluster(
                group.minOf { it.pos.x }, group.minOf { it.pos.y }, group.minOf { it.pos.z },
                group.maxOf { it.pos.x }, group.maxOf { it.pos.y }, group.maxOf { it.pos.z },
                counts, score(counts),
            )
        }.sortedByDescending { it.score }
    }

    private fun near(a: BlockPos3, b: BlockPos3, gap: Int) = abs(a.x - b.x) <= gap && abs(a.y - b.y) <= gap && abs(a.z - b.z) <= gap

    /** The same weighting the chunk analysis uses, so a cluster's number means the same thing. */
    private fun score(counts: Map<Category, Int>): Int =
        (counts[Category.CONTAINER] ?: 0) * 4 + (counts[Category.HOPPER] ?: 0) * 3 +
            (counts[Category.MACHINE] ?: 0) * 2 + (counts[Category.REDSTONE] ?: 0)
}
