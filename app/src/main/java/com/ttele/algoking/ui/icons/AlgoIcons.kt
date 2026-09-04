package com.ttele.algoking.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The AlgoKing icon set — DESIGN_SYSTEM.md §6.
 *
 * One style throughout, read off the reference: a 24dp grid, 2.2dp strokes with
 * round caps and joins, and solid fills reserved for brand ornaments (crown, bolt,
 * lock) and for the white glyphs inside algorithm tiles. Tint is applied by the
 * caller, so every path is authored in black.
 */
object AlgoIcons {

    // ── Header & navigation ───────────────────────────────────────────────────

    val ArrowBack: ImageVector by lazy {
        stroked("M19.4 12H5.2", "M11.2 5.8 5.2 12l6 6.2")
    }

    val Search: ImageVector by lazy {
        stroked(circle(11f, 11f, 6.6f), "M15.9 15.9 20.8 20.8")
    }

    val Bookmark: ImageVector by lazy {
        stroked(
            "M6.6 3.9h10.8a1.8 1.8 0 0 1 1.8 1.8v15.4l-7.2-4.4-7.2 4.4V5.7a1.8 1.8 0 0 1 1.8-1.8z",
        )
    }

    val ChevronRight: ImageVector by lazy { stroked("M9.2 5.2 16 12l-6.8 6.8", width = 2.5f) }

    val ChevronLeft: ImageVector by lazy { stroked("M14.8 5.2 8 12l6.8 6.8", width = 2.5f) }

    // ── Bottom navigation ─────────────────────────────────────────────────────

    val HomeFilled: ImageVector by lazy {
        filled(
            "M11.1 3.2a1.4 1.4 0 0 1 1.8 0l8.2 7a1 1 0 0 1 .35.76V19.6a1.9 1.9 0 0 1-1.9 1.9h-4.2" +
                "v-5.1a1.3 1.3 0 0 0-1.3-1.3h-4.1a1.3 1.3 0 0 0-1.3 1.3v5.1H4.45a1.9 1.9 0 0 1-1.9-1.9" +
                "v-8.64a1 1 0 0 1 .35-.76z",
        )
    }

    val HomeOutline: ImageVector by lazy {
        stroked(
            "M3.4 10.6 12 3.4l8.6 7.2v9a1.6 1.6 0 0 1-1.6 1.6h-3.6v-5.6H8.6v5.6H5a1.6 1.6 0 0 1-1.6-1.6z",
        )
    }

    /** Journey — an open book with the section markers on the right leaf. */
    val Book: ImageVector by lazy {
        stroked(
            "M12 6.4C10 4.8 7.3 4.3 4.4 4.8a1 1 0 0 0-.8 1v11.4a1 1 0 0 0 1.2 1c2.5-.5 4.8 0 7.2 1.6",
            "M12 6.4c2-1.6 4.7-2.1 7.6-1.6a1 1 0 0 1 .8 1v11.4a1 1 0 0 1-1.2 1c-2.5-.5-4.8 0-7.2 1.6",
            "M12 6.4v14.8",
            circle(16.6f, 9.2f, 0.85f),
            circle(16.6f, 12f, 0.85f),
            circle(16.6f, 14.8f, 0.85f),
        )
    }

    /** Daily — a calendar with a check. */
    val CalendarCheck: ImageVector by lazy {
        stroked(
            "M4.6 6.4h14.8a1.6 1.6 0 0 1 1.6 1.6v12a1.6 1.6 0 0 1-1.6 1.6H4.6A1.6 1.6 0 0 1 3 20V8a1.6 1.6 0 0 1 1.6-1.6z",
            "M7.8 3.2v4",
            "M16.2 3.2v4",
            "M8.6 13.8l2.6 2.6 4.4-4.8",
        )
    }

    val Person: ImageVector by lazy {
        stroked(
            circle(12f, 8.1f, 3.9f),
            "M4.9 20.6a7.1 7.1 0 0 1 14.2 0",
        )
    }

    // ── Learning controls ─────────────────────────────────────────────────────

    /** SWAP — two crossing paths, the shape of an exchange. */
    val Shuffle: ImageVector by lazy {
        stroked(
            "M3.4 7.4h3.1c3 0 4.5 9.2 7.5 9.2h3.4",
            "M3.4 16.6h3.1c3 0 4.5-9.2 7.5-9.2h3.4",
            "M17 4.2l3.6 3.2L17 10.6",
            "M17 13.4l3.6 3.2L17 19.8",
        )
    }

    /** KEEP — move on, unchanged. */
    val ArrowForward: ImageVector by lazy {
        stroked("M4.6 12h14.8", "M13.4 6l6 6-6 6", width = 2.4f)
    }

    val Pause: ImageVector by lazy {
        filled(
            "M8.2 4.8h2.4a1.4 1.4 0 0 1 1.4 1.4v11.6a1.4 1.4 0 0 1-1.4 1.4H8.2a1.4 1.4 0 0 1-1.4-1.4V6.2a1.4 1.4 0 0 1 1.4-1.4z",
            "M13.4 4.8h2.4a1.4 1.4 0 0 1 1.4 1.4v11.6a1.4 1.4 0 0 1-1.4 1.4h-2.4a1.4 1.4 0 0 1-1.4-1.4V6.2a1.4 1.4 0 0 1 1.4-1.4z",
        )
    }

    val Play: ImageVector by lazy {
        filled("M8.4 5.4 18.6 11.3a.8.8 0 0 1 0 1.4L8.4 18.6a.8.8 0 0 1-1.2-.7V6.1a.8.8 0 0 1 1.2-.7z")
    }

    val Restart: ImageVector by lazy {
        stroked(
            "M20.4 12a8.4 8.4 0 1 1-2.6-6.1",
            "M19.2 2.6v4.4h-4.4",
        )
    }

    val Bulb: ImageVector by lazy {
        stroked(
            "M12 3a6.2 6.2 0 0 1 3.7 11.2c-.6.5-.9 1.1-.9 1.8H9.2c0-.7-.3-1.3-.9-1.8A6.2 6.2 0 0 1 12 3z",
            "M9.6 18.6h4.8",
            "M10.5 21.2h3",
        )
    }

    val Undo: ImageVector by lazy {
        stroked(
            "M8.6 7.4h6.9a5.6 5.6 0 0 1 0 11.2H7.8",
            "M12.4 3.2 8.2 7.4l4.2 4.2",
        )
    }

    val Info: ImageVector by lazy {
        stroked(circle(12f, 7.4f, 0.2f), "M12 10.9v5.9", width = 2.2f)
    }

    val Check: ImageVector by lazy { stroked("M5.2 12.6 9.7 17.2 18.8 7.2", width = 2.8f) }

    // ── Brand ornaments (always solid) ────────────────────────────────────────

    val Crown: ImageVector by lazy {
        filled(
            "M2.6 8.2a1 1 0 0 1 1.6-.8l3.6 2.8 3.3-5.8a.9.9 0 0 1 1.6 0l3.3 5.8 3.6-2.8a1 1 0 0 1 1.6.8" +
                "l-1.5 8.2H4.1z",
            "M4.4 18.4h15.2a1.1 1.1 0 0 1 0 2.2H4.4a1.1 1.1 0 0 1 0-2.2z",
        )
    }

    val Bolt: ImageVector by lazy {
        filled("M14 2.2 5.1 13.4a.7.7 0 0 0 .55 1.15H10l-1.1 7.3a.6.6 0 0 0 1.07.45l8.9-11.4a.7.7 0 0 0-.55-1.13H14.1l1-6.6a.6.6 0 0 0-1.1-.97z")
    }

    val Lock: ImageVector by lazy {
        filled("M6.4 10.2h11.2a1.7 1.7 0 0 1 1.7 1.7v7.2a1.7 1.7 0 0 1-1.7 1.7H6.4a1.7 1.7 0 0 1-1.7-1.7v-7.2a1.7 1.7 0 0 1 1.7-1.7z") +
            stroked("M8.5 10.2V7.7a3.5 3.5 0 0 1 7 0v2.5", width = 2.2f)
    }

    // ── Algorithm tile glyphs (white on the accent gradient) ──────────────────

    /** Binary Search — a heavy magnifier. */
    val TileSearch: ImageVector by lazy {
        stroked(circle(10.4f, 10.4f, 5.8f), "M14.9 14.9 20.4 20.4", width = 3.2f)
    }

    /** Bubble & Selection Sort — six elements waiting to be ordered. */
    val TileDots: ImageVector by lazy {
        filled(
            circle(6.6f, 9.2f, 2.05f),
            circle(12f, 9.2f, 2.05f),
            circle(17.4f, 9.2f, 2.05f),
            circle(6.6f, 14.8f, 2.05f),
            circle(12f, 14.8f, 2.05f),
            circle(17.4f, 14.8f, 2.05f),
        )
    }

    /** Insertion Sort — a growing sorted portion. */
    val TileBars: ImageVector by lazy {
        filled(
            "M5.4 13.4h3.2a1.6 1.6 0 0 1 1.6 1.6v4.6H5.4a1.6 1.6 0 0 1-1.6-1.6V15a1.6 1.6 0 0 1 1.6-1.6z",
            "M10.4 9.6h3.2a1.6 1.6 0 0 1 1.6 1.6v8.4h-4.8z",
            "M15.4 5.4h3.2A1.6 1.6 0 0 1 20.2 7v11a1.6 1.6 0 0 1-1.6 1.6h-3.2z",
        )
    }

    /** Merge Sort — divide, conquer, merge. */
    val TileNodes: ImageVector by lazy {
        stroked(
            "M7.2 7.2 12 12l4.8-4.8",
            "M7.2 16.8 12 12l4.8 4.8",
            width = 2.2f,
        ) + filled(
            circle(5.6f, 5.6f, 2.4f),
            circle(18.4f, 5.6f, 2.4f),
            circle(5.6f, 18.4f, 2.4f),
            circle(18.4f, 18.4f, 2.4f),
            circle(12f, 12f, 2.4f),
        )
    }

    val Sparkle: ImageVector by lazy {
        filled("M12 3.4c.7 3.3 1.6 4.2 4.9 4.9-3.3.7-4.2 1.6-4.9 4.9-.7-3.3-1.6-4.2-4.9-4.9 3.3-.7 4.2-1.6 4.9-4.9z")
    }

    // ── Builders ──────────────────────────────────────────────────────────────

    private const val SIZE = 24f

    /** SVG path data for a full circle, so dots read identically everywhere. */
    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M$cx ${cy - r}a$r $r 0 1 0 0 ${r * 2}a$r $r 0 1 0 0 ${-r * 2}z"

    private fun builder() = ImageVector.Builder(
        defaultWidth = SIZE.dp,
        defaultHeight = SIZE.dp,
        viewportWidth = SIZE,
        viewportHeight = SIZE,
    )

    private fun filled(vararg data: String): ImageVector {
        val b = builder()
        data.forEach { b.addFill(it) }
        return b.build()
    }

    private fun stroked(vararg data: String, width: Float = 2.2f): ImageVector {
        val b = builder()
        data.forEach { b.addStroke(it, width) }
        return b.build()
    }

    private fun ImageVector.Builder.addFill(data: String) = apply {
        addPath(pathData = addPathNodes(data), fill = SolidColor(Color.Black))
    }

    private fun ImageVector.Builder.addStroke(data: String, width: Float) = apply {
        addPath(
            pathData = addPathNodes(data),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    /** Lets an icon mix a solid body with a stroked detail (the lock, the nodes). */
    private operator fun ImageVector.plus(other: ImageVector): ImageVector {
        val b = builder()
        listOf(this, other).forEach { vector ->
            vector.root.forEach { node ->
                if (node is androidx.compose.ui.graphics.vector.VectorPath) {
                    b.addPath(
                        pathData = node.pathData,
                        fill = node.fill,
                        stroke = node.stroke,
                        strokeLineWidth = node.strokeLineWidth,
                        strokeLineCap = node.strokeLineCap,
                        strokeLineJoin = node.strokeLineJoin,
                    )
                }
            }
        }
        return b.build()
    }
}
