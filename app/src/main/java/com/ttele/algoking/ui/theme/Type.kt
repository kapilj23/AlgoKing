package com.ttele.algoking.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * AlgoKing typography tokens — DESIGN_SYSTEM.md §2.
 *
 * The face in the reference is Plus Jakarta Sans: geometric humanist, single-storey
 * `g`, straight-tail `y`, tall x-height. Drop PlusJakartaSans-{Medium,SemiBold,Bold,
 * ExtraBold}.ttf into `res/font/` and change [fontFamily] to the FontFamily built
 * from them — that is the only edit required; every style below inherits it.
 *
 * Weights used: 500 / 600 / 700 / 800. There is no Regular and no Light anywhere
 * in the reference.
 */
object AlgoType {

    /** The single family token. Never name a family anywhere else. */
    val fontFamily: FontFamily = FontFamily.SansSerif

    private val trim = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

    private fun style(
        size: Int,
        weight: FontWeight,
        lineHeight: Int,
        tracking: Float = 0f,
    ) = TextStyle(
        fontFamily = fontFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.sp,
        lineHeightStyle = trim,
    )

    /** The two-line home hero. */
    val displayLarge = style(26, FontWeight.ExtraBold, 34, -0.4f)

    /** "Compare 8 and 2" — the question on a learning screen. */
    val headlineLarge = style(22, FontWeight.ExtraBold, 28, -0.2f)

    /** Screen title in the app header. */
    val titleLarge = style(20, FontWeight.ExtraBold, 26, -0.2f)

    /** Algorithm card title, "Step 3 / 14", "Explanation". */
    val titleMedium = style(17, FontWeight.Bold, 22, -0.1f)

    /** Previous / Next / Restart / Hint / Undo. */
    val titleSmall = style(15, FontWeight.Bold, 20)

    /** Instruction copy, explanation body. */
    val bodyLarge = style(15, FontWeight.Medium, 24)

    /** Card description, legend labels, hero subtitle. */
    val bodyMedium = style(13, FontWeight.Medium, 20)

    /** SWAP · KEEP · Submit Answer. */
    val labelLarge = style(16, FontWeight.ExtraBold, 20, 0.6f)

    /** Category chips, stepper labels, "Step 3 / 14". */
    val labelMedium = style(13, FontWeight.SemiBold, 16)

    /** Category badges, metric labels, nav labels, "Mastered". */
    val labelSmall = style(11, FontWeight.SemiBold, 14, 0.2f)

    /** Array bar and array cell values. */
    val numeralLarge = style(22, FontWeight.ExtraBold, 26, -0.5f)

    /** Metric values. */
    val numeralMedium = style(20, FontWeight.ExtraBold, 24, -0.5f)

    /** The percentage inside a progress ring. */
    val numeralSmall = style(14, FontWeight.ExtraBold, 16, -0.3f)

    /** Values inside a dense sequence cell — a whole array must fit one row. */
    val sceneNumeral = style(15, FontWeight.ExtraBold, 18, -0.4f)

    /** The AlgoKing wordmark. */
    val wordmark = style(26, FontWeight.ExtraBold, 30, -0.6f)
}

/** Material3 mapping, so stock M3 components inherit the system too. */
val AlgoTypography = Typography(
    displayLarge = AlgoType.displayLarge,
    headlineLarge = AlgoType.headlineLarge,
    titleLarge = AlgoType.titleLarge,
    titleMedium = AlgoType.titleMedium,
    titleSmall = AlgoType.titleSmall,
    bodyLarge = AlgoType.bodyLarge,
    bodyMedium = AlgoType.bodyMedium,
    labelLarge = AlgoType.labelLarge,
    labelMedium = AlgoType.labelMedium,
    labelSmall = AlgoType.labelSmall,
)
