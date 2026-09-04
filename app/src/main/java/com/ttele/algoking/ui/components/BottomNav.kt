package com.ttele.algoking.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.ttele.algoking.ui.icons.AlgoIcons
import com.ttele.algoking.ui.theme.AlgoColors
import com.ttele.algoking.ui.theme.AlgoType
import com.ttele.algoking.ui.theme.Dimens
import com.ttele.algoking.ui.theme.Elevation
import com.ttele.algoking.ui.theme.Radius
import com.ttele.algoking.ui.theme.Spacing
import com.ttele.algoking.ui.theme.algoShadow

/** The four destinations — PRODUCT_SPEC.md §10, rendered per DESIGN_SYSTEM.md §6.2. */
enum class NavDestination(
    val label: String,
    val filled: ImageVector,
    val outlined: ImageVector,
) {
    Home("Home", AlgoIcons.HomeFilled, AlgoIcons.HomeOutline),
    Journey("Journey", AlgoIcons.Book, AlgoIcons.Book),
    Daily("Daily", AlgoIcons.CalendarCheck, AlgoIcons.CalendarCheck),
    Profile("Profile", AlgoIcons.Person, AlgoIcons.Person),
}

/**
 * Bottom navigation — DESIGN_SYSTEM.md §6.2.
 * Filled violet glyph and label when active, outlined muted glyph when not.
 * No pill, no indicator, no badge.
 */
@Composable
fun AlgoBottomNav(
    selected: NavDestination,
    modifier: Modifier = Modifier,
    onSelect: (NavDestination) -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .algoShadow(Elevation.card, Radius.sheet)
            .background(AlgoColors.surface, Radius.sheet),
    ) {
        HairlineDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(Dimens.bottomNavHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavDestination.entries.forEach { destination ->
                NavItem(destination, destination == selected) { onSelect(destination) }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    destination: NavDestination,
    active: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val tint = if (active) AlgoColors.primary else AlgoColors.textMuted

    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(interaction, null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AlgoIcon(
            icon = if (active) destination.filled else destination.outlined,
            tint = tint,
            size = Dimens.navGlyph,
        )
        Gap(Spacing.xxs)
        androidx.compose.material3.Text(
            text = destination.label,
            style = AlgoType.labelSmall,
            color = tint,
        )
    }
}
