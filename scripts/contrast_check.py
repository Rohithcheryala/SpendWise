#!/usr/bin/env python3
"""
Palette contrast validator for SpendWise.

Why this exists: UI_UX_AUDIT.md makes numeric claims about every colour token —
text must clear WCAG AA (4.5:1), and a card must separate from the app
background by >= 1.15:1 (light) / 1.10:1 (dark) so the surface ramp is actually
perceptible. Eyeballing hex values cannot verify that; this computes it.

    python3 scripts/contrast_check.py      # the proposed Indigo Ledger palette

Exit code 0 when every check passes, 1 when any fails (so it can be wired into
CI). Standard library only.

Reference: WCAG 2.1 relative luminance / contrast ratio.
"""

from __future__ import annotations

import sys

MIN_TEXT = 4.5            # WCAG AA, normal-size text
MIN_SURFACE_LIGHT = 1.15  # card vs app background, light theme
MIN_SURFACE_DARK = 1.10   # card vs app background, dark theme
MIN_HAIRLINE = 1.50       # a 1dp card border must be visible
MIN_NESTED = 1.04         # a nested panel only needs to be *felt*, not seen


def luminance(hex_colour: str) -> float:
    value = hex_colour.lstrip("#")
    channels = [int(value[i:i + 2], 16) / 255 for i in (0, 2, 4)]

    def linearise(c: float) -> float:
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

    r, g, b = (linearise(c) for c in channels)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a: str, b: str) -> float:
    la, lb = luminance(a), luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


LIGHT = {
    "primary": "#4338CA", "onPrimary": "#FFFFFF",
    "primaryContainer": "#E0E7FF", "onPrimaryContainer": "#1E1B4B",
    "secondaryContainer": "#DCE3F0", "onSecondaryContainer": "#131B2E",
    "tertiary": "#0F766E",
    "error": "#B42318", "errorContainer": "#FEE4E2", "onErrorContainer": "#55160C",
    "background": "#EBEEF4", "onBackground": "#101828",
    "surface": "#FFFFFF", "onSurface": "#101828",
    "surfaceContainer": "#F1F3F8", "surfaceContainerHigh": "#EAEDF4",
    "onSurfaceVariant": "#46505F",
    "outlineVariant": "#CBD1DB",
    "inverseSurface": "#2A3040", "inverseOnSurface": "#EDEFF5",
    "textPrimary": "#101828", "textSecondary": "#46505F", "textTertiary": "#5C6675",
}

DARK = {
    "primary": "#B4B9FF", "onPrimary": "#1E1B4B",
    "primaryContainer": "#3730A3", "onPrimaryContainer": "#E0E7FF",
    "secondaryContainer": "#37415A", "onSecondaryContainer": "#DCE3F0",
    "tertiary": "#5EEAD4",
    "error": "#FDA29B", "errorContainer": "#7A271A", "onErrorContainer": "#FEE4E2",
    "background": "#05080C", "onBackground": "#E7EAF0",
    "surface": "#121822", "onSurface": "#E7EAF0",
    "surfaceContainer": "#1A2130", "surfaceContainerHigh": "#202938",
    "onSurfaceVariant": "#AEB6C4",
    "outlineVariant": "#333C4C",
    "inverseSurface": "#E7EAF0", "inverseOnSurface": "#121822",
    "textPrimary": "#E7EAF0", "textSecondary": "#AEB6C4", "textTertiary": "#949CAB",
}

SEMANTICS_LIGHT = {
    "income": "#0F6B34", "incomeContainer": "#DCFAE6",
    "expense": "#C81E1E", "expenseContainer": "#FEE4E2",
    "transfer": "#1D4ED8", "transferContainer": "#DBEAFE",
    "warning": "#B54708", "warningContainer": "#FEF0C7",
}

SEMANTICS_DARK = {
    "income": "#4ADE80", "incomeContainer": "#05432A",
    "expense": "#FF8A8F", "expenseContainer": "#4E1315",
    "transfer": "#93B4FF", "transferContainer": "#1B2A52",
    "warning": "#FDB022", "warningContainer": "#4A2A05",
}


def check_group(title: str, pairs: list[tuple[str, str, float]]) -> int:
    print(f"--- {title} ---")
    failures = 0
    for label, fg, bg, minimum in pairs:
        ratio = contrast(fg, bg)
        ok = ratio >= minimum
        if not ok:
            failures += 1
        print(
            "%-50s %-8s / %-8s = %5.2f  (min %4.2f) %s"
            % (label, fg, bg, ratio, minimum, "PASS" if ok else "*** FAIL ***")
        )
    print(f"failures: {failures}\n")
    return failures


def text_pairs(theme: dict, semantics: dict) -> list[tuple[str, str, float]]:
    card = theme["surface"]
    high = theme["surfaceContainerHigh"]
    return [
        ("text on app background", theme["onBackground"], theme["background"], MIN_TEXT),
        ("text on card", theme["onSurface"], card, MIN_TEXT),
        ("secondary text on background",
         theme["onSurfaceVariant"], theme["background"], MIN_TEXT),
        ("secondary text on card", theme["onSurfaceVariant"], card, MIN_TEXT),
        ("secondary text on containerHigh", theme["onSurfaceVariant"], high, MIN_TEXT),
        ("textPrimary on card", theme["textPrimary"], card, MIN_TEXT),
        ("textSecondary on card", theme["textSecondary"], card, MIN_TEXT),
        ("textTertiary on card", theme["textTertiary"], card, MIN_TEXT),
        ("textTertiary on containerHigh", theme["textTertiary"], high, MIN_TEXT),
        ("primary on background", theme["primary"], theme["background"], MIN_TEXT),
        ("primary on card", theme["primary"], card, MIN_TEXT),
        ("onPrimary on primary", theme["onPrimary"], theme["primary"], MIN_TEXT),
        ("onPrimaryContainer on primaryContainer",
         theme["onPrimaryContainer"], theme["primaryContainer"], MIN_TEXT),
        ("onSecondaryContainer on secondaryContainer",
         theme["onSecondaryContainer"], theme["secondaryContainer"], MIN_TEXT),
        ("tertiary on card", theme["tertiary"], card, MIN_TEXT),
        ("error on card", theme["error"], card, MIN_TEXT),
        ("onErrorContainer on errorContainer",
         theme["onErrorContainer"], theme["errorContainer"], MIN_TEXT),
        ("inverseOnSurface on inverseSurface",
         theme["inverseOnSurface"], theme["inverseSurface"], MIN_TEXT),
        ("income on card", semantics["income"], card, MIN_TEXT),
        ("income on incomeContainer",
         semantics["income"], semantics["incomeContainer"], MIN_TEXT),
        ("expense on card", semantics["expense"], card, MIN_TEXT),
        ("expense on expenseContainer",
         semantics["expense"], semantics["expenseContainer"], MIN_TEXT),
        ("transfer on card", semantics["transfer"], card, MIN_TEXT),
        ("transfer on transferContainer",
         semantics["transfer"], semantics["transferContainer"], MIN_TEXT),
        ("warning on card", semantics["warning"], card, MIN_TEXT),
        ("warning on warningContainer",
         semantics["warning"], semantics["warningContainer"], MIN_TEXT),
    ]


def surface_pairs(theme: dict, card_vs_background: float) -> list[tuple[str, str, float]]:
    return [
        ("card vs app background", theme["surface"], theme["background"], card_vs_background),
        ("nested container vs card", theme["surfaceContainer"], theme["surface"], MIN_NESTED),
        ("containerHigh vs card", theme["surfaceContainerHigh"], theme["surface"], 1.10),
        ("card hairline border vs card",
         theme["outlineVariant"], theme["surface"], MIN_HAIRLINE),
    ]


def main() -> int:
    print("=== Indigo Ledger — palette validation ===\n")

    failures = 0
    failures += check_group("LIGHT text (WCAG AA >= 4.5:1)", text_pairs(LIGHT, SEMANTICS_LIGHT))
    failures += check_group("LIGHT surface separation", surface_pairs(LIGHT, MIN_SURFACE_LIGHT))
    failures += check_group("DARK text (WCAG AA >= 4.5:1)", text_pairs(DARK, SEMANTICS_DARK))
    failures += check_group("DARK surface separation", surface_pairs(DARK, MIN_SURFACE_DARK))

    print("=== income vs expense luminance separation (greyscale / CVD) ===")
    print("  Below ~1.10:1 the two are indistinguishable without hue, so the +/-")
    print("  sign and the direction arrow must always accompany the colour.")
    print("  light %.2f" % contrast(SEMANTICS_LIGHT["income"], SEMANTICS_LIGHT["expense"]))
    print("  dark  %.2f" % contrast(SEMANTICS_DARK["income"], SEMANTICS_DARK["expense"]))
    print()
    print("TOTAL FAILURES: %d" % failures)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())