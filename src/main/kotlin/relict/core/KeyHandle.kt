package relict.core

/**
 * A registered key binding, as the shared code sees it. The real binding object is a Minecraft class whose name
 * depends on the version, so it stays in [native] for the version-specific code to use.
 */
class KeyHandle(
    val native: Any,
    private val pressed: () -> Boolean,
    private val label: () -> String,
) {
    /** True once for every press since the last call. */
    fun consumePress(): Boolean = pressed()

    /** The key's current name, e.g. "Right Shift". */
    val displayName: String get() = label()
}
