package com.ttele.algoking.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoGradients
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Elevation
import com.ttele.algoking.ui.theme.Radius
import com.ttele.algoking.ui.theme.ShadowSpec
import com.ttele.algoking.ui.theme.Spacing
import com.ttele.algoking.ui.theme.algoShadow

/**
 * The page — DESIGN_SYSTEM.md §0 rule 1.
 * A near-white ground carrying a faint warm glow in the top-left corner.
 */
@Composable
fun AlgoScreen(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AlgoGradients.pageBackground()),
    ) {
        content()
    }
}

/**
 * The one card — DESIGN_SYSTEM.md §4.
 * Every rectangle that reads as a card comes through here, so the radius, the
 * shadow and the padding can never drift apart between screens.
 */
@Composable
fun AlgoCard(
    modifier: Modifier = Modifier,
    color: Color = AlgoColors.surface,
    shadow: ShadowSpec? = Elevation.card,
    border: Color? = null,
    shape: Shape = Radius.card,
    padding: Dp = Dimens.cardPadding,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .then(if (shadow != null) Modifier.algoShadow(shadow, shape) else Modifier)
            .background(color, shape)
            .then(
                if (border != null) Modifier.border(Dimens.hairline, border, shape) else Modifier,
            )
            .padding(padding),
    ) {
        content()
    }
}

/** Press feedback — the shadow drops and the element scales. It never greys out. */
@Composable
private fun Modifier.pressScale(pressed: Boolean): Modifier {
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    return this.scale(scale)
}

/**
 * Primary button — DESIGN_SYSTEM.md §6.7.
 * Also the geometry for [DecisionButton]; only the gradient and glyph differ.
 */
@Composable
fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    gradient: Brush = AlgoGradients.primary(),
    pressedGradient: Brush = AlgoGradients.primaryPressed(),
    shadowHue: Color = AlgoColors.primary,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Row(
        modifier = modifier
            .height(Dimens.buttonHeight)
            .alpha(if (enabled) 1f else 0.45f)
            .pressScale(pressed)
            .algoShadow(
                if (pressed) Elevation.buttonPressed(shadowHue) else Elevation.button(shadowHue),
                Radius.button,
            )
            .background(if (pressed) pressedGradient else gradient, Radius.button)
            .noRippleClickable(interaction, enabled, onClick)
            .padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            AlgoIcon(icon, AlgoColors.onPrimary, Dimens.buttonGlyph)
            Box(Modifier.width(Spacing.sm))
        }
        Text(label, style = AlgoType.labelLarge, color = AlgoColors.onPrimary)
    }
}

/**
 * A decision option — DESIGN_SYSTEM.md §6.9.
 *
 * Every option a learner can pick is this one component. The tones are assigned by
 * position, never by correctness, so the UI cannot style the right answer
 * differently even by accident (PRODUCT_SPEC.md §5).
 */
enum class DecisionTone(internal val hue: Color) {
    First(AlgoColors.primary),
    Second(AlgoColors.secondary),
    Third(AlgoColors.accent),
    ;

    companion object {
        fun forIndex(index: Int): DecisionTone = entries[index % entries.size]
    }
}

@Composable
fun RowScope.DecisionButton(
    label: String,
    tone: DecisionTone,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    PrimaryButton(
        label = label,
        modifier = Modifier.weight(1f),
        icon = icon,
        gradient = tone.gradient(),
        pressedGradient = tone.pressedGradient(),
        shadowHue = tone.hue,
        enabled = enabled,
        onClick = onClick,
    )
}

internal fun DecisionTone.gradient(): Brush = when (this) {
    DecisionTone.First -> AlgoGradients.primary()
    DecisionTone.Second -> AlgoGradients.secondary()
    DecisionTone.Third -> AlgoGradients.accent()
}

internal fun DecisionTone.pressedGradient(): Brush = when (this) {
    DecisionTone.First -> AlgoGradients.primaryPressed()
    DecisionTone.Second -> AlgoGradients.secondaryPressed()
    DecisionTone.Third -> AlgoGradients.accentPressed()
}

/** The SWAP / KEEP pair, expressed in the same component. */
enum class Decision(val label: String, internal val tone: DecisionTone) {
    Swap("SWAP", DecisionTone.First),
    Keep("KEEP", DecisionTone.Second),
}

@Composable
fun RowScope.DecisionButton(
    decision: Decision,
    icon: ImageVector,
    onClick: () -> Unit = {},
) = DecisionButton(decision.label, decision.tone, icon, onClick = onClick)

/**
 * Secondary button — DESIGN_SYSTEM.md §6.8.
 * Previous · Next · Restart · Hint · Undo are all this one component.
 */
@Composable
fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    iconTint: Color = AlgoColors.textSecondary,
    onClick: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Row(
        modifier = modifier
            .height(Dimens.buttonHeight)
            .pressScale(pressed)
            .algoShadow(if (pressed) Elevation.pressed else Elevation.raised, Radius.button)
            .background(AlgoColors.surface, Radius.button)
            .noRippleClickable(interaction, onClick = onClick)
            .padding(horizontal = Spacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            AlgoIcon(leadingIcon, iconTint, Dimens.secondaryGlyph)
            Box(Modifier.width(Spacing.xs))
        }
        Text(label, style = AlgoType.titleSmall, color = AlgoColors.textPrimary)
        if (trailingIcon != null) {
            Box(Modifier.width(Spacing.xs))
            AlgoIcon(trailingIcon, iconTint, Dimens.secondaryGlyph)
        }
    }
}

/** A 44dp white icon tile — the header buttons on all three screens. */
@Composable
fun IconTileButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = AlgoColors.textPrimary,
    onClick: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = modifier
            .size(Dimens.headerButton)
            .pressScale(pressed)
            .algoShadow(if (pressed) Elevation.pressed else Elevation.card, Radius.icon)
            .background(AlgoColors.surface, Radius.icon)
            .noRippleClickable(interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AlgoIcon(icon, tint, Dimens.headerGlyph)
    }
}

/** The play/pause control — DESIGN_SYSTEM.md §6.18. */
@Composable
fun PlayPauseFab(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .size(Dimens.fabSize)
            .pressScale(pressed)
            .algoShadow(if (pressed) Elevation.pressed else Elevation.floating, shape)
            .background(AlgoGradients.primary(), shape)
            .noRippleClickable(interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AlgoIcon(icon, AlgoColors.onPrimary, Dimens.fabGlyph)
    }
}

/** Every glyph in the app is drawn through here, so tint and size stay tokenised. */
@Composable
fun AlgoIcon(icon: ImageVector, tint: Color, size: Dp) {
    Image(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(size),
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint),
    )
}

/** Ripple-free click, matching the reference's press-and-drop feedback. */
private fun Modifier.noRippleClickable(
    interaction: MutableInteractionSource,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = interaction,
    indication = null,
    enabled = enabled,
    onClick = onClick,
)

/** A full-bleed 1dp rule in the border token. */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Dimens.hairline)
            .background(AlgoColors.border),
    )
}

/** Convenience for the many fixed gaps in the layouts. */
@Composable
fun Gap(size: Dp) = Box(Modifier.size(size))

