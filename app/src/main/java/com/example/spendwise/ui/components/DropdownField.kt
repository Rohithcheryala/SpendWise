package com.example.spendwise.ui.components

import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.spendwise.ui.theme.Dimens

/**
 * A tap-to-choose field. Now a thin wrapper over [SpendwiseField], so it is
 * pixel-identical to the amount box it sits beside: same fill, same hairline,
 * same 48dp row, same label style.
 *
 * [leadingIcon] / [trailingIcon] exist so a caller can say what the field
 * *means* without leaving the shared chrome — a date swaps the chevron for a
 * calendar, an account could show its bank mark.
 */
@Composable
fun DropdownField(
    label: String?,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = Icons.Rounded.KeyboardArrowDown,
) {
    SpendwiseField(
        modifier = modifier,
        label = label,
        onClick = if (enabled) onClick else null,
    ) {
        val displayText = value.ifBlank { placeholder.orEmpty() }
        val isPlaceholder = value.isBlank() || !enabled
        val textColor = if (isPlaceholder) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        }

        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(Dimens.md))
        }

        Text(
            text = displayText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            fontWeight = if (isPlaceholder) FontWeight.Normal else FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        if (trailingIcon != null) {
            Spacer(Modifier.width(Dimens.sm))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
