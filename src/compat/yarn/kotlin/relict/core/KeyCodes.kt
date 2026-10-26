package relict.core

import org.lwjgl.glfw.GLFW

/** The key codes the mod needs. Shared code uses these so it never names GLFW (26.3 no longer has it). */
object KeyCodes {
    val UNBOUND = GLFW.GLFW_KEY_UNKNOWN
    val RIGHT_SHIFT = GLFW.GLFW_KEY_RIGHT_SHIFT
    val ESCAPE = GLFW.GLFW_KEY_ESCAPE
    val B = GLFW.GLFW_KEY_B
}
