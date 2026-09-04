package com.ttele.algoking.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * AlgoKing colour tokens — DESIGN_SYSTEM.md §1.
 *
 * Every value here was sampled from the approved reference (res/drawable/ref.png).
 * Screens and components read these tokens; nothing declares a literal colour.
 */
object AlgoColors {

    // ── Primary — the app is violet ───────────────────────────────────────────
    val primary = Color(0xFF6D28F0)
    val primaryLight = Color(0xFF8B4BF6)
    val primaryDark = Color(0xFF5A1FD0)
    val primarySoft = Color(0xFFEFEAFE)
    val primarySurface = Color(0xFFF2EFFE)

    // ── Secondary — the alternative action ────────────────────────────────────
    val secondary = Color(0xFFFB8B02)
    val secondaryLight = Color(0xFFFEA802)
    val secondaryDark = Color(0xFFF27600)
    val secondarySoft = Color(0xFFFFF1DC)

    // ── Accent (blue) ─────────────────────────────────────────────────────────
    val accent = Color(0xFF2F7DFC)
    val accentLight = Color(0xFF6BA0FD)
    val accentSoft = Color(0xFFE7F0FF)

    // ── Success — status only, never an action ────────────────────────────────
    val success = Color(0xFF2FB444)
    val successLight = Color(0xFF62D05C)
    val successSoft = Color(0xFFE5F7E3)
    val onSuccessSoft = Color(0xFF1E8A2B)

    // ── Warning is an alias of secondary; the reference has no separate hue ───
    val warning = secondary
    val warningSoft = secondarySoft

    // ── Error — derived, not present in the reference. Consequence only. ──────
    val error = Color(0xFFF0455C)
    val errorSoft = Color(0xFFFEE8EB)

    // ── Fifth category accent ─────────────────────────────────────────────────
    val pink = Color(0xFFEA3C91)
    val pinkLight = Color(0xFFF65AA6)
    val pinkSoft = Color(0xFFFDE7F2)

    // ── Gold — brand ornament (crown, streak, hint) ───────────────────────────
    val gold = Color(0xFFFBA90A)
    val goldSoft = Color(0xFFFDF4DF)

    // ── Surfaces & neutrals (blue-violet biased; never pure grey) ─────────────
    val background = Color(0xFFFCFCFD)
    val backgroundGlowWarm = Color(0xFFFDF3F9)
    val backgroundGlowCool = Color(0xFFF5F2FE)
    val surface = Color(0xFFFFFFFF)
    val surfaceVariant = Color(0xFFF7F6FE)
    val surfaceMuted = Color(0xFFF1F1F7)
    val border = Color(0xFFEDEDF5)
    val borderStrong = Color(0xFFDDDDE9)

    // ── Text ──────────────────────────────────────────────────────────────────
    val textPrimary = Color(0xFF0E1230)
    val textSecondary = Color(0xFF3A4166)
    val textMuted = Color(0xFF6E769B)
    val onPrimary = Color(0xFFFFFFFF)

    // ── Disabled ──────────────────────────────────────────────────────────────
    val disabled = Color(0xFFA6AABC)
    val disabledSurface = Color(0xFFEEEEF5)

    // ── Brand ─────────────────────────────────────────────────────────────────
    val brandInk = Color(0xFF151A4E)
}

/**
 * Algorithm-visualisation semantics — DESIGN_SYSTEM.md §1.3.
 *
 * This is the legend printed under every array. The meanings are a contract and
 * are identical in every algorithm, forever.
 */
object AlgoViz {
    /** Under examination right now. */
    val comparing = AlgoColors.primary
    val comparingTop = Color(0xFFB26AFA)
    val comparingBottom = Color(0xFF6E46EE)

    /** Its partner — the other half of the comparison. */
    val next = AlgoColors.secondary
    val nextTop = Color(0xFFFEB102)
    val nextBottom = Color(0xFFFD901F)

    /** Finalised, in its final position. */
    val sorted = AlgoColors.success
    val sortedTop = Color(0xFF62D05C)
    val sortedBottom = Color(0xFF2FAE3D)

    /** Seen this pass, not yet final — the idle bar. */
    val checked = Color(0xFFDEDEF6)
    val checkedTop = Color(0xFFE1E2FC)
    val checkedBottom = Color(0xFFCECFEA)

    /** Current focus marker — an outline, never a fill. */
    val active = AlgoColors.primary

    /** Out of consideration. */
    val eliminated = Color(0xFFE8E8F0)
    const val eliminatedAlpha = 0.55f

    /** The dashed swap arc, its arrowhead, and pointer marks. */
    val pointer = AlgoColors.primary

    /** The small index numerals above the array. */
    val indexLabel = AlgoColors.textPrimary
}

/**
 * Every filled colour surface in the reference is a two-stop diagonal gradient,
 * top-left to bottom-right — DESIGN_SYSTEM.md §0 rule 3.
 */
object AlgoGradients {

    private fun diagonal(start: Color, end: Color) =
        Brush.linearGradient(listOf(start, end))

    private fun vertical(top: Color, bottom: Color) =
        Brush.verticalGradient(listOf(top, bottom))

    /** Primary buttons, active chips, the FAB. */
    fun primary() = diagonal(Color(0xFF7B3AF2), Color(0xFF5B27E0))

    fun primaryPressed() = diagonal(AlgoColors.primaryDark, Color(0xFF4A16BC))

    /** KEEP and every other secondary-filled surface. */
    fun secondary() = diagonal(Color(0xFFFEA502), Color(0xFFF97D02))

    fun secondaryPressed() = diagonal(AlgoColors.secondaryDark, Color(0xFFDD6A00))

    /** The third decision option, when an algorithm genuinely offers one. */
    fun accent() = diagonal(Color(0xFF4B90FD), Color(0xFF1F63E8))

    fun accentPressed() = diagonal(Color(0xFF2A6FE0), Color(0xFF1550C4))

    /** Algorithm icon tiles and badges, keyed off the accent. */
    fun accentTile(accent: AlgoAccent) = diagonal(accent.light, accent.core)

    /** Array bars — vertical, light at the top. */
    fun bar(top: Color, bottom: Color) = vertical(top, bottom)

    /** The page background glow: warm in the top-left, cool in the top-right. */
    fun pageBackground() = Brush.linearGradient(
        0.00f to AlgoColors.backgroundGlowWarm,
        0.18f to AlgoColors.backgroundGlowCool,
        0.45f to AlgoColors.background,
        1.00f to AlgoColors.background,
    )
}

/**
 * An algorithm owns exactly one accent hue, and that hue colours exactly three
 * things: its icon tile, its category badge, and its progress ring.
 * DESIGN_SYSTEM.md §0.1.
 */
enum class AlgoAccent(
    val core: Color,
    val light: Color,
    val soft: Color,
    val onSoft: Color,
) {
    Green(AlgoColors.success, AlgoColors.successLight, AlgoColors.successSoft, AlgoColors.onSuccessSoft),
    Violet(AlgoColors.primary, AlgoColors.primaryLight, AlgoColors.primarySoft, AlgoColors.primary),
    Orange(AlgoColors.secondary, AlgoColors.secondaryLight, AlgoColors.secondarySoft, AlgoColors.secondaryDark),
    Pink(AlgoColors.pink, AlgoColors.pinkLight, AlgoColors.pinkSoft, AlgoColors.pink),
    Blue(AlgoColors.accent, AlgoColors.accentLight, AlgoColors.accentSoft, AlgoColors.accent),
}
