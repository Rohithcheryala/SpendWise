package com.example.spendwise.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// Brand palette — "Indigo Ledger"
//
// Indigo is the brand + action lane. It sits far in hue from BOTH income-green
// (142°) and expense-red (0°), so the brand can never be mistaken for a
// money-direction signal — the structural flaw of the old emerald palette,
// where brand and `income` were 28° apart.
//
// Neutrals are cool and near-neutral: structure carries the contrast, and the
// accent colours carry the meaning. See UI_UX_AUDIT.md §4.
//
// Rules pinned to these values (validated, see §5 / Appendix B):
//   • every text colour ≥ 4.5:1 on `background`, `surface` and its container
//   • card ≥ 1.15:1 (light) / 1.10:1 (dark) against `background`
//   • `outlineVariant` ≥ 1.5:1 on a card, so a 1dp hairline is actually visible
// ─────────────────────────────────────────────────────────────────────────────

// ── Light ────────────────────────────────────────────────────────────────────
val md_theme_light_primary = Color(0xFF4338CA)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFE0E7FF)
val md_theme_light_onPrimaryContainer = Color(0xFF1E1B4B)
val md_theme_light_secondary = Color(0xFF4F5B76)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFDCE3F0)
val md_theme_light_onSecondaryContainer = Color(0xFF131B2E)
val md_theme_light_tertiary = Color(0xFF0F766E)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFCBFBF1)
val md_theme_light_onTertiaryContainer = Color(0xFF042F2E)
val md_theme_light_error = Color(0xFFB42318)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFFEE4E2)
val md_theme_light_onErrorContainer = Color(0xFF55160C)

// The app chrome is a cool grey and cards are pure WHITE. This pair is the
// single most important change: it gives every card an edge to sit on, which
// `background == surface` could never do. 1.16:1 + a 1dp hairline reads crisp.
val md_theme_light_background = Color(0xFFEBEEF4)
val md_theme_light_onBackground = Color(0xFF101828)
val md_theme_light_surface = Color(0xFFFFFFFF)
val md_theme_light_onSurface = Color(0xFF101828)
val md_theme_light_surfaceVariant = Color(0xFFE1E5EE)
val md_theme_light_onSurfaceVariant = Color(0xFF46505F)
val md_theme_light_outline = Color(0xFF6B7484)
val md_theme_light_outlineVariant = Color(0xFFCBD1DB)
val md_theme_light_scrim = Color(0xFF000000)
val md_theme_light_inverseSurface = Color(0xFF2A3040)
val md_theme_light_inverseOnSurface = Color(0xFFEDEFF5)
val md_theme_light_inversePrimary = Color(0xFFB4B9FF)
// Light ramp: white surface → progressively greyer containers (M3 direction).
val md_theme_light_surfaceContainerLowest = Color(0xFFF7F8FB)
val md_theme_light_surfaceContainerLow = Color(0xFFF4F6FA)
val md_theme_light_surfaceContainer = Color(0xFFF1F3F8)
val md_theme_light_surfaceContainerHigh = Color(0xFFEAEDF4)
val md_theme_light_surfaceContainerHighest = Color(0xFFE1E5EE)

// ── Dark ────────────────────────────────────────────────────────────────────
val md_theme_dark_primary = Color(0xFFB4B9FF)
val md_theme_dark_onPrimary = Color(0xFF1E1B4B)
val md_theme_dark_primaryContainer = Color(0xFF3730A3)
val md_theme_dark_onPrimaryContainer = Color(0xFFE0E7FF)
val md_theme_dark_secondary = Color(0xFFB9C3DA)
val md_theme_dark_onSecondary = Color(0xFF222C42)
val md_theme_dark_secondaryContainer = Color(0xFF37415A)
val md_theme_dark_onSecondaryContainer = Color(0xFFDCE3F0)
val md_theme_dark_tertiary = Color(0xFF5EEAD4)
val md_theme_dark_onTertiary = Color(0xFF042F2E)
val md_theme_dark_tertiaryContainer = Color(0xFF115E59)
val md_theme_dark_onTertiaryContainer = Color(0xFFCBFBF1)
val md_theme_dark_error = Color(0xFFFDA29B)
val md_theme_dark_onError = Color(0xFF55160C)
val md_theme_dark_errorContainer = Color(0xFF7A271A)
val md_theme_dark_onErrorContainer = Color(0xFFFEE4E2)

// Near-black chrome, raised navy cards (1.13:1). Shadows are useless on this
// background, so layering is carried by tone + hairline instead.
val md_theme_dark_background = Color(0xFF05080C)
val md_theme_dark_onBackground = Color(0xFFE7EAF0)
val md_theme_dark_surface = Color(0xFF121822)
val md_theme_dark_onSurface = Color(0xFFE7EAF0)
val md_theme_dark_surfaceVariant = Color(0xFF2A3341)
val md_theme_dark_onSurfaceVariant = Color(0xFFAEB6C4)
val md_theme_dark_outline = Color(0xFF7C8797)
val md_theme_dark_outlineVariant = Color(0xFF333C4C)
val md_theme_dark_scrim = Color(0xFF000000)
val md_theme_dark_inverseSurface = Color(0xFFE7EAF0)
val md_theme_dark_inverseOnSurface = Color(0xFF121822)
val md_theme_dark_inversePrimary = Color(0xFF4338CA)
// Dark ramp: near-black surface → progressively lighter containers (M3 direction).
val md_theme_dark_surfaceContainerLowest = Color(0xFF0B0F16)
val md_theme_dark_surfaceContainerLow = Color(0xFF161D29)
val md_theme_dark_surfaceContainer = Color(0xFF1A2130)
val md_theme_dark_surfaceContainerHigh = Color(0xFF202938)
val md_theme_dark_surfaceContainerHighest = Color(0xFF2A3341)