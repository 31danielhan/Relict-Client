package relict.render

/** 1.21.10 has no public way to build the through-walls render layers the overlays need, so they are unavailable there. */
object WorldOverlays {
    fun install(): Boolean = false
}
