package com.example.smartblindstick

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * ============================================================================
 * LAUNCHER ENTRY POINT
 * ============================================================================
 * - Old app file: [OriginalMainActivity.kt] (VSense / Smart Blind Stick)
 * - New app file: [VaachakMainActivity.kt] (Vaachak with Eye Test & Accessible UI)
 * ============================================================================
 */
open class MainActivity : VaachakMainActivity()

/**
 * AI Inference Modes for the Surroundings Screen:
 * - ONLINE: Uses Vaachak Render API (provider credentials stay on the server)
 * - OFFLINE: Uses the bundled YOLO detector and ML Kit OCR
 */
enum class AiInferenceMode(
    val title: String,
    val modelName: String,
    val description: String,
    val isOnline: Boolean
) {
    ONLINE(
        title = "Online Mode",
        modelName = "Gemma via Vaachak",
        description = "Uses the Vaachak cloud API on Render",
        isOnline = true
    ),
    OFFLINE(
        title = "Offline Mode",
        modelName = "YOLO and ML Kit",
        description = "On-device detection and text reading; cloud descriptions disabled",
        isOnline = false
    )
}

/**
 * Dropdown bar positioned at the top of the Surroundings screen to toggle
 * between cloud descriptions and on-device detection.
 */
@Composable
fun SurroundingModeDropdownBar(
    currentMode: AiInferenceMode,
    onModeSelected: (AiInferenceMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xEE111827),
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(
                width = 2.dp,
                color = if (currentMode.isOnline) Color(0xFF00E676) else Color(0xFF38BDF8)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Status glow indicator
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (currentMode.isOnline) Color(0xFF00E676) else Color(0xFF38BDF8))
                    )

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = currentMode.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            // Chip badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (currentMode.isOnline) Color(0xFF064E3B) else Color(0xFF0C4A6E)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (currentMode.isOnline) "Cloud API" else "On-Device",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (currentMode.isOnline) Color(0xFF6EE7B7) else Color(0xFF7DD3FC)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = if (currentMode.isOnline)
                                "Gemma via Vaachak Render API"
                            else
                                "YOLO detection + ML Kit text reading",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Dropdown Caret Icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            if (expanded) {
                                moveTo(0f, size.height)
                                lineTo(size.width / 2f, 0f)
                                lineTo(size.width, size.height)
                            } else {
                                moveTo(0f, 0f)
                                lineTo(size.width / 2f, size.height)
                                lineTo(size.width, 0f)
                            }
                            close()
                        }
                        drawPath(path, color = Color.White)
                    }
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(Color(0xFF0F172A))
                .border(1.5.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
        ) {
            AiInferenceMode.values().forEach { mode ->
                val isSelected = currentMode == mode
                DropdownMenuItem(
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (mode.isOnline) Color(0xFF00E676) else Color(0xFF38BDF8))
                                    )
                                    Text(
                                        text = mode.title,
                                        fontSize = 15.sp,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                                    )
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF064E3B))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF6EE7B7)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Model: ${mode.modelName}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (mode.isOnline) Color(0xFF86EFAC) else Color(0xFF93C5FD)
                            )

                            Text(
                                text = mode.description,
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onModeSelected(mode)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) Color(0xFF1E293B) else Color.Transparent)
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    cameraExecutor: ExecutorService,
    visionProcessor: VisionProcessor,
    onAnalyzeImage: (androidx.camera.core.ImageProxy) -> Unit,
    onCameraBound: (Camera) -> Unit,
    onMicClick: () -> Unit,
    selectedAiMode: AiInferenceMode = AiInferenceMode.ONLINE,
    onModeChanged: ((AiInferenceMode) -> Unit)? = null
) {
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var currentDetections by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var imageDimensions by remember { mutableStateOf(Pair(1, 1)) }
    
    DisposableEffect(visionProcessor) {
        visionProcessor.onDetectionsUpdated = { detections, width, height ->
            currentDetections = detections
            imageDimensions = Pair(width, height)
        }
        onDispose {
            visionProcessor.onDetectionsUpdated = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Preview Feed
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .align(Alignment.Center)
                .clickable { onMicClick() }
        ) {
            CameraPreviewFeed(
                cameraExecutor = cameraExecutor,
                onAnalyzeImage = onAnalyzeImage,
                onCameraBound = onCameraBound,
                lensFacing = lensFacing,
                modifier = Modifier.fillMaxSize()
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val scaleX = size.width / imageDimensions.first.toFloat()
                val scaleY = size.height / imageDimensions.second.toFloat()
                
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GREEN
                    textSize = 60f
                    isFakeBoldText = true
                }
                
                for (det in currentDetections) {
                    val left = det.boundingBox.left * scaleX
                    val top = det.boundingBox.top * scaleY
                    val right = det.boundingBox.right * scaleX
                    val bottom = det.boundingBox.bottom * scaleY
                    
                    drawRect(
                        color = Color.Green,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        style = Stroke(width = 8f)
                    )
                    
                    drawContext.canvas.nativeCanvas.drawText(
                        det.label, left, top - 10f, paint
                    )
                }
            }
        }

        // TOP: Accessible Drop Down Bar to switch between Online and Offline modes
        SurroundingModeDropdownBar(
            currentMode = selectedAiMode,
            onModeSelected = { newMode ->
                onModeChanged?.invoke(newMode)
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        )

        // BOTTOM: Large accessible buttons for Surroundings view
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onMicClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E676),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(52.dp)
            ) {
                Text("Tap to Speak", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Button(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF27272A),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(52.dp)
            ) {
                Text("Flip Camera", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun CameraPreviewFeed(
    cameraExecutor: ExecutorService,
    onAnalyzeImage: (androidx.camera.core.ImageProxy) -> Unit,
    onCameraBound: (Camera) -> Unit,
    lensFacing: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx)
        },
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            onAnalyzeImage(imageProxy)
                        }
                    }

                val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview, imageAnalyzer
                    )
                    onCameraBound(camera)
                } catch (exc: Exception) {
                    Log.e("CameraX", "Use case binding failed", exc)
                }
            }, ContextCompat.getMainExecutor(context))
        },
        modifier = modifier
    )
}