package com.ttele.algoking.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The one spacing scale — DESIGN_SYSTEM.md §3.
 * Every gap on every screen is a member of this scale.
 */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
}

/** Applied dimensions, so a screen never picks its own — DESIGN_SYSTEM.md §3. */
object Dimens {
    val screenPadding = Spacing.md
    val cardPadding = Spacing.md
    val cardGap = Spacing.sm
    val sectionGap = Spacing.lg

    val headerHeight = 56.dp
    val headerButton = 44.dp
    val headerGlyph = 22.dp

    val bottomNavHeight = 64.dp
    val navGlyph = 26.dp

    val minTouchTarget = 48.dp

    val algorithmCardHeight = 100.dp
    val algorithmIconTile = 56.dp
    val algorithmIconGlyph = 28.dp

    val chipHeight = 34.dp
    val chipPadding = Spacing.md
    val badgeHeight = 22.dp
    val badgePadding = 10.dp

    val statusColumn = 62.dp
    val progressRing = 44.dp
    val progressRingStroke = 4.dp

    val buttonHeight = 56.dp
    val buttonGlyph = 22.dp
    val secondaryGlyph = 20.dp

    val fabSize = 62.dp
    val fabGlyph = 22.dp

    val metricRowHeight = 68.dp

    val arrayBarWidth = 42.dp
    val arrayBarMinHeight = 72.dp
    val arrayBarMaxHeight = 136.dp
    val arrayBarValueInset = 14.dp
    val arcRegionHeight = 38.dp
    val arrayBarGap = Spacing.sm
    val arrayCell = 44.dp

    // Generic sequence renderer (ARCHITECTURE.md §7.1)
    val sceneCellHeight = 44.dp

    // The graph stage. Tall enough for three rows of nodes without scrolling,
    // and the node is a 48dp circle so it clears the 48dp minimum touch target.
    val graphStageHeight = 260.dp
    val graphNode = 48.dp
    val sceneCellGap = 4.dp
    val sceneGroupGap = 3.dp
    val sceneRailHeight = 20.dp

    // A mission box prints a whole product id, so it is sized by its content
    // rather than by a share of the screen. Four fit a phone width; the rest wrap.
    val missionBoxWidth = 78.dp
    val missionBoxHeight = 56.dp
    val missionBoxGap = 8.dp

    // A vertical pile: cells are wide plates rather than square boxes, so the pile
    // reads as a stack of things rather than a column of numbers.
    val stackCellHeight = 40.dp
    val stackCellWidth = 132.dp
    val stackCellGap = 5.dp

    // A chain. Nodes are split into a value and a NEXT compartment, and the arrows
    // between them are given real width — they are the subject, not the spacing.
    val chainRowHeight = 76.dp
    val chainNodeHeight = 40.dp
    val chainValueWidth = 30.dp
    val chainNextWidth = 14.dp
    val chainNextDot = 6.dp
    val chainGap = 22.dp
    val chainGapWide = 40.dp
    val chainArrowHeight = 14.dp
    val chainDetachedHeight = 34.dp

    // A bucket table. Every bucket is the same height whether or not it holds
    // anything — the empty ones are the point: they are addressable space.
    val bucketHeight = 52.dp
    val bucketGap = 6.dp
    val bucketLabelWidth = 52.dp
    val bucketRuleHeight = 32.dp
    val chainTickWidth = 14.dp

    /** The gutter that names each row of the Stack-vs-Queue table. */
    val compareLabelWidth = 64.dp

    val legendSwatch = 14.dp
    val legendGap = Spacing.sm

    val stepperNode = 30.dp
    val stepperConnector = 2.dp

    val stepDot = 6.dp
    val stepDotActive = 10.dp
    val dotComplete = 14.dp
    val dotCurrent = 18.dp
    val dotUpcoming = 12.dp
    val dotRail = 2.dp

    val chevron = 20.dp
    val lockTile = 36.dp
    val mascot = 88.dp
    val starSize = 40.dp
    val starGlyph = 24.dp

    val hairline = 1.dp
    val outline = 1.5.dp
    val outlineStrong = 2.5.dp
}

/**
 * Corner radii — DESIGN_SYSTEM.md §4.
 * There is exactly one card radius. If it reads as a card, it is [card].
 */
object Radius {
    val swatch = RoundedCornerShape(4.dp)
    val cell = RoundedCornerShape(10.dp)

    /** A whole array on one row means narrow cells; 10dp would read as a capsule. */
    val sceneCell = RoundedCornerShape(8.dp)
    val button = RoundedCornerShape(16.dp)
    val icon = RoundedCornerShape(16.dp)
    val card = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val pill = RoundedCornerShape(percent = 50)
}
