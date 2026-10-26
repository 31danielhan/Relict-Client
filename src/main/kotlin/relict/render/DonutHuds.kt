package relict.render

import relict.donut.DonutRegions
import relict.donut.FinderEntry
import relict.game.PlayerState

/** The screen layouts of the Donut HUDs. They only talk to [HudCanvas], so they look the same on every version. */
object DonutHuds {
    private const val PAD = 6
    private const val MARGIN = 6

    private fun panel(c: HudCanvas, x: Int, y: Int, w: Int, h: Int) {
        c.fill(x, y, w, h, Colors.PANEL)
        c.fill(x, y, w, 1, Colors.PANEL_EDGE)
        c.fill(x, y + h - 1, w, 1, Colors.PANEL_EDGE)
        c.fill(x, y, 1, h, Colors.PANEL_EDGE)
        c.fill(x + w - 1, y, 1, h, Colors.PANEL_EDGE)
    }

    /** The server's region map, top right, with the region you are in highlighted. */
    fun regions(c: HudCanvas, player: PlayerState?) {
        val cellW = 17
        val cellH = 10
        val labelW = 8
        val gridW = DonutRegions.GRID * cellW
        val w = PAD * 2 + labelW + gridW
        val x0 = c.width - w - MARGIN
        val y0 = MARGIN
        val cell = player?.let { DonutRegions.cellAt(it.x, it.z) }
        val h = PAD * 2 + 12 + DonutRegions.GRID * cellH + 10 + 3 * 9 + 22
        panel(c, x0, y0, w, h)

        c.text("DONUT REGIONS", x0 + PAD, y0 + PAD, Colors.MUTED, 0.8f)
        val title = cell?.number?.toString() ?: "-"
        c.text(title, x0 + w - PAD - c.textWidth(title, 1f), y0 + PAD - 1, cell?.let { Colors.group(it.group) } ?: Colors.MUTED, 1f)

        val gx = x0 + PAD + labelW
        val gy = y0 + PAD + 12
        for (row in 0 until DonutRegions.GRID) {
            c.text("${row + 1}", x0 + PAD, gy + row * cellH + 2, Colors.MUTED, 0.6f)
            for (col in 0 until DonutRegions.GRID) {
                val r = DonutRegions.cell(col, row)
                val cx = gx + col * cellW
                val cy = gy + row * cellH
                c.fill(cx + 1, cy + 1, cellW - 2, cellH - 2, Colors.group(r.group))
                val label = r.number.toString()
                c.text(label, cx + (cellW - c.textWidth(label, 0.6f)) / 2, cy + 3, Colors.WHITE, 0.6f)
                if (cell != null && cell.column == col && cell.row == row) {
                    c.fill(cx, cy, cellW, 1, Colors.WHITE); c.fill(cx, cy + cellH - 1, cellW, 1, Colors.WHITE)
                    c.fill(cx, cy, 1, cellH, Colors.WHITE); c.fill(cx + cellW - 1, cy, 1, cellH, Colors.WHITE)
                }
            }
        }
        for (col in 0 until DonutRegions.GRID) c.text("${'A' + col}", gx + col * cellW + cellW / 2 - 2, gy + DonutRegions.GRID * cellH + 1, Colors.MUTED, 0.6f)

        var ly = gy + DonutRegions.GRID * cellH + 11
        val groups = listOf("EU Central", "NA West", "EU West", "Asia", "NA East", "Oceania")
        groups.chunked(2).forEach { pair ->
            pair.forEachIndexed { i, g ->
                val lx = gx + i * 70
                c.fill(lx, ly + 1, 5, 5, Colors.group(g))
                c.text(g, lx + 8, ly, Colors.MUTED, 0.65f)
            }
            ly += 9
        }
        if (player != null) {
            c.text("X: ${player.blockX}  Z: ${player.blockZ}", x0 + PAD, ly + 3, Colors.WHITE, 0.75f)
            cell?.group?.let { g -> c.text(g, x0 + w - PAD - c.textWidth(g, 0.75f), ly + 3, Colors.group(g), 0.75f) }
            if (cell != null) {
                c.text("${DonutRegions.rangeLabel(cell.minX, cell.maxX)}  ${DonutRegions.rangeLabel(cell.minZ, cell.maxZ)}", x0 + PAD, ly + 13, Colors.MUTED, 0.65f)
            }
        }
    }

    /** The nearest flagged chunks, top left. */
    fun baseFinder(c: HudCanvas, entries: List<FinderEntry>, rows: Int = 5) {
        val w = 150
        val shown = entries.take(rows)
        val rowH = 17
        val h = PAD * 2 + 12 + shown.size * rowH + (if (entries.size > rows) 10 else 0) + (if (shown.isEmpty()) 10 else 0)
        val x0 = MARGIN
        val y0 = MARGIN
        panel(c, x0, y0, w, h)

        c.text("BaseFinder", x0 + PAD, y0 + PAD, Colors.ACCENT, 0.85f)
        val count = "${entries.size} nearby"
        c.text(count, x0 + w - PAD - c.textWidth(count, 0.7f), y0 + PAD + 1, Colors.MUTED, 0.7f)

        if (shown.isEmpty()) c.text("Nothing flagged yet", x0 + PAD, y0 + PAD + 14, Colors.MUTED, 0.7f)
        shown.forEachIndexed { i, e ->
            val y = y0 + PAD + 12 + i * rowH
            val r = e.result
            c.text("${i + 1}", x0 + PAD, y + 1, Colors.MUTED, 0.7f)
            c.text(r.kind.label, x0 + PAD + 9, y, Colors.WHITE, 0.8f)
            val conf = "${r.confidence}%"
            c.text(conf, x0 + w - PAD - c.textWidth(conf, 0.7f), y + 1, Colors.ACCENT, 0.7f)
            c.text("${e.direction} ${e.distance}m / Y ${r.minY}..${r.maxY} / ${r.evidenceSections}/${r.totalSections} sections", x0 + PAD + 9, y + 8, Colors.MUTED, 0.55f)
            val barW = w - PAD * 2
            c.fill(x0 + PAD, y + 15, barW, 1, Colors.PANEL_EDGE)
            c.fill(x0 + PAD, y + 15, barW * r.confidence / 100, 1, Colors.ACCENT)
        }
        if (entries.size > rows) c.text("+${entries.size - rows} more nearby", x0 + PAD, y0 + h - 11, Colors.MUTED, 0.65f)
    }
}
