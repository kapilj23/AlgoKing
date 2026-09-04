package com.ttele.algoking.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevation tokens — DESIGN_SYSTEM.md §5.
 *
 * The reference's shadows are soft, low-opacity and *violet-tinted* — never grey and
 * never Material's default black. A filled button's shadow additionally takes the
 * button's own hue.
 */
data class ShadowSpec(val elevation: Dp, val color: Color)

object Elevation {
    /** Cards, chips, header icon buttons, the bottom nav. */
    val card = ShadowSpec(6.dp, AlgoColors.primary.copy(alpha = 0.34f))

    /** Secondary buttons, the bar that is currently comparing. */
    val raised = ShadowSpec(9.dp, AlgoColors.primary.copy(alpha = 0.42f))

    /** The play/pause FAB. */
    val floating = ShadowSpec(16.dp, AlgoColors.primary.copy(alpha = 0.80f))

    /** Any pressed state — the shadow collapses, the fill does not grey out. */
    val pressed = ShadowSpec(2.dp, AlgoColors.primary.copy(alpha = 0.40f))

    /** A filled button carries a shadow tinted with its own hue. */
    fun button(hue: Color) = ShadowSpec(14.dp, hue.copy(alpha = 0.70f))

    fun buttonPressed(hue: Color) = ShadowSpec(4.dp, hue.copy(alpha = 0.55f))
}

/** Applies a [ShadowSpec]. Shadows never clip their content. */
fun Modifier.algoShadow(spec: ShadowSpec, shape: Shape): Modifier = this.shadow(
    elevation = spec.elevation,
    shape = shape,
    clip = false,
    ambientColor = spec.color,
    spotColor = spec.color,
)
