package com.example.spendwise.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.example.spendwise.ui.theme.Dimens
import com.example.spendwise.ui.theme.SpendwiseTheme

/**
 * Free-form note on a transaction — deliberately the only field on the form
 * that grows, because a note is prose ("split with Rohith, he'll pay me back
 * next week") and a 500-character limit is not a one-line limit.
 *
 * Two things used to block that:
 *  - `ImeAction.Done` on a multi-line field makes the Enter key *submit*
 *    instead of inserting a newline, so the box could never hold a second
 *    line. The IME action is now the default, which is what gives Enter back
 *    to the newline.
 *  - the label was a bold `titleMedium` while every other field label was a
 *    quiet `labelMedium`, so it read as a section header rather than a field.
 *
 * The character counter stays out of the way until it matters (last 100
 * characters) instead of sitting under the box as a permanent "0/500".
 */
@Composable
fun NotesSection(
    note: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxLength: Int = 500,
    minLines: Int = 3,
    maxLines: Int = 8,
) {
    var isFocused by remember { mutableStateOf(false) }
    val nearLimit = note.length >= maxLength - 100

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm)
    ) {
        SpendwiseField(
            label = "Note",
            focused = isFocused,
            contentPadding = SpendwiseFieldDefaults.multilineContentPadding,
            // Top-aligned: a multi-line value must start where the caret does.
            verticalAlignment = Alignment.Top,
            // No ripple: this is text entry, not a button.
            indication = null,
        ) {
            BasicTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused },
                value = note,
                onValueChange = { if (it.length <= maxLength) onValueChange(it) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                minLines = minLines,
                maxLines = maxLines,
                // Capitalisation only — no `imeAction`, so Enter inserts a
                // newline (see the class doc above).
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (note.isEmpty()) {
                            Text(
                                text = "Add a note",
                                style = MaterialTheme.typography.bodyLarge,
                                color = SpendwiseTheme.text.tertiary
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        if (nearLimit) {
            Text(
                text = "${note.length}/$maxLength",
                style = MaterialTheme.typography.labelSmall,
                color = if (note.length >= maxLength) {
                    MaterialTheme.colorScheme.error
                } else {
                    SpendwiseTheme.colors.warning
                },
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotesSectionPreview() {
    NotesSection(
        note = "Lunch with friends",
        onValueChange = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun NotesSectionEmptyPreview() {
    NotesSection(
        note = "",
        onValueChange = {}
    )
}
