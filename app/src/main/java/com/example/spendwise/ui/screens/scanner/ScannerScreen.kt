package com.example.spendwise.ui.screens.scanner

import androidx.compose.material.icons.Icons
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.MotionEvent
import android.view.Surface
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Send
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spendwise.ui.theme.Dimens
import com.example.spendwise.ui.theme.SpendwiseTheme
import com.example.spendwise.ui.components.DropdownField
import com.example.spendwise.ui.components.FriendAvatar
import com.example.spendwise.ui.screens.transactiondetail.DropdownOption
import com.example.spendwise.ui.screens.transactiondetail.TagUiModel
import com.example.spendwise.viewmodel.ScannerViewModel
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.hypot
import com.example.spendwise.ui.components.SpendwiseCard

/** The NPCI-standard UPI deep-link action — no SDK constant exists for it. */
private const val ACTION_UPI_PAY = "android.intent.action.UPI_PAY"


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

    // ── live vision feedback ──
    // On-screen pixel size of the camera viewport (drives the shared ViewPort
    // so analyzer coordinates map 1:1 onto the screen), the QR corner points
    // in screen space (drives the detection brackets), and the last
    // tap-to-focus position (drives the focus pulse ring).
    var viewSizePx by remember { mutableStateOf(IntSize.Zero) }
    var qrCorners by remember { mutableStateOf<List<FloatArray>?>(null) }
    var focusPulse by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    // Missed detections happen a frame at a time — hold the last sighting for
    // a beat so the brackets don't blink, then fall back to the scan sweep.
    LaunchedEffect(qrCorners, payTarget) {
        if (qrCorners != null && payTarget == null) {
            delay(350)
            qrCorners = null
        }
    }

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

    fun dismissPayment() {
        payTarget = null
        sheetError = null
        viewModel.clearError()
        torchOn = false
        camera?.cameraControl?.enableTorch(false)
    }

    // The payment form is a plain full-screen overlay, NOT a ModalBottomSheet:
    // sheets intercept back and dismiss themselves, so the user's habitual
    // back-gesture "just close the keyboard" killed the whole payment. Here
    // back follows normal screen semantics — IME first, cancel second.
    BackHandler(enabled = payTarget != null) { dismissPayment() }

    fun launchUpiApp() {
        val target = payTarget ?: return
        if (amount.toDoubleOrNull() == null || amount.toDouble() <= 0.0) {
            sheetError = "Enter a valid amount"
            return
        }
        // No account requirement here: a payment can fail at the last step and
        // be retried from a different account, so the landing bank SMS — which
        // names the account that actually paid — decides on merge.
        val intent = Intent(ACTION_UPI_PAY).also {
            it.data = buildUpiUri(target.vpa, target.name, amount, note)
        }
        // Chooser + NO resolveActivity() pre-check (naa's UpiPayPlugin): on
        // Android 11+ package visibility makes resolveActivity return null even
        // with GPay/PhonePe/Paytm installed, which wrongly hard-failed every
        // payment. The chooser is system-mediated; a genuine absence surfaces
        // as ActivityNotFoundException.
        try {
            payLauncher.launch(Intent.createChooser(intent, "Pay with"))
        } catch (_: android.content.ActivityNotFoundException) {
            sheetError = "No UPI app found on this device"
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
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
                            imageVector = if (torchOn) Icons.Filled.FlashOff else Icons.Rounded.FlashOn,
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
                .padding(horizontal = Dimens.screenGutter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // ── live camera viewport with the QR decode pipeline ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { viewSizePx = it }
                    .clip(MaterialTheme.shapes.large)
                    .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    CameraPreviewWithQrAnalysis(
                        viewSizePx = viewSizePx,
                        onCameraBound = { camera = it },
                        scanningPaused = payTarget != null,
                        onQrCorners = { qrCorners = it },
                        onFocusAt = { x, y -> focusPulse = x to y },
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

                // Live vision feedback over the preview.
                QrScanOverlay(
                    corners = qrCorners,
                    decoded = payTarget != null,
                    modifier = Modifier.fillMaxSize(),
                )

                focusPulse?.let { (x, y) ->
                    FocusPulseRing(x = x, y = y, modifier = Modifier.fillMaxSize())
                }

                ScannerStatusPill(
                    text = when {
                        payTarget != null -> "QR captured — review details"
                        qrCorners != null -> "QR detected — reading…"
                        else -> "Point your camera at a UPI QR code"
                    },
                    highlight = qrCorners != null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                )

                statusMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── manual UPI ID entry (no QR needed) ──
            SpendwiseCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Dimens.cardPadding)) {
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
                            Icon(Icons.Rounded.Send, contentDescription = "Pay")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── the payment overlay ──
    payTarget?.let { target ->
        PaymentOverlay(
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
            onDismiss = { dismissPayment() },
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
    viewSizePx: IntSize,
    onCameraBound: (Camera) -> Unit,
    scanningPaused: Boolean,
    onQrCorners: (List<FloatArray>?) -> Unit,
    onFocusAt: (Float, Float) -> Unit,
    onQrDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // The analyzer lambda lives across recompositions — always read the
    // CURRENT paused flag, not the one from the first composition.
    val currentPaused by rememberUpdatedState(scanningPaused)
    // Analyzer threads read these without recomposition — keep them hot.
    val currentViewSize by rememberUpdatedState(viewSizePx)
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
                    // Show where the camera just focused — the user asked.
                    onFocusAt(event.x, event.y)
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
    // Keyed on the viewport's pixel size: the shared ViewPort must match the
    // on-screen view box, so (re)bind whenever it is first laid out or resized.
    LaunchedEffect(viewSizePx) {
        if (viewSizePx.width < 2 || viewSizePx.height < 2) return@LaunchedEffect
        runCatching {
            val provider = ProcessCameraProvider.getInstance(context).await(context)
            val rotation = runCatching { previewView.display.rotation }
                .getOrDefault(Surface.ROTATION_0)
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            // Baked rotation: the analyzer image arrives display-oriented, so
            // its width/height (and ML Kit corner points) are directly mappable
            // to screen coordinates without extra rotation math.
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageRotationEnabled(true)
                .setTargetRotation(rotation)
                .build()
            // Shared ViewPort = the classic ML Kit scanner trick: preview and
            // analysis are cropped to the SAME field of view, so a QR's corner
            // points land exactly where the QR appears on screen.
            val useCaseGroup = UseCaseGroup.Builder()
                .setViewPort(
                    // Aspect ratio of the on-screen view box: preview and
                    // analysis end up cropped to the same FOV as the user sees.
                    ViewPort.Builder(
                        Rational(viewSizePx.width, viewSizePx.height),
                        rotation,
                    ).build()
                )
                .addUseCase(preview)
                .addUseCase(imageAnalysis)
                .build()

            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                useCaseGroup,
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
                            val barcode = barcodes.firstOrNull()
                            val corners = barcode?.cornerPoints
                            if (corners != null && corners.size == 4) {
                                // Map image-space corners onto the screen with
                                // FILL_CENTER math (aspect ratios match thanks
                                // to the shared ViewPort, so crop is minimal).
                                val view = currentViewSize
                                val rot = proxy.imageInfo.rotationDegrees
                                val imgW = if (rot % 180 == 90) proxy.height else proxy.width
                                val imgH = if (rot % 180 == 90) proxy.width else proxy.height
                                if (imgW > 0 && imgH > 0 && view.width > 0 && view.height > 0) {
                                    val scale = maxOf(
                                        view.width / imgW.toFloat(),
                                        view.height / imgH.toFloat(),
                                    )
                                    val dx = (view.width - imgW * scale) / 2f
                                    val dy = (view.height - imgH * scale) / 2f
                                    onQrCorners(
                                        corners.map {
                                            floatArrayOf(it.x * scale + dx, it.y * scale + dy)
                                        }
                                    )
                                }
                            } else {
                                onQrCorners(null)
                            }
                            barcode?.rawValue?.let(onQrDetected)
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

/**
 * Live vision feedback, ML Kit-scanner style. While nothing is detected a
 * static rounded reticle sweeps a line (visible proof the camera is alive);
 * the moment a QR is spotted the brackets snap onto its real corners on the
 * preview, turning green when the code has been captured.
 */

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






