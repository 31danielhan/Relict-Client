package relict.core

import com.mojang.blaze3d.platform.InputConstants

/** The key codes the mod needs. Shared code uses these so it never names GLFW (26.3 no longer has it). */
object KeyCodes {
    val UNBOUND = InputConstants.UNKNOWN.value
    val RIGHT_SHIFT = InputConstants.KEY_RSHIFT
    val ESCAPE = InputConstants.KEY_ESCAPE
    val B = InputConstants.KEY_B
}
