package com.ismoilovdev.chipid.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The design's 24x24 stroke icons, rebuilt from their SVG paths. */
object Ic {
    private fun icon(name: String, width: Float, vararg d: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            d.forEach {
                addPath(
                    pathData = addPathNodes(it), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 -$r ${r}h-${w - 2 * r}a$r $r 0 0 1 -$r -${r}v-${h - 2 * r}a$r $r 0 0 1 $r -${r}z"

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 -${2 * r} 0"

    val shield = icon("shield", 2f, "M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6z", "M9 12l2 2 4-4")
    val settings = icon("settings", 2f, circle(12f, 12f, 3f), "M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1")
    val passport = icon("passport", 1.8f, rect(5f, 3f, 14f, 18f, 2f), circle(12f, 10f, 3f), "M9 16h6")
    val idCard = icon("id", 1.8f, rect(2f, 5f, 20f, 14f, 2f), circle(8f, 11f, 2f), "M5.5 16c.5-1.5 1.5-2 2.5-2s2 .5 2.5 2M14 10h5M14 13h3")
    val check = icon("check", 3f, "M5 12l5 5 9-10")
    val checkThin = icon("check-thin", 2.2f, "M5 12l5 5 9-10")
    val lock = icon("lock", 2f, rect(5f, 11f, 14f, 10f, 2f), "M8 11V8a4 4 0 0 1 8 0v3")
    val scan = icon("scan", 2f, "M3 8V5a2 2 0 0 1 2-2h3M16 3h3a2 2 0 0 1 2 2v3M21 16v3a2 2 0 0 1-2 2h-3M8 21H5a2 2 0 0 1-2-2v-3", "M7 12h10")
    val back = icon("back", 2f, "M15 6l-6 6 6 6")
    val flash = icon("flash", 2f, "M13 2L4 14h7l-1 8 9-12h-7z")
    val pencil = icon("pencil", 2f, "M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z")
    val camera = icon("camera", 2f, "M4 8h3l2-3h6l2 3h3v11H4z", circle(12f, 13f, 3.5f))
    val close = icon("close", 2.2f, "M6 6l12 12M18 6L6 18")
    val nfc = icon("nfc", 1.8f, "M6 8.5a5 5 0 0 1 0 7", "M9.5 6a9 9 0 0 1 0 12", "M13 3.5a13 13 0 0 1 0 17")
    val export = icon("export", 2f, "M12 15V3M7 8l5-5 5 5", "M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6")
    val user = icon("user", 1.6f, circle(12f, 9f, 4f), "M4 21c1-4 4.5-6 8-6s7 2 8 6")
    val chevron = icon("chevron", 2f, "M9 6l6 6-6 6")
    val alert = icon("alert", 2f, "M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z", "M12 9v4M12 17h.01")
    val info = icon("info", 2f, circle(12f, 12f, 9f), "M12 16v-4M12 8h.01")
    val eye = icon("eye", 2f, "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z", circle(12f, 12f, 3f))
    val trash = icon("trash", 2f, "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3")
}
