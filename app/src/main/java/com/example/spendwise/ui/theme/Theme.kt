package com.example.spendwise.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    errorContainer = md_theme_light_errorContainer,
    onError = md_theme_light_onError,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    outlineVariant = md_theme_light_outlineVariant,
    scrim = md_theme_light_scrim,
    inverseSurface = md_theme_light_inverseSurface,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inversePrimary = md_theme_light_inversePrimary,
    surfaceContainerLowest = md_theme_light_surfaceContainerLowest,
    surfaceContainerLow = md_theme_light_surfaceContainerLow,
    surfaceContainer = md_theme_light_surfaceContainer,
    surfaceContainerHigh = md_theme_light_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_light_surfaceContainerHighest,
)

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    onError = md_theme_dark_onError,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant,
    scrim = md_theme_dark_scrim,
    inverseSurface = md_theme_dark_inverseSurface,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceContainerLowest = md_theme_dark_surfaceContainerLowest,
    surfaceContainerLow = md_theme_dark_surfaceContainerLow,
    surfaceContainer = md_theme_dark_surfaceContainer,
    surfaceContainerHigh = md_theme_dark_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_dark_surfaceContainerHighest,
)

/**
 * Semantic colors for financial meaning (income / expense / transfer / warning).
 * These are NOT part of Material's color scheme but are theme-aware and provided
 * alongside it, so screens never hardcode greens/reds again.
 */
@Immutable
data class SemanticColors(
    val income: Color,
    val incomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val transfer: Color,
    val transferContainer: Color,
    val warning: Color,
    val warningContainer: Color
)

private fun lightSemanticColors() = SemanticColors(
    income = Color(0xFF0F6B34),
    incomeContainer = Color(0xFFDCFAE6),
    expense = Color(0xFFC81E1E),
    expenseContainer = Color(0xFFFEE4E2),
    transfer = Color(0xFF1D4ED8),
    transferContainer = Color(0xFFDBEAFE),
    warning = Color(0xFFB54708),
    warningContainer = Color(0xFFFEF0C7)
)

private fun darkSemanticColors() = SemanticColors(
    income = Color(0xFF4ADE80),
    incomeContainer = Color(0xFF05432A),
    expense = Color(0xFFFF8A8F),
    expenseContainer = Color(0xFF4E1315),
    transfer = Color(0xFF93B4FF),
    transferContainer = Color(0xFF1B2A52),
    warning = Color(0xFFFDB022),
    warningContainer = Color(0xFF4A2A05)
)

val LocalSemanticColors = staticCompositionLocalOf { lightSemanticColors() }

/**
 * Text colours are *measured*, never derived by multiplying one colour's alpha.
 * All three clear 4.5:1 on `background`, `surface` and `surfaceContainerHigh`
 * in both themes — worst case 4.96:1 (light tertiary on surfaceContainerHigh).
 * Contrast per token is listed in UI_UX_AUDIT.md §5.9.
 */
@Immutable
data class TextColors(
    val primary: Color,     // titles, money, values
    val secondary: Color,   // supporting copy, list subtitles
    val tertiary: Color     // placeholders, captions, de-emphasised meta
)

private val LightTextColors = TextColors(
    primary = Color(0xFF101828),
    secondary = Color(0xFF46505F),
    tertiary = Color(0xFF5C6675)   // still ≥ 4.5:1 — a real placeholder colour
)

private val DarkTextColors = TextColors(
    primary = Color(0xFFE7EAF0),
    secondary = Color(0xFFAEB6C4),
    tertiary = Color(0xFF949CAB)
)

val LocalTextColors = staticCompositionLocalOf { LightTextColors }

/**
 * Categorical colours for things that are merely *different*, not meaningful:
 * account types, avatars, donut slices. Deliberately excludes income / expense
 * / transfer — those three encode direction, and reusing them as decoration
 * (which `donutPalette()` did) makes the legend teach the user nothing.
 */
@Immutable
data class CategoricalColors(val swatches: List<Color>)

private val LightCategorical = CategoricalColors(listOf(
    Color(0xFF4338CA), // indigo (brand)
    Color(0xFF0F766E), // teal
    Color(0xFFB54708), // amber
    Color(0xFF7E22CE), // purple
    Color(0xFF0369A1), // sky
    Color(0xFFBE185D), // magenta
    Color(0xFF854D0E), // bronze
    Color(0xFF334155), // slate
))

private val DarkCategorical = CategoricalColors(listOf(
    Color(0xFFB4B9FF), Color(0xFF5EEAD4), Color(0xFFFDB022), Color(0xFFD8B4FE),
    Color(0xFF7DD3FC), Color(0xFFF9A8D4), Color(0xFFFCD34D), Color(0xFF94A3B8),
))

val LocalCategoricalColors = staticCompositionLocalOf { LightCategorical }

/** Accessor for SpendWise-specific semantic colors: `SpendwiseTheme.colors.income` */
object SpendwiseTheme {
    val colors: SemanticColors
        @Composable get() = LocalSemanticColors.current

    val text: TextColors
        @Composable get() = LocalTextColors.current

    val categorical: CategoricalColors
        @Composable get() = LocalCategoricalColors.current
}

@Composable
fun SpendwiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic (Material You) color disabled by default: it overrides our brand
    // palette and made the app look generic. Flip to true if wallpaper theming
    // is preferred over brand identity at a later date.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val semanticColors = if (darkTheme) darkSemanticColors() else lightSemanticColors()
    val textColors = if (darkTheme) DarkTextColors else LightTextColors
    val categoricalColors = if (darkTheme) DarkCategorical else LightCategorical

    CompositionLocalProvider(
        LocalSemanticColors provides semanticColors,
        LocalTextColors provides textColors,
        LocalCategoricalColors provides categoricalColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = SpendwiseShapes,
            content = content
        )
    }
}