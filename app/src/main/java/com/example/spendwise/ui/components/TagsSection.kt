package com.example.spendwise.ui.components


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spendwise.ui.screens.transaction.TagUiModel
import androidx.compose.foundation.layout.ExperimentalLayoutApi

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagSection(
    tags: List<TagUiModel>,
    onRemove: (String) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        tags.forEach { tag ->

            InputChip(
                selected = true,
                onClick = { onRemove(tag.id) },
                label = {
                    Text(tag.label)
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Remove ${tag.label}",
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            )
        }

        AssistChip(
            onClick = onAddClick,
            label = {
                Text("Add Tag")
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null
                )
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TagSectionPreview() {
    TagSection(
        tags = listOf(
            TagUiModel("1", "rahul"),
            TagUiModel("2", "upi"),
            TagUiModel("3", "food")
        ),
        onRemove = {},
        onAddClick = {}
    )
}