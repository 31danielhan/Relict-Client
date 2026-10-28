package relict.donut

import kotlin.math.min

enum class Kind(val label: String) {
    STASH("Stash"),
    FARM("Farm"),
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
    /** Worked out earlier, when the chunk's underground was loaded; it is not now (see [ChunkMemory]). */
    val fromMemory: Boolean = false,
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

        // What the whole chunk holds, for spotting the game's own structures and farms (these look above and below the height limit).
        val all = HashMap<String, Int>()
        for (section in chunk.sections) for ((id, n) in section.counts) all.merge(id, n, Int::plus)
        val natural = NaturalStructures.detect(all)
        val explained = natural.flatMap { it.explains }.toSet()

        val totals = HashMap<Category, Int>()
        val sectionsWithEvidence = HashSet<SectionSnapshot>()
        for (section in window) {
            for ((id, n) in section.counts) {
                val category = BlockCategories.of(id)?.takeIf { it !in explained } ?: continue
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
        // Repeaters, comparators, observers, pistons and wire never generate on their own, so any amount of them is a player's work.
        add(min(60, count(Category.REDSTONE) * 2), "${count(Category.REDSTONE)} redstone")
        add(tier(count(Category.CRAFTED_DEEPSLATE)), "crafted deepslate ${count(Category.CRAFTED_DEEPSLATE)}")
        add(tier(count(Category.COBBLESTONE)), "cobblestone ${count(Category.COBBLESTONE)}")
        add(tier(count(Category.OBSIDIAN)), "obsidian ${count(Category.OBSIDIAN)}")
        add(tier(count(Category.BUILDING)), "building blocks ${count(Category.BUILDING)}")
        // Terrain that is wrong for its height. Below Y -8 the world is deepslate with no plain stone in it, and above Y 8
        // there is no deepslate, so either one means something was placed or dug out and replaced. Every section counts here,
        // not only the ones under the height limit.
        var stoneInDeepslate = 0
        var deepslateHigh = 0
        for (section in chunk.sections) {
            if (section.maxY < DEEPSLATE_BELOW) stoneInDeepslate += section.counts[STONE] ?: 0
            if (section.minY > DEEPSLATE_ABOVE) deepslateHigh += section.counts[DEEPSLATE] ?: 0
        }
        if (stoneInDeepslate >= WRONG_TERRAIN_MIN) add(min(40, 6 + stoneInDeepslate / 8), "stone in the deepslate layer $stoneInDeepslate")
        if (deepslateHigh >= WRONG_TERRAIN_MIN) add(min(40, 6 + deepslateHigh / 8), "deepslate above Y $DEEPSLATE_ABOVE $deepslateHigh")

        // A base is a mix of many kinds of made block; a cave has none. (A natural structure has plenty, so it does not count there.)
        val madeKinds = HashSet<String>()
        if (natural.isEmpty()) for (section in window) for ((id, n) in section.counts) if (n > 0 && BlockCategories.isPlayerMade(id)) madeKinds += id
        if (madeKinds.size >= MADE_KINDS_MIN) add(min(30, madeKinds.size * 3), "${madeKinds.size} kinds of player-made blocks")

        // Tunnels and shafts that were dug out. A natural cave is full of cave air; a hole someone mined is plain air. It says nothing
        // about whether the digger is still there (they may have left long ago), only that someone was. The game's own structures
        // and geodes are full of plain air too, so they do not count.
        var dugAir = 0
        for (section in chunk.sections) {
            if (section.maxY >= 0) continue
            val air = section.counts[AIR] ?: 0
            // A section that is mostly air is open sky or a void (flat and empty worlds), not a tunnel through rock.
            if (air * 2 <= section.counts.values.sum()) dugAir += air
        }
        val geode = (all["minecraft:budding_amethyst"] ?: 0) > 0 || ((all["minecraft:amethyst_block"] ?: 0) >= 20 && (all["minecraft:calcite"] ?: 0) > 0)
        if (dugAir >= DUG_AIR_MIN && natural.isEmpty() && !geode) add(min(25, 4 + dugAir / 60), "dug-out tunnels: $dugAir open blocks underground")

        // A farm: plants growing, with the parts that harvest them. Plants alone are scenery.
        var growing = 0
        var farmParts = 0
        for ((id, n) in all) {
            if (Growth.isGrowth(id)) growing += n
            if (Growth.isFarmMachine(id)) farmParts += n
        }
        val farm = growing >= FARM_GROWTH_MIN && farmParts >= FARM_PARTS_MIN
        if (farm) add(min(50, 8 + farmParts * 2), "farm: $growing growing blocks with $farmParts machine parts")
        for (n in natural) evidence += Evidence(0, "looks like a natural ${n.label}")

        val travel = count(Category.TRAVEL)
        add(if (travel >= 3) min(20, travel) else 0, "$travel shaft gear")

        // Blocks changed here lately, which is what a player standing in this chunk looks like from outside. It is only a hint that someone may be there.
        add(min(30, liveSignals * 10), "possible player here ($liveSignals block ${if (liveSignals == 1) "change" else "changes"})")

        val score = evidence.sumOf { it.points }
        if (score < settings.minScore) return null
        val evidenceList = evidence.filter { it.points > 0 || it.text.startsWith("looks like") }

        val kind = when {
            farm && count(Category.CONTAINER) < 4 -> Kind.FARM
            count(Category.CONTAINER) >= 4 || count(Category.HOPPER) >= 6 -> Kind.STASH
            count(Category.MACHINE) + count(Category.REDSTONE) >= 20 -> Kind.MACHINE
            travel >= 3 && count(Category.CONTAINER) == 0 -> Kind.DUG_SHAFT
            dugAir >= DUG_AIR_MIN && natural.isEmpty() && !geode && count(Category.CONTAINER) == 0 -> Kind.DUG_SHAFT
            else -> Kind.UNKNOWN
        }
        val spanY = sectionsWithEvidence.ifEmpty { window.toSet() }
        return ChunkResult(
            chunkX = chunk.chunkX, chunkZ = chunk.chunkZ,
            kind = kind, score = score, confidence = confidence(score),
            evidence = evidenceList.sortedByDescending { it.points },
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

    private const val AIR = "minecraft:air"
    private const val DUG_AIR_MIN = 300
    private const val STONE = "minecraft:stone"
    private const val DEEPSLATE = "minecraft:deepslate"
    private const val DEEPSLATE_BELOW = -8
    private const val DEEPSLATE_ABOVE = 8
    private const val WRONG_TERRAIN_MIN = 16
    private const val MADE_KINDS_MIN = 4
    private const val FARM_GROWTH_MIN = 12
    private const val FARM_PARTS_MIN = 4

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
