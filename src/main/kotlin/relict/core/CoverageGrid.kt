package relict.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Which chunks have been searched, for one dimension. Chunks are stored as bits in 32x32-chunk regions,
 * so a million searched chunks take about 125 KB.
 */
class CoverageGrid {
    private val regions = HashMap<Long, LongArray>()

    /** Number of searched chunks. */
    var count = 0
        private set

    val isEmpty: Boolean get() = count == 0

    fun mark(cx: Int, cz: Int): Boolean {
        val bits = regions.getOrPut(key(cx shr REGION_SHIFT, cz shr REGION_SHIFT)) { LongArray(WORDS) }
        val index = indexInRegion(cx, cz)
        val word = index ushr 6
        val mask = 1L shl (index and 63)
        if (bits[word] and mask != 0L) return false
        bits[word] = bits[word] or mask
        count++
        return true
    }

    fun isSearched(cx: Int, cz: Int): Boolean {
        val bits = regions[key(cx shr REGION_SHIFT, cz shr REGION_SHIFT)] ?: return false
        val index = indexInRegion(cx, cz)
        return bits[index ushr 6] and (1L shl (index and 63)) != 0L
    }

    /**
     * Marks every chunk within [radius] of (cx, cz) for which [isLoaded] is true.
     * Returns how many were newly marked.
     */
    fun markDisc(cx: Int, cz: Int, radius: Int, isLoaded: (Int, Int) -> Boolean = { _, _ -> true }): Int {
        var added = 0
        val r2 = radius * radius
        for (dz in -radius..radius) for (dx in -radius..radius) {
            if (dx * dx + dz * dz > r2) continue
            if (isLoaded(cx + dx, cz + dz) && mark(cx + dx, cz + dz)) added++
        }
        return added
    }

    fun clear() {
        regions.clear()
        count = 0
    }

    /**
     * A coarse picture of the window starting at chunk ([x0], [z0]), [w] x [h] cells, each cell covering
     * [step] x [step] chunks. Each byte is the searched fraction of its cell (0 = none, 255 = all).
     */
    fun raster(x0: Int, z0: Int, w: Int, h: Int, step: Int): ByteArray {
        require(w > 0 && h > 0 && step > 0) { "Window must be positive" }
        val counts = IntArray(w * h)
        val xEnd = x0.toLong() + w.toLong() * step
        val zEnd = z0.toLong() + h.toLong() * step
        for ((k, bits) in regions) {
            val baseX = (k shr 32).toInt() shl REGION_SHIFT
            val baseZ = k.toInt() shl REGION_SHIFT
            if (baseX + REGION_SIZE <= x0 || baseX >= xEnd || baseZ + REGION_SIZE <= z0 || baseZ >= zEnd) continue
            for (word in 0 until WORDS) {
                var bitsLeft = bits[word]
                while (bitsLeft != 0L) {
                    val bit = java.lang.Long.numberOfTrailingZeros(bitsLeft)
                    bitsLeft = bitsLeft and (bitsLeft - 1)
                    val index = word * 64 + bit
                    val cx = baseX + (index and (REGION_SIZE - 1))
                    val cz = baseZ + (index ushr REGION_SHIFT)
                    if (cx < x0 || cx >= xEnd || cz < z0 || cz >= zEnd) continue
                    counts[((cz - z0) / step) * w + (cx - x0) / step]++
                }
            }
        }
        val full = step * step
        return ByteArray(counts.size) { i ->
            val c = counts[i]
            if (c == 0) 0 else maxOf(1, c * 255 / full).toByte()
        }
    }

    fun toBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(MAGIC)
            d.writeInt(VERSION)
            d.writeInt(regions.size)
            for ((k, bits) in regions) {
                d.writeInt((k shr 32).toInt())
                d.writeInt(k.toInt())
                bits.forEach(d::writeLong)
            }
        }
        return out.toByteArray()
    }

    companion object {
        private const val MAGIC = 0x56434F56 // "VCOV"
        private const val VERSION = 1
        private const val REGION_SHIFT = 5
        private const val REGION_SIZE = 1 shl REGION_SHIFT
        private const val WORDS = REGION_SIZE * REGION_SIZE / 64

        private fun key(rx: Int, rz: Int): Long = (rx.toLong() shl 32) or (rz.toLong() and 0xFFFFFFFFL)

        private fun indexInRegion(cx: Int, cz: Int) = ((cz and (REGION_SIZE - 1)) shl REGION_SHIFT) or (cx and (REGION_SIZE - 1))

        /** Throws [IllegalArgumentException] if [bytes] is not a valid coverage file. */
        fun fromBytes(bytes: ByteArray): CoverageGrid {
            val grid = CoverageGrid()
            try {
                DataInputStream(ByteArrayInputStream(bytes)).use { d ->
                    require(d.readInt() == MAGIC) { "Not a coverage file" }
                    require(d.readInt() == VERSION) { "Unsupported coverage version" }
                    val n = d.readInt()
                    require(n in 0..10_000_000) { "Corrupt region count" }
                    repeat(n) {
                        val rx = d.readInt()
                        val rz = d.readInt()
                        val bits = LongArray(WORDS) { d.readLong() }
                        grid.regions[key(rx, rz)] = bits
                        grid.count += bits.sumOf { java.lang.Long.bitCount(it) }
                    }
                }
            } catch (e: java.io.IOException) {
                throw IllegalArgumentException("Truncated coverage file", e)
            }
            return grid
        }
    }
}
