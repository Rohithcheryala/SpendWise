package com.example.spendwise.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.spendwise.ui.theme.SpendwiseTheme
import com.example.spendwise.ui.theme.TABULAR_FIGURES
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The one place money is rendered.
 *
 * Why this exists: before the UI pass, every screen formatted rupees by hand
 * (`"₹" + "%,.0f".format(x)`), which produced inconsistent grouping, no
 * semantic colour, and digits that shifted width as values changed.
 * `MoneyText` fixes all three:
 *
 *  - **Semantic colour** — pulls from `SpendwiseTheme.colors` so income/expense
 *    stay correct in light *and* dark theme instead of hardcoded greens/reds.
 *  - **Tabular figures** — `tnum` keeps digit columns from jiggling.
 *  - **Count-up** — value changes animate (`animateFloatAsState`), which is
 *    what makes balances feel alive when a transaction is saved.
 *
 * Sizes map onto the Outfit type scale: HERO (display), BALANCE (headline),
 * TITLE (title), BODY (body), LABEL (label).
 */
enum class MoneySemantic {
    /** Money coming in — green. */
    INCOME,

    /** Money going out — red. */
    EXPENSE,

    /** Between the user's own accounts — blue (money isn't lost). */
    TRANSFER,

    /** Informational / not yet classified — neutral ink. */
    NEUTRAL,

    /** Budget overrun, pending review, etc. — amber. */
    WARNING,
}

enum class MoneySize {
    /** Onboarding / big statement figures. */
    HERO,

    /** Account balance, budget total. */
    BALANCE,

    /** Card headings. */
    TITLE,

    /** Prominent inline figures (friend balances, section totals). */
    HEADING,

    /** List rows and summaries. */
    BODY,

    /** Chips, captions, inline metadata. */
    LABEL,
}

/** Rupee sign (U+20B9) — a constant so callers never hand-type it. */
const val RUPEE_SIGN = "\u20B9"

/**
 * Indian digit grouping — last three digits, then pairs of two:
 * `153250` -> `"1,53,250"`.
 *
 * Hand-rolled rather than `DecimalFormat("##,##,##0")`: multi-group-size
 * patterns behave differently across JDK/ICU implementations (the Android
 * runtime silently ignores the extra group, yielding `153250` with no
 * separators at all). This version is deterministic on every device and locale,
 * and is pinned by `MoneyFormatTest`.
 */
private fun groupIndian(value: Long): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits

    val tail = digits.takeLast(3)
    val head = digits.dropLast(3)
    val groupedHead = head.reversed().chunked(2).joinToString(",").reversed()
    return "$groupedHead,$tail"
}

/**
 * Formats paise using Indian digit grouping and drops the decimals when the
 * value is a whole rupee amount (₹1,53,250 instead of ₹1,53,250.00).
 * Negative values keep a leading minus; callers that want a semantic sign
 * should pass `showSign = true` to [MoneyText].
 */
fun formatPaise(amountPaise: Long): String {
    val sign = if (amountPaise < 0) "-" else ""
    val paise = abs(amountPaise)
    val rupees = paise / 100
    val remainder = paise % 100

    val body = if (remainder == 0L) {
        groupIndian(rupees)
    } else {
        "${groupIndian(rupees)}.${remainder.toString().padStart(2, '0')}"
    }
    return "$sign$RUPEE_SIGN$body"
}

/** Convenience for the parts of the app that still hold rupee [Double]s. */
fun formatRupees(amount: Double): String = formatPaise((amount * 100).roundToLong())

@Composable
private fun MoneySize.style(): TextStyle = when (this) {
    MoneySize.HERO -> MaterialTheme.typography.displayMedium.copy(
        fontWeight = FontWeight.ExtraBold
    )

    MoneySize.BALANCE -> MaterialTheme.typography.headlineLarge.copy(
        fontWeight = FontWeight.Bold
    )

    MoneySize.TITLE -> MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold
    )

    MoneySize.HEADING -> MaterialTheme.typography.headlineSmall.copy(
        fontWeight = FontWeight.Bold
    )

    MoneySize.BODY -> MaterialTheme.typography.titleSmall.copy(
        fontWeight = FontWeight.SemiBold
    )

    MoneySize.LABEL -> MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun MoneySemantic.color(): Color = when (this) {
    MoneySemantic.INCOME -> SpendwiseTheme.colors.income
    MoneySemantic.EXPENSE -> SpendwiseTheme.colors.expense
    MoneySemantic.TRANSFER -> SpendwiseTheme.colors.transfer
    MoneySemantic.WARNING -> SpendwiseTheme.colors.warning
    MoneySemantic.NEUTRAL -> MaterialTheme.colorScheme.onSurface
}

/**
 * Renders a paise amount with brand money styling.
 *
 * @param amountPaise value in paise (minor units) — never a float.
 * @param semantic drives the colour unless [color] is supplied explicitly.
 * @param size role on the type scale.
 * @param showSign prefixes `+`/`−` so direction survives without an icon.
 * @param animateChanges count-up animation when the value changes.
 */
@Composable
fun MoneyText(
    amountPaise: Long,
    modifier: Modifier = Modifier,
    semantic: MoneySemantic = MoneySemantic.NEUTRAL,
    size: MoneySize = MoneySize.BODY,
    showSign: Boolean = false,
    animateChanges: Boolean = true,
    color: Color? = null,
) {
    val animated by animateFloatAsState(
        targetValue = amountPaise.toFloat(),
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "MoneyText"
    )
    val value = if (animateChanges) animated.roundToLong() else amountPaise

    // formatPaise carries the minus for negatives; re-sign explicitly so a
    // rising balance reads "+₹500" and a falling one "−₹500" (a true minus
    // glyph, not a hyphen) when the caller asks for direction.
    val sign = when {
        value < 0L -> "\u2212"
        showSign && value > 0L -> "+"
        else -> ""
    }
    val magnitude = formatPaise(abs(value)).removePrefix(RUPEE_SIGN)

    Text(
        text = sign + RUPEE_SIGN + magnitude,
        modifier = modifier,
        style = size.style().copy(fontFeatureSettings = TABULAR_FIGURES),
        color = color ?: semantic.color(),
        maxLines = 1,
    )
}

/**
 * Renders an already-formatted money string (e.g. `"₹10,000"` coming out of a
 * mapper) with the same styling/semantics but no count-up — the string is
 * opaque, so there is nothing numeric to animate.
 */
@Composable
fun MoneyText(
    text: String,
    modifier: Modifier = Modifier,
    semantic: MoneySemantic = MoneySemantic.NEUTRAL,
    size: MoneySize = MoneySize.BODY,
    color: Color? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        style = size.style().copy(fontFeatureSettings = TABULAR_FIGURES),
        color = color ?: semantic.color(),
        maxLines = 1,
    )
}