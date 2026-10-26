package relict.donut

import kotlin.math.min

enum class Kind(val label: String) {
    STASH("Stash"),
    MACHINE("Machine"),
    DUG_SHAFT("Dug Shaft"),
    UNKNOWN("Unknown"),
    EXCAVATED("Excavated"),
}

data class Evidence(val points: Int, val text: String)

/** The verdict for one chunk. */
data class ChunkResult(
    val chunkX: Int,
    val chunkZ: Int,
    val kind: Kind,
    val score: Int,
    /** 0-99. Rises with the score, never claims certainty. */
    val confidence: Int,
    val evidence: List<Evidence>,
    val evidenceSections: Int,
    val totalSections: Int,
    val minY: Int,
    val maxY: Int,
)

class AnalysisSettings(
    /** Only blocks below this height count, so surface trees and houses don't flag a chunk. */
    val maxY: Int = 0,
    /** Chunks scoring below this aren't reported. */
    val minScore: Int = 20,
)

/**
 * Turns block counts into a score. It is a plain additive model so every point can be explained:
 * each kind of player-made block adds points in tiers, and the evidence list says which.
 */
object BaseAnalyzer {
    fun analyze(chunk: ChunkSnapshot, settings: AnalysisSettings = AnalysisSettings(), liveSignals: Int = 0): ChunkResult? {
        val window = chunk.sections.filter { it.minY < settings.maxY }
        if (window.isEmpty()) return null

        val totals = HashMap<Category, Int>()
        val sectionsWithEvidence = HashSet<SectionSnapshot>()
        for (section in window) {
            for ((id, n) in section.counts) {
                val category = BlockCategories.of(id) ?: continue
                totals.merge(category, n, Int::plus)
                // A single torch is just a lit cave; real evidence needs more than that in a section.
                if (category != Category.TRAVEL || n >= 2) sectionsWithEvidence += section
            }
        }
        fun count(c: Category) = totals[c] ?: 0

        val evidence = ArrayList<Evidence>()
        fun add(points: Int, text: String) { if (points > 0) evidence += Evidence(points, text) }

        add(min(60, count(Category.CONTAINER) * 4), "${count(Category.CONTAINER)} containers")
        add(min(90, count(Category.HOPPER) * 3), "${count(Category.HOPPER)} hoppers")
        add(min(40, count(Category.MACHINE) * 2), "${count(Category.MACHINE)} machines")
        add(min(40, count(Category.REDSTONE)), "${count(Category.REDSTONE)} redstone")
        add(tier(count(Category.CRAFTED_DEEPSLATE)), "crafted deepslate ${count(Category.CRAFTED_DEEPSLATE)}")
        add(tier(count(Category.COBBLESTONE)), "cobblestone ${count(Category.COBBLESTONE)}")
        add(tier(count(Category.OBSIDIAN)), "obsidian ${count(Category.OBSIDIAN)}")
        add(tier(count(Category.BUILDING)), "building blocks ${count(Category.BUILDING)}")
        val travel = count(Category.TRAVEL)
        add(if (travel >= 3) min(20, travel) else 0, "$travel shaft gear")

        add(min(30, liveSignals * 10), "live player signal $liveSignals")

        val score = evidence.sumOf { it.points }
        if (score < settings.minScore) return null

        val kind = when {
            count(Category.CONTAINER) >= 4 || count(Category.HOPPER) >= 6 -> Kind.STASH
            count(Category.MACHINE) + count(Category.REDSTONE) >= 20 -> Kind.MACHINE
            travel >= 3 && count(Category.CONTAINER) == 0 -> Kind.DUG_SHAFT
            else -> Kind.UNKNOWN
        }
        val spanY = sectionsWithEvidence.ifEmpty { window.toSet() }
        return ChunkResult(
            chunkX = chunk.chunkX, chunkZ = chunk.chunkZ,
            kind = kind, score = score, confidence = confidence(score),
            evidence = evidence.sortedByDescending { it.points },
            evidenceSections = sectionsWithEvidence.size, totalSections = window.size,
            minY = spanY.minOf { it.minY }, maxY = spanY.maxOf { it.maxY },
        )
    }

    /** A chunk next to a flagged chunk is likelier to belong to the same base. */
    fun withNeighbourBonus(results: List<ChunkResult>): List<ChunkResult> {
        val byChunk = results.associateBy { it.chunkX to it.chunkZ }
        return results.map { r ->
            val neighbours = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).count { (dx, dz) -> byChunk.containsKey((r.chunkX + dx) to (r.chunkZ + dz)) }
            if (neighbours == 0) r else {
                val bonus = min(18, neighbours * 6)
                val score = r.score + bonus
                r.copy(score = score, confidence = confidence(score), evidence = (r.evidence + Evidence(bonus, "$neighbours chunk(s) next door are flagged too")).sortedByDescending { it.points })
            }
        }
    }

    fun confidence(score: Int): Int = min(99, (100.0 * score / (score + 15)).toInt())

    /** 10+ -> 3, 30+ -> 6, 100+ -> 8, 300+ -> 10: big piles matter more, with diminishing returns. */
    private fun tier(count: Int): Int = when {
        count >= 300 -> 10
        count >= 100 -> 8
        count >= 30 -> 6
        count >= 10 -> 3
        else -> 0
    }
}
