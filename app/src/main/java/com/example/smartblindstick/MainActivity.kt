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

/**
 * ============================================================================
 * LAUNCHER ENTRY POINT
 * ============================================================================
 * - Old app file: [OriginalMainActivity.kt] (VSense / Smart Blind Stick)
 * - New app file: [VaachakMainActivity.kt] (Vaachak with Eye Test & Accessible UI)
 * ============================================================================
 */
open class MainActivity : VaachakMainActivity()

@Composable
fun MainScreen(
    cameraExecutor: ExecutorService,
    visionProcessor: VisionProcessor,
    onAnalyzeImage: (androidx.camera.core.ImageProxy) -> Unit,
    onCameraBound: (Camera) -> Unit,
    onMicClick: () -> Unit
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

    Box(modifier = Modifier.fillMaxSize()) {
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

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = onMicClick) {
                Text("Tap to Speak")
            }
            Button(onClick = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            }) {
                Text("Flip Camera")
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