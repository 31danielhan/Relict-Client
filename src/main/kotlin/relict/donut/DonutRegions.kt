package relict.donut

import kotlin.math.floor

/**
 * DonutSMP's world is split into a 9 x 9 grid of 50,000-block cells centred on 0,0 (columns A-I run west to east,
 * rows 1-9 north to south). Each cell has a region number. The numbers were read from the server's own region map;
 * [groupNames] says which geographic group (EU Central, NA East...) a region belongs to, where that is known.
 */
object DonutRegions {
    const val CELL_SIZE = 50_000
    const val GRID = 9
    private const val HALF = CELL_SIZE * GRID / 2 // 225,000

    /** Region numbers by [row][column], row 0 = northmost (rows "1"), column 0 = westmost ("A"). */
    private val NUMBERS = arrayOf(
        intArrayOf(82, 100, 101, 102, 103, 104, 105, 106, 91),
        intArrayOf(83, 44, 75, 42, 41, 40, 39, 38, 92),
        intArrayOf(84, 45, 14, 13, 12, 11, 10, 37, 93),
        intArrayOf(85, 46, 74, 3, 2, 1, 25, 36, 94),
        intArrayOf(86, 47, 72, 71, 5, 4, 24, 35, 95),
        intArrayOf(87, 51, 17, 9, 8, 7, 23, 34, 96),
        intArrayOf(88, 54, 18, 61, 62, 21, 22, 33, 97),
        intArrayOf(89, 26, 27, 28, 29, 30, 59, 32, 98),
        intArrayOf(90, 107, 108, 109, 110, 111, 112, 113, 99),
    )

    /** Region number -> group name. Only the regions confirmed so far; the rest show as "unknown" until data is loaded. */
    val groupNames: MutableMap<Int, String> = mutableMapOf(
        22 to "EU Central",
        107 to "EU West",
        5 to "NA East",
    )

    data class Cell(val column: Int, val row: Int) {
        val number: Int get() = NUMBERS[row][column]
        val label: String get() = "${'A' + column}${row + 1}"
        val group: String? get() = groupNames[number]
        val minX: Int get() = -HALF + column * CELL_SIZE
        val maxX: Int get() = minX + CELL_SIZE
        val minZ: Int get() = -HALF + row * CELL_SIZE
        val maxZ: Int get() = minZ + CELL_SIZE
    }

    /** The cell containing a world position, or null outside the 450,000-block map. */
    fun cellAt(x: Double, z: Double): Cell? {
        // floor on the double itself: truncating to an integer first would round -0.1 up to 0 and accept it
        val column = floor((x + HALF) / CELL_SIZE).toInt()
        val row = floor((z + HALF) / CELL_SIZE).toInt()
        if (column !in 0 until GRID || row !in 0 until GRID) return null
        return Cell(column, row)
    }

    fun cell(column: Int, row: Int): Cell = Cell(column, row)

    /** Compact range text like "75k..125k", as the server's own map shows it. */
    fun rangeLabel(min: Int, max: Int): String = "${min / 1000}k..${max / 1000}k"
}
