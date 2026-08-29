package com.example.spendwise.ui.screens.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spendwise.ui.theme.SpendwiseTheme
import com.example.spendwise.ui.components.DropdownField
import com.example.spendwise.ui.components.FriendAvatar
import com.example.spendwise.ui.screens.transaction.DropdownOption
import com.example.spendwise.ui.screens.transaction.TagUiModel
import com.example.spendwise.viewmodel.ScannerViewModel
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** The NPCI-standard UPI deep-link action — no SDK constant exists for it. */
private const val ACTION_UPI_PAY = "android.intent.action.UPI_PAY"

/** A decoded UPI payment target: who to pay, and any amount/note the QR carried. */
data class UpiTarget(
    val name: String,
    val vpa: String,
    /** Amount pre-encoded in the QR ("am=" param), null when the QR leaves it open. */
    val qrAmount: String?,
    /** Note pre-encoded in the QR ("tn=" param). */
    val qrNote: String?,
)

/** `upi://pay?pa=...&pn=...&am=...&tn=...` -> [UpiTarget], null for non-UPI QRs. */
fun parseUpiQr(raw: String): UpiTarget? {
    if (!raw.startsWith("upi://", ignoreCase = true)) return null
    val parsed = Uri.parse(raw)
    val payeeAddress = parsed.getQueryParameter("pa")?.takeIf { it.isNotBlank() } ?: return null
    return UpiTarget(
        name = parsed.getQueryParameter("pn").orEmpty(),
        vpa = payeeAddress,
        qrAmount = parsed.getQueryParameter("am")?.takeIf { it.toDoubleOrNull() != null },
        qrNote = parsed.getQueryParameter("tn")?.takeIf { it.isNotBlank() },
    )
}

private fun buildUpiUri(vpa: String, name: String, amount: String, note: String): Uri =
    Uri.parse("upi://pay").buildUpon()
        .appendQueryParameter("pa", vpa)
        .appendQueryParameter("pn", name.ifBlank { vpa })
        .appendQueryParameter("am", amount)
        .appendQueryParameter("cu", "INR")
        .apply { if (note.isNotBlank()) appendQueryParameter("tn", note) }
        .build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    modifier: Modifier = Modifier,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // ── camera + scan state ──
    var torchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var payTarget by remember { mutableStateOf<UpiTarget?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var lastScanAt by remember { mutableStateOf(0L) }

    // ── payment-sheet form state (hoisted so the pay-result callback sees it) ──
    var upiInput by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(listOf<TagUiModel>()) }
    var newTag by remember { mutableStateOf("") }
    var selectedAccount by remember { mutableStateOf<DropdownOption?>(null) }
    var selectedCategory by remember { mutableStateOf<DropdownOption?>(null) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var sheetError by remember { mutableStateOf<String?>(null) }
    var lastPayee by remember { mutableStateOf("") }

    // A fresh target (QR or manual) prefills the sheet; the chosen account
    // stays sticky — most payments leave the same account.
    LaunchedEffect(payTarget) {
        val target = payTarget ?: return@LaunchedEffect
        amount = target.qrAmount.orEmpty()
        note = target.qrNote.orEmpty()
        tags = emptyList()
        newTag = ""
        selectedCategory = null
        sheetError = null
    }

    /**
     * Fire the UPI intent (the user's chosen UPI app completes the payment),
     * then — regardless of result, since UPI result payloads are unreliable —
     * record the user's entered intent as a buffer entry. The bank SMS either
     * merges + auto-confirms it, or the user reviews it in the inbox.
     */
    val payLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val target = payTarget ?: return@rememberLauncherForActivityResult
        lastPayee = target.name.ifBlank { target.vpa }
        viewModel.recordQrPayment(
            payeeName = target.name,
            vpa = target.vpa,
            amount = amount,
            accountId = selectedAccount?.id?.toLongOrNull(),
            categoryId = selectedCategory?.id?.toLongOrNull(),
            tags = tags.map { it.label },
            note = note,
            upiUri = buildUpiUri(target.vpa, target.name, amount, note).toString(),
        )
        payTarget = null
        torchOn = false
    }

    fun launchUpiApp() {
        val target = payTarget ?: return
        if (amount.toDoubleOrNull() == null || amount.toDouble() <= 0.0) {
            sheetError = "Enter a valid amount"
            return
        }
        if (selectedAccount == null) {
            sheetError = "Select the account to pay from"
            return
        }
        val intent = Intent(ACTION_UPI_PAY).also {
            it.data = buildUpiUri(target.vpa, target.name, amount, note)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            sheetError = "No UPI app found on this device"
            return
        }
        payLauncher.launch(intent)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Scan & Pay (UPI)", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = {
                            torchOn = !torchOn
                            camera?.cameraControl?.enableTorch(torchOn)
                        },
                        enabled = camera != null
                    ) {
                        Icon(
                            imageVector = if (torchOn) Icons.Filled.FlashOff else Icons.Filled.FlashOn,
                            contentDescription = "Flash"
                        )
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

            // ── live camera viewport with the QR decode pipeline ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    CameraPreviewWithQrAnalysis(
                        onCameraBound = { camera = it },
                        scanningPaused = payTarget != null,
                        onQrDetected = { raw ->
                            val now = System.currentTimeMillis()
                            if (now - lastScanAt < 2_000) return@CameraPreviewWithQrAnalysis
                            lastScanAt = now
                            val target = parseUpiQr(raw)
                            if (target == null) {
                                statusMessage = "QR detected, but it isn't a UPI payment code"
                            } else {
                                statusMessage = null
                                payTarget = target
                            }
                        }
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "Camera permission needed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Spendwise scans UPI QR codes through the camera to pre-fill payments.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Allow camera")
                        }
                    }
                }

                statusMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── manual UPI ID entry (no QR needed) ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Pay via UPI ID or mobile number",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                                upiInput.takeIf { it.isNotBlank() }?.let { input ->
                                    payTarget = UpiTarget(
                                        name = input,
                                        vpa = input,
                                        qrAmount = null,
                                        qrNote = null,
                                    )
                                }
                            },
                            enabled = upiInput.isNotBlank()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Pay")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── the comprehensive payment sheet ──
    payTarget?.let { target ->
        PaymentSheet(
            target = target,
            amount = amount,
            onAmountChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
            note = note,
            onNoteChange = { note = it },
            tags = tags,
            onRemoveTag = { id -> tags = tags.filterNot { it.id == id } },
            newTag = newTag,
            onNewTagChange = { newTag = it },
            onAddTag = {
                val label = newTag.trim()
                if (label.isNotEmpty() && tags.none { it.id == label }) {
                    tags = tags + TagUiModel(label, label)
                }
                newTag = ""
            },
            accounts = uiState.accounts,
            selectedAccount = selectedAccount,
            onAccountClick = { showAccountPicker = true },
            categories = uiState.categories,
            selectedCategory = selectedCategory,
            onCategoryClick = { showCategoryPicker = true },
            isSaving = uiState.isSaving,
            error = sheetError ?: uiState.error,
            onDismiss = {
                payTarget = null
                sheetError = null
                viewModel.clearError()
                torchOn = false
                camera?.cameraControl?.enableTorch(false)
            },
            onPay = { launchUpiApp() }
        )
    }

    if (showAccountPicker) {
        OptionPickerSheet(
            title = "Pay from account",
            options = uiState.accounts,
            selected = selectedAccount,
            onSelect = {
                selectedAccount = it
                showAccountPicker = false
            },
            onDismiss = { showAccountPicker = false }
        )
    }

    if (showCategoryPicker) {
        OptionPickerSheet(
            title = "Category",
            options = uiState.categories,
            selected = selectedCategory,
            onSelect = {
                selectedCategory = it
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }

    // ── recorded-to-buffer confirmation ──
    if (uiState.saved) {
        SavedConfirmationSheet(
            amount = amount,
            payee = lastPayee,
            onDone = { viewModel.dismissSaved() }
        )
    }
}

/**
 * CameraX preview bound to the back camera plus an ML Kit QR analyzer. The
 * analyzer runs on a background executor and only decodes while the payment
 * sheet is closed ([scanningPaused]) — one QR can't re-trigger behind the sheet.
 */
@Composable
private fun CameraPreviewWithQrAnalysis(
    onCameraBound: (Camera) -> Unit,
    scanningPaused: Boolean,
    onQrDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // The analyzer lambda lives across recompositions — always read the
    // CURRENT paused flag, not the one from the first composition.
    val currentPaused by rememberUpdatedState(scanningPaused)
    // Camera handle for tap-to-focus; captured via MutableState so the touch
    // listener (set once) always reads the latest binding.
    val boundCamera = remember { mutableStateOf<Camera?>(null) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    // Tap anywhere to focus + meter there — the camera's default AF hunt is
    // unreliable for QR-sized targets at close range. The listener reads the
    // camera handle via MutableState, so it stays correct across rebinds.
    DisposableEffect(previewView) {
        previewView.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                boundCamera.value?.let { cam ->
                    val factory = previewView.meteringPointFactory
                    val point = factory.createPoint(event.x, event.y)
                    cam.cameraControl.startFocusAndMetering(
                        FocusMeteringAction.Builder(
                            point,
                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                    )
                }
                view.performClick()
            }
            true
        }
        onDispose {
            previewView.setOnTouchListener(null)
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            barcodeScanner.close()
            analysisExecutor.shutdown()
        }
    }

    // ProcessCameraProvider.getInstance returns a ListenableFuture; bridge it
    // into coroutines without pulling in the guava integration artifact.
    LaunchedEffect(Unit) {
        runCatching {
            val provider = ProcessCameraProvider.getInstance(context).await(context)
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            runCatching { imageAnalysis.targetRotation = previewView.display.rotation }

            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis,
            )
            boundCamera.value = camera
            onCameraBound(camera)

            // Continuous focus pinned to the frame center: without this the
            // AF engine hunts across the whole scene and QRs sit out of focus.
            runCatching {
                val center = SurfaceOrientedMeteringPointFactory(1f, 1f)
                    .createPoint(0.5f, 0.5f)
                camera.cameraControl.startFocusAndMetering(
                    FocusMeteringAction.Builder(center, FocusMeteringAction.FLAG_AF)
                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build()
                )
            }

            imageAnalysis.setAnalyzer(analysisExecutor) { proxy ->
                // The frame buffer must stay open until ML Kit has consumed it:
                // closing the proxy right after process() invalidates the image
                // and detection silently never fires. Conversely, with
                // KEEP_ONLY_LATEST an un-closed proxy stalls the whole pipeline,
                // so EVERY exit path must close it — either immediately (skipped
                // frame) or on detection completion.
                try {
                    if (currentPaused) {
                        runCatching { proxy.close() }
                        return@setAnalyzer
                    }
                    val mediaImage = proxy.image
                    if (mediaImage == null) {
                        runCatching { proxy.close() }
                        return@setAnalyzer
                    }
                    val input = InputImage.fromMediaImage(
                        mediaImage,
                        proxy.imageInfo.rotationDegrees,
                    )
                    barcodeScanner.process(input)
                        .addOnSuccessListener { barcodes ->
                            barcodes.firstOrNull()?.rawValue?.let(onQrDetected)
                        }
                        .addOnCompleteListener { proxy.close() }
                } catch (_: Exception) {
                    // A failed frame must never kill the analyzer.
                    runCatching { proxy.close() }
                }
            }
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** Await a ListenableFuture without the guava-coroutines integration module. */
private suspend fun <T> ListenableFuture<T>.await(context: android.content.Context): T =
    suspendCancellableCoroutine { cont ->
        addListener(
            {
                try {
                    cont.resume(get())
                } catch (e: Exception) {
                    cont.cancel(e)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
        cont.invokeOnCancellation { cancel(false) }
    }

/**
 * The full payment sheet: payee identity, amount, the account the money leaves,
 * an optional category (written as a real category leg, not left unclassified),
 * tags, and a note. "Pay" hands off to the user's UPI app; on return the intent
 * is recorded as a buffer entry.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PaymentSheet(
    target: UpiTarget,
    amount: String,
    onAmountChange: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    tags: List<TagUiModel>,
    onRemoveTag: (String) -> Unit,
    newTag: String,
    onNewTagChange: (String) -> Unit,
    onAddTag: () -> Unit,
    accounts: List<DropdownOption>,
    selectedAccount: DropdownOption?,
    onAccountClick: () -> Unit,
    categories: List<DropdownOption>,
    selectedCategory: DropdownOption?,
    onCategoryClick: () -> Unit,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onPay: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Payee identity.
            Row(verticalAlignment = Alignment.CenterVertically) {
                FriendAvatar(name = target.name.ifBlank { target.vpa }, modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = target.name.ifBlank { target.vpa },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = target.vpa,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChange,
                label = { Text("Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            DropdownField(
                label = "Pay from account",
                value = selectedAccount?.label.orEmpty(),
                placeholder = "Select account",
                onClick = onAccountClick
            )

            DropdownField(
                label = "Category (optional)",
                value = selectedCategory?.label.orEmpty(),
                placeholder = "Uncategorised",
                onClick = onCategoryClick
            )
            // Tags: chips + inline add field.
            if (tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = { onRemoveTag(tag.id) },
                            label = { Text(tag.label) },
                            trailingIcon = {
                                Icon(Icons.Outlined.Close, contentDescription = "Remove ${tag.label}")
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = newTag,
                onValueChange = onNewTagChange,
                label = { Text("Add tag (e.g. trip, work)") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = onAddTag, enabled = newTag.isNotBlank()) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add tag")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                label = { Text("Note (optional)") },
                placeholder = { Text("Dinner split, groceries, rent…") },
                modifier = Modifier.fillMaxWidth()
            )

            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = onPay,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text("Pay ₹${amount.ifBlank { "0" }} via UPI app")
            }
        }
    }
}

/** Simple bottom-sheet radio list for account/category selection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionPickerSheet(
    title: String,
    options: List<DropdownOption>,
    selected: DropdownOption?,
    onSelect: (DropdownOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            if (options.isEmpty()) {
                Text(
                    text = "Nothing available yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
            options.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option) }
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (option == selected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
/** Post-payment confirmation: the intent was recorded, the bank SMS will finish it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedConfirmationSheet(
    amount: String,
    payee: String,
    onDone: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDone, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = SpendwiseTheme.colors.income,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Recorded ₹${amount.ifBlank { "0" }} to $payee",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Saved to your Buffer Inbox. When the bank SMS arrives it merges " +
                    "with this entry and confirms automatically — or review it manually.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}





