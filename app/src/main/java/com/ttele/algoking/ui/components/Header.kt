package com.ttele.algoking.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.ttele.algoking.ui.icons.AlgoIcons
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Elevation
import com.ttele.algoking.ui.theme.Radius
import com.ttele.algoking.ui.theme.Spacing
import com.ttele.algoking.ui.theme.algoShadow

/**
 * App header — DESIGN_SYSTEM.md §6.1.
 *
 * 56dp, transparent, three slots: leading icon button · centred title · trailing
 * action. Identical on Home, Learning and Practice — only the contents change.
 */
@Composable
fun AlgoHeader(
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    center: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.headerHeight)
            .padding(horizontal = Dimens.screenPadding),
    ) {
        Box(Modifier.align(Alignment.CenterStart)) { leading() }
        Box(Modifier.align(Alignment.Center)) { center() }
        Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

/** The screen title used by Learning and Practice. */
@Composable
fun HeaderTitle(text: String) {
    Text(text, style = AlgoType.titleLarge, color = AlgoColors.textPrimary)
}

/**
 * The wordmark — DESIGN_SYSTEM.md §6.1.
 * `Algo` in brandInk, `King` in primary, a tilted gold crown over the `i`.
 */
@Composable
fun AlgoWordmark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("Algo", style = AlgoType.wordmark, color = AlgoColors.brandInk)
        Text("K", style = AlgoType.wordmark, color = AlgoColors.primary)
        Box(contentAlignment = Alignment.TopCenter) {
            Text("i", style = AlgoType.wordmark, color = AlgoColors.primary)
            Box(
                Modifier
                    .offset(y = (-3).dp)
                    .rotate(-12f),
            ) {
                AlgoIcon(AlgoIcons.Crown, AlgoColors.gold, 12.dp)
            }
        }
        Text("ng", style = AlgoType.wordmark, color = AlgoColors.primary)
    }
}

/**
 * Streak chip — the trailing slot on Practice.
 * A pill on `goldSoft`, the same 44dp height as an [IconTileButton] so the two
 * header treatments line up across screens.
 */
@Composable
fun StreakChip(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(Dimens.headerButton)
            .algoShadow(Elevation.card, Radius.pill)
            .background(AlgoColors.goldSoft, Radius.pill)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        AlgoIcon(AlgoIcons.Bolt, AlgoColors.gold, Dimens.secondaryGlyph)
        Box(Modifier.width(Spacing.xs))
        Text("$count", style = AlgoType.titleSmall, color = AlgoColors.textPrimary)
    }
}

/** The crown tile that opens Home — the brand's leading slot. */
@Composable
fun CrownButton(modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    IconTileButton(
        icon = AlgoIcons.Crown,
        modifier = modifier,
        tint = AlgoColors.gold,
        onClick = onClick,
    )
}

/** Reserves the header's leading width when a screen has no leading action. */
@Composable
fun HeaderSpacer() = Box(Modifier.size(Dimens.headerButton))
