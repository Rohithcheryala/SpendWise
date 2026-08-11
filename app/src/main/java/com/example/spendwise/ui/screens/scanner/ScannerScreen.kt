package com.example.spendwise.ui.screens.scanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.spendwise.ui.components.FriendAvatar

data class UpiContact(
    val id: Long,
    val name: String,
    val upiId: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    modifier: Modifier = Modifier
) {
    var upiInput by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<UpiContact?>(null) }
    var showPaymentSheet by remember { mutableStateOf(false) }
    var paymentSuccess by remember { mutableStateOf(false) }

    val recentContacts = remember {
        listOf(
            UpiContact(1, "Rahul Sharma", "rahul@okaxis"),
            UpiContact(2, "Priya Patel", "priya@okhdfcbank"),
            UpiContact(3, "Chai Spot", "chaispot@paytm"),
            UpiContact(4, "SuperMarket", "supermart@icici")
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Scan & Pay (UPI)", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.FlashOn, contentDescription = "Flash")
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Image, contentDescription = "Upload from Gallery")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Animated Scanner Viewport
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Scanning Laser Line Animation
                val transition = rememberInfiniteTransition(label = "laser")
                val translateY by transition.animateFloat(
                    initialValue = -90f,
                    targetValue = 90f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "laserY"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(2.dp)
                        .offset(y = translateY.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )

                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    modifier = Modifier.size(100.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Align QR code within frame to scan",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            // Quick Pay to Recent Contacts
            Text(
                text = "Recent Contacts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(recentContacts) { contact ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedContact = contact
                                showPaymentSheet = true
                            }
                            .padding(8.dp)
                    ) {
                        FriendAvatar(name = contact.name, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = contact.name.split(" ").first(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = contact.upiId.take(12),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Manual UPI ID Entry
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Pay via UPI ID or Mobile Number",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = upiInput,
                            onValueChange = { upiInput = it },
                            placeholder = { Text("e.g. name@upi or 9876543210") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (upiInput.isNotBlank()) {
                                    selectedContact = UpiContact(0, upiInput, upiInput)
                                    showPaymentSheet = true
                                }
                            },
                            enabled = upiInput.isNotBlank()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Pay")
                        }
                    }
                }
            }
        }
    }

    if (showPaymentSheet && selectedContact != null) {
        UpiPaymentBottomSheet(
            contact = selectedContact!!,
            onDismiss = {
                showPaymentSheet = false
                paymentSuccess = false
            },
            onPaySuccess = {
                paymentSuccess = true
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpiPaymentBottomSheet(
    contact: UpiContact,
    onDismiss: () -> Unit,
    onPaySuccess: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var isSuccess by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isSuccess) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(72.dp)
                )
                Text(
                    text = "Payment Successful!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF16A34A)
                )
                Text(
                    text = "Paid ₹$amount to ${contact.name}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FriendAvatar(name = contact.name, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(contact.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(contact.upiId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Add Note (Optional)") },
                    placeholder = { Text("Dinner split, Groceries, etc.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = {
                        isSuccess = true
                        onPaySuccess()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = amount.toDoubleOrNull()?.let { it > 0 } == true
                ) {
                    Text("Pay ₹${amount.ifBlank { "0" }}")
                }
            }
        }
    }
}