package com.example.spendwise.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.spendwise.ui.theme.Dimens

/**
 * The chrome every input control is built from — the field counterpart of
 * [SpendwiseCard].
 *
 * Why this exists: the transaction form used to render the amount as an
 * `OutlinedTextField` (56dp floor, `surface` fill, **direction-coloured** focus
 * border) directly beside the date as a borderless `surfaceContainerHigh`
 * `DropdownField` (46dp, no border). Same row, same job, two design languages:
 * one box taller than the other, different fills, and a red hairline on the
 * amount that reads as a validation error rather than a focus state.
 *
 * One rule now, and it is the same rule [SpendwiseCard] uses for cards:
 *
 *   fill  → `surfaceContainerHigh` (a real tonal step off `background`)
 *   edge  → 1dp `outlineVariant` hairline
 *   shape → `shapes.small`
 *   size  → `heightIn(min = Dimens.fieldHeight)`, grows with font scale
 *
 * The hairline is not decoration: in the light theme `surfaceContainerHigh`
 * (`#EAEDF4`) sits at **1.005:1** against `background` (`#EBEEF4`), i.e. the
 * fields are invisible without it.
 *
 * Focus deliberately uses `primary` (indigo, the app's action lane) and never
 * the direction accent — indigo is far in hue from both income-green and
 * expense-red, so a focused field can't be mistaken for a money signal or an
 * error. Direction keeps its colour on the *money itself* (the ₹ prefix and
 * the amount digits), which is where it carries meaning.
 */
@Composable
fun SpendwiseField(
    modifier: Modifier = Modifier,
    /** Field name rendered above the box in the app's one label style. */
    label: String? = null,
    /** Swaps the hairline to `primary`. Text fields track their own focus. */
    focused: Boolean = false,
    minHeight: Dp = Dimens.fieldHeight,
    contentPadding: PaddingValues = SpendwiseFieldDefaults.contentPadding,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    /**
     * Makes the whole box a tap target — including the padding ring, which a
     * `BasicTextField` alone does not cover. `null` for a non-interactive box.
     */
    onClick: (() -> Unit)? = null,
    /**
     * `LocalIndication.current` (ripple) by default, which is right for a
     * dropdown — it opens something. Text-entry fields pass `null`: their
     * feedback is the focus hairline, and a ripple reads as a button press.
     */
    indication: Indication? = LocalIndication.current,
    content: @Composable RowScope.() -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (focused) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        label = "field_border"
    )

    val shape = MaterialTheme.shapes.small
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.xs)
    ) {
        if (label != null) {
            FieldLabel(text = label)
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = indication,
                            onClick = onClick
                        )
                    } else {
                        Modifier
                    }
                ),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Row(
                // `heightIn` then `padding` → the min applies to the *box*, so
                // every field is exactly Dimens.fieldHeight tall whether its
                // value is a 26dp amount or a 22dp date.
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .padding(contentPadding),
                verticalAlignment = verticalAlignment,
                content = content
            )
        }
    }
}

/** Spacing tokens for [SpendwiseField], so call sites stop eyeballing padding. */
object SpendwiseFieldDefaults {
    /** Single-line fields: 16dp gutter, 8dp above/below the value. */
    val contentPadding = PaddingValues(horizontal = Dimens.lg, vertical = Dimens.sm)

    /** Multi-line fields: roomier vertical rhythm, same left edge as the above. */
    val multilineContentPadding = PaddingValues(horizontal = Dimens.lg, vertical = Dimens.md)
}

/**
 * The one label style for a form control. Before this, "Account"/"Category"
 * were `labelMedium` while the note's "Note" was a bold `titleMedium`, so one
 * label shouted and the rest whispered.
 */
@Composable
fun FieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium
    )
}
