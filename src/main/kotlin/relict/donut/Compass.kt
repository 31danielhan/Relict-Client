package relict.donut

import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Directions as the HUD shows them: north is toward -Z, east toward +X. */
object Compass {
    private val NAMES = listOf("E", "SE", "S", "SW", "W", "NW", "N", "NE")

    fun direction(dx: Double, dz: Double): String {
        if (dx == 0.0 && dz == 0.0) return "here"
        val degrees = Math.toDegrees(atan2(dz, dx)) // 0 = east, 90 = south
        val index = ((degrees + 360) % 360 / 45).roundToInt() % 8
        return NAMES[index]
    }

    fun distance(dx: Double, dz: Double): Int = sqrt(dx * dx + dz * dz).roundToInt()
}
