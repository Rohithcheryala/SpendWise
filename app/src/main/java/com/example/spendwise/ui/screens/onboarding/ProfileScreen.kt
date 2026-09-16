package com.example.spendwise.ui.screens.onboarding

import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.Icons
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spendwise.ui.theme.SpendwiseTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    onContinue: (String, Color) -> Unit,
    modifier: Modifier = Modifier
) {

    var name by remember { mutableStateOf("") }

    val categorical = SpendwiseTheme.categorical.swatches
    var selectedColor by remember { mutableStateOf(categorical.first()) }

    val colorOptions = categorical.take(6)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // Avatar with selected color
            val avatarBg by animateColorAsState(
                targetValue = selectedColor.copy(alpha = 0.15f),
                label = "avatar_bg"
            )
            val avatarTint by animateColorAsState(
                targetValue = selectedColor,
                label = "avatar_tint"
            )

            Surface(
                modifier = Modifier.size(96.dp),
                shape = CircleShape,
                color = avatarBg
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (name.isNotBlank()) {
                        Text(
                            text = name.trim().first().uppercaseChar().toString(),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = avatarTint
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Person,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = avatarTint
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "Personalize your app",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Set your display name and choose a color theme.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Your name") },
                placeholder = { Text("e.g. Rohith") },
                singleLine = true,
                shape = MaterialTheme.shapes.small
            )

            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Accent Color",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    colorOptions.forEach { color ->
                        val isSelected = color == selectedColor
                        val size by animateDpAsState(
                            targetValue = if (isSelected) 44.dp else 40.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "color_swatch_size"
                        )

                        Box(
                            modifier = Modifier
                                .size(size)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected)
                                        Modifier.border(
                                            3.dp,
                                            MaterialTheme.colorScheme.outline,
                                            CircleShape
                                        )
                                    else Modifier
                                )
                                .clickable { selectedColor = color },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { onContinue(name.trim(), selectedColor) },
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 0.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = selectedColor
                )
            ) {
                Text(
                    "Continue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ProfilePreview() {
    ProfileScreen(
        onContinue = { _: String, _: Color -> },
    )
}