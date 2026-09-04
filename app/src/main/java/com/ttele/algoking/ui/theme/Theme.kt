package com.ttele.algoking.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * AlgoKing theme — DESIGN_SYSTEM.md §9.
 *
 * v2 is a light theme, derived from the approved reference. Dynamic colour is
 * deliberately off: the palette carries meaning (violet = chrome, orange = the
 * alternative action, green = status) and must not be re-tinted by the device.
 */
private val AlgoColorScheme = lightColorScheme(
    primary = AlgoColors.primary,
    onPrimary = AlgoColors.onPrimary,
    primaryContainer = AlgoColors.primarySoft,
    onPrimaryContainer = AlgoColors.primary,
    secondary = AlgoColors.secondary,
    onSecondary = AlgoColors.onPrimary,
    secondaryContainer = AlgoColors.secondarySoft,
    onSecondaryContainer = AlgoColors.secondaryDark,
    tertiary = AlgoColors.accent,
    onTertiary = AlgoColors.onPrimary,
    tertiaryContainer = AlgoColors.accentSoft,
    onTertiaryContainer = AlgoColors.accent,
    background = AlgoColors.background,
    onBackground = AlgoColors.textPrimary,
    surface = AlgoColors.surface,
    onSurface = AlgoColors.textPrimary,
    surfaceVariant = AlgoColors.surfaceVariant,
    onSurfaceVariant = AlgoColors.textSecondary,
    outline = AlgoColors.border,
    outlineVariant = AlgoColors.borderStrong,
    error = AlgoColors.error,
    onError = AlgoColors.onPrimary,
    errorContainer = AlgoColors.errorSoft,
    onErrorContainer = AlgoColors.error,
)

@Composable
fun AlgoKingTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = AlgoColorScheme,
        typography = AlgoTypography,
        content = content,
    )
}
