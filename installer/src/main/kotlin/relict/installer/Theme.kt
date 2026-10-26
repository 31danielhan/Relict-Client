package relict.installer

import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JPanel

/** The installer's palette: near-black background, slightly lighter cards, one blue accent. */
object Theme {
    val BACKGROUND = Color(0x0d, 0x11, 0x17)
    val CARD = Color(0x15, 0x1a, 0x23)
    val CARD_SELECTED = Color(0x16, 0x20, 0x3a)
    val BORDER = Color(0x24, 0x2b, 0x38)
    val TEXT = Color(0xe6, 0xed, 0xf3)
    val MUTED = Color(0x8b, 0x94, 0xa5)
    val ACCENT = Color(0x4c, 0x7d, 0xff)
    val GOOD = Color(0x3e, 0xcf, 0x8e)
    val WARN = Color(0xf5, 0xb7, 0x3d)

    /** FlatLaf defaults that give the whole window the dark look and rounded controls. */
    val defaults = mapOf(
        "@background" to "#0d1117",
        "@foreground" to "#e6edf3",
        "@accentColor" to "#4c7dff",
        "Component.arc" to "12",
        "Button.arc" to "12",
        "TextComponent.arc" to "10",
        "ProgressBar.arc" to "999",
        "ScrollBar.thumbArc" to "999",
        "ScrollBar.width" to "10",
        "TextField.background" to "#0d1117",
        "ComboBox.background" to "#0d1117",
        "TextArea.background" to "#0d1117",
        "Component.borderColor" to "#242b38",
        "Component.focusWidth" to "1",
    )
}

/** A panel with a rounded, filled and outlined background: the card everything sits on. */
open class RoundedPanel(var fill: Color = Theme.CARD, var outline: Color? = Theme.BORDER, private val arc: Int = 18) : JPanel() {
    init {
        isOpaque = false
    }

    /** Cards keep their natural height when a box layout has spare room (unless a size was set on purpose). */
    override fun getMaximumSize(): Dimension = if (isMaximumSizeSet) super.getMaximumSize() else Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = fill
        g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
        outline?.let {
            g2.color = it
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
        }
        g2.dispose()
        super.paintComponent(g)
    }
}
