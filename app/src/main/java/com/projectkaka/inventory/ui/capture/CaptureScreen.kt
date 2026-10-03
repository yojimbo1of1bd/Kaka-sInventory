package com.projectkaka.inventory.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    isInitialDocumentMode: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: CaptureViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permCamera by viewModel.permCamera.collectAsStateWithLifecycle()
    val hasSeenDocMode by viewModel.hasSeenDocMode.collectAsStateWithLifecycle()

    var showMetadataDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isInitialDocumentMode) {
        viewModel.setDocumentMode(isInitialDocumentMode)
    }

    LaunchedEffect(state.isDocumentSaved) {
        if (state.isDocumentSaved) {
            viewModel.resetDocumentSaved()
            onBack()
        }
    }

    var hasOsPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasOsPermission = granted }

    LaunchedEffect(permCamera) {
        if (permCamera && !hasOsPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (!permCamera) {
            PermissionDisabledPrompt(
                message = "Camera access is disabled in App Settings.",
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (hasOsPermission) {
            CameraViewfinder(
                isDocumentMode = state.isDocumentMode,
                onPhotoCaptured = { file -> viewModel.onPhotoCaptured(file) }
            )
        } else {
            PermissionPrompt(
                onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // --- Document Guide Overlay in Document Mode ---
        if (state.isDocumentMode && hasOsPermission && permCamera) {
            DocumentFrameGuide(modifier = Modifier.fillMaxSize())
        }

        // --- Top Bar: Back + Mode Indicator + Page/Burst Counter ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            if (state.isDocumentMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xCC0D1117))
                        .border(1.dp, Color(0xFF58A6FF), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = Color(0xFF58A6FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Page ${state.documentPages.size + 1}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                if (state.documentPages.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF238636))
                            .clickable { showMetadataDialog = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Done (${state.documentPages.size})",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.width(40.dp))
                }
            } else {
                if (state.capturedCount > 0) {
                    Text(
                        text = "Captured: ${state.capturedCount}",
                        color = Color(0xFFF1C40F),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x99000000))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                } else {
                    Spacer(Modifier.width(40.dp))
                }
            }
        }

        // --- Bottom Thumbnail Reel for Document Pages ---
        if (state.isDocumentMode && state.documentPages.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 200.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(state.documentPages) { index, path ->
                    Box(
                        modifier = Modifier
                            .size(56.dp, 72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x99000000))
                            .border(1.5.dp, Color(0xFF58A6FF), RoundedCornerShape(8.dp))
                    ) {
                        AsyncImage(
                            model = File(path),
                            contentDescription = "Page ${index + 1}",
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .background(Color(0xCC000000), RoundedCornerShape(bottomEnd = 6.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "P${index + 1}",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0xCCFF0000))
                                .clickable { viewModel.removePage(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }

        // --- Bottom Status + Hint ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val message = when {
                state.error != null -> state.error!!
                state.isProcessing -> if (state.isDocumentMode) "Processing page to 95% WebP…" else "Compressing to WebP…"
                state.lastMessage != null -> state.lastMessage!!
                state.isDocumentMode -> if (state.documentPages.isEmpty()) "Tap shutter for Page 1" else "Tap shutter for Page ${state.documentPages.size + 1} or tap Done"
                else -> "Tap the shutter. Camera stays open for the next item."
            }
            Text(
                text = message,
                color = if (state.error != null) Color(0xFFE74C3C) else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        // --- First-time Document Mode Onboarding Overlay ---
        if (state.isDocumentMode && !hasSeenDocMode) {
            DocumentModeOnboardingDialog(
                onDismiss = { viewModel.dismissDocModeOnboarding() }
            )
        }

        // --- Metadata Dialog when User Taps Done ---
        if (showMetadataDialog) {
            DocumentMetadataDialog(
                pageCount = state.documentPages.size,
                onDismiss = { showMetadataDialog = false },
                onSave = { title, docType, issueDate, expiryDate, notes ->
                    viewModel.saveDocument(title, docType, issueDate, expiryDate, notes)
                    showMetadataDialog = false
                }
            )
        }
    }
}

/**
 * CameraX viewfinder with dynamic quality mode based on document vs item capture.
 */
@Composable
private fun CameraViewfinder(
    isDocumentMode: Boolean,
    onPhotoCaptured: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor: Executor = remember { ContextCompat.getMainExecutor(context) }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    val imageCapture = remember(isDocumentMode) {
        ImageCapture.Builder()
            .setCaptureMode(
                if (isDocumentMode) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            )
            .build()
    }

    DisposableEffect(lifecycleOwner, isDocumentMode) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            runCatching {
                provider?.unbindAll()
                provider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            }
        }, executor)

        onDispose { runCatching { provider?.unbindAll() } }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        ShutterButton(
            onClick = {
                val scratchDir = File(context.cacheDir, "kaka_scratch").apply { mkdirs() }
                val scratch = File(scratchDir, "shot_${System.currentTimeMillis()}.jpg")
                val options = ImageCapture.OutputFileOptions.Builder(scratch).build()

                imageCapture.takePicture(
                    options,
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            onPhotoCaptured(scratch)
                        }
                        override fun onError(exception: ImageCaptureException) {
                            if (scratch.exists()) scratch.delete()
                        }
                    }
                )
            },
            isDocumentMode = isDocumentMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 110.dp)
        )
    }
}

@Composable
private fun DocumentFrameGuide(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 90.dp)
            .border(1.5.dp, Color(0x6658A6FF), RoundedCornerShape(16.dp))
    )
}

@Composable
private fun ShutterButton(
    onClick: () -> Unit,
    isDocumentMode: Boolean,
    modifier: Modifier = Modifier
) {
    val buttonColor = if (isDocumentMode) Color(0xFF58A6FF) else Color(0xFFF1C40F)
    Box(
        modifier = modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .background(buttonColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isDocumentMode) Icons.Default.Description else Icons.Default.CameraAlt,
            contentDescription = "Capture",
            tint = Color(0xFF0D1117),
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun DocumentModeOnboardingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF58A6FF), modifier = Modifier.size(36.dp))
        },
        title = {
            Text("Document Mode", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Welcome to multi-page document scanning!",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "• Tap the shutter for Page 1, Page 2, Page 3 sequentially.\n" +
                           "• Saved at ultra-crisp 95% quality so text remains legible.\n" +
                           "• When all pages are photographed, tap 'Done' to save.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF58A6FF))
            ) {
                Text("Got It", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun DocumentMetadataDialog(
    pageCount: Int,
    onDismiss: () -> Unit,
    onSave: (title: String, docType: String, issueDate: Long, expiryDate: Long?, notes: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("PRESCRIPTION") }
    var notes by remember { mutableStateOf("") }

    val docTypes = listOf("PRESCRIPTION", "WARRANTY", "RECEIPT", "ID_COPY", "CONTRACT", "OTHER")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Save Document ($pageCount page${if (pageCount > 1) "s" else ""})", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Document Title") },
                    placeholder = { Text("e.g. Paracetamol Rx, TV Warranty") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text("Document Type", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(docTypes.size) { idx ->
                        val type = docTypes[idx]
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.replace("_", " "), fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("Dosage, terms, serial number...") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        title.ifBlank { "Document ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}" },
                        selectedType,
                        System.currentTimeMillis(),
                        null,
                        notes
                    )
                },
                enabled = true
            ) {
                Text("Save to Vault", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Camera access is required to capture items and documents.",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Grant permission",
            color = Color(0xFF1E242B),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF1C40F))
                .clickable { onGrant() }
                .padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun PermissionDisabledPrompt(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.CameraAlt,
            contentDescription = null,
            tint = Color(0xFFE74C3C),
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Enable it in App Settings to use this feature.",
            color = Color.Gray,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
