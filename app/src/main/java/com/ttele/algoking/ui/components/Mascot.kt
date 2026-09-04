package com.ttele.algoking.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ttele.algoking.ui.theme.AlgoColors

/**
 * The mascot — DESIGN_SYSTEM.md §6.17.
 *
 * A purple blob king with a gold crown. He lives in the copy area of an
 * explanation or result card and is never allowed onto the algorithm canvas.
 * Authored on a 100 x 100 grid and scaled, so he is identical at 88dp and 120dp.
 */
@Composable
fun MascotKing(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 100f
        fun x(v: Float) = v * u
        fun y(v: Float) = v * u

        val body = Color(0xFF9957F8)
        val bodyDeep = Color(0xFF7B3AF2)
        val crownGold = AlgoColors.gold
        val crownLight = Color(0xFFFED45C)
        val mouth = Color(0xFFD6246B)

        // Sparkles — three violet four-point stars around him.
        listOf(
            Triple(88f, 22f, 6f),
            Triple(14f, 34f, 4.5f),
            Triple(90f, 46f, 4f),
        ).forEach { (cx, cy, r) ->
            drawSparkle(Offset(x(cx), y(cy)), r * u, AlgoColors.primaryLight.copy(alpha = 0.75f))
        }

        // Sceptre — a gold rod with a ball, held on his right.
        drawLine(
            color = crownGold,
            start = Offset(x(17f), y(44f)),
            end = Offset(x(21f), y(88f)),
            strokeWidth = 3.2f * u,
            cap = StrokeCap.Round,
        )
        drawCircle(crownGold, radius = 5f * u, center = Offset(x(16.5f), y(41f)))

        // Arms.
        drawCircle(bodyDeep, radius = 8f * u, center = Offset(x(24f), y(66f)))
        drawCircle(bodyDeep, radius = 8f * u, center = Offset(x(76f), y(60f)))

        // Body — a soft blob.
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(body, bodyDeep)),
            topLeft = Offset(x(27f), y(38f)),
            size = Size(x(46f), y(52f)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f * u, 22f * u),
        )

        // Highlight along the top-left of the body.
        drawRoundRect(
            color = Color.White.copy(alpha = 0.16f),
            topLeft = Offset(x(31f), y(42f)),
            size = Size(x(18f), y(20f)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f * u, 10f * u),
        )

        // Crown.
        val crown = Path().apply {
            moveTo(x(28f), y(38f))
            lineTo(x(25f), y(16f))
            lineTo(x(36f), y(26f))
            lineTo(x(50f), y(9f))
            lineTo(x(64f), y(26f))
            lineTo(x(75f), y(16f))
            lineTo(x(72f), y(38f))
            close()
        }
        drawPath(crown, Brush.verticalGradient(listOf(crownLight, crownGold)))
        drawCircle(Color.White.copy(alpha = 0.55f), radius = 2.6f * u, center = Offset(x(50f), y(28f)))

        // Eyes — the left open, the right winking.
        drawCircle(Color.White, radius = 6.2f * u, center = Offset(x(41f), y(58f)))
        drawCircle(AlgoColors.textPrimary, radius = 3.4f * u, center = Offset(x(41.6f), y(58.6f)))
        drawArc(
            color = AlgoColors.textPrimary,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(x(54f), y(53f)),
            size = Size(x(12f), y(10f)),
            style = Stroke(width = 2.6f * u, cap = StrokeCap.Round),
        )

        // Smile.
        val smile = Path().apply {
            moveTo(x(41f), y(70f))
            quadraticTo(x(50f), y(82f), x(59f), y(70f))
            close()
        }
        drawPath(smile, mouth)

        // Blush.
        drawCircle(Color(0xFFF06BA8).copy(alpha = 0.45f), radius = 4f * u, center = Offset(x(33f), y(70f)))
        drawCircle(Color(0xFFF06BA8).copy(alpha = 0.45f), radius = 4f * u, center = Offset(x(67f), y(68f)))
    }
}

private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + radius * 0.22f, center.y - radius * 0.22f, center.x + radius, center.y)
        quadraticTo(center.x + radius * 0.22f, center.y + radius * 0.22f, center.x, center.y + radius)
        quadraticTo(center.x - radius * 0.22f, center.y + radius * 0.22f, center.x - radius, center.y)
        quadraticTo(center.x - radius * 0.22f, center.y - radius * 0.22f, center.x, center.y - radius)
        close()
    }
    drawPath(path, color)
}
