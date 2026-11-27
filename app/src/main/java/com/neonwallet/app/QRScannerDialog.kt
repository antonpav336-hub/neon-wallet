package com.neonwallet.app

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
internal fun QRScannerDialog(onClose: () -> Unit, onScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var delivered by remember { mutableStateOf(false) }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
    }
    DisposableEffect(lifecycleOwner, previewView) {
        val view = previewView
        if (view == null) return@DisposableEffect onDispose { scanner.close() }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val mediaImage = proxy.image
                    if (mediaImage == null || delivered) {
                        proxy.close()
                    } else {
                        val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener(mainExecutor) { codes ->
                                val value = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                if (!value.isNullOrBlank() && !delivered) {
                                    delivered = true
                                    onScanned(value)
                                }
                            }
                            .addOnCompleteListener { proxy.close() }
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (error: Exception) {
                cameraError = error.localizedMessage ?: "Не удалось запустить камеру."
            }
        }, mainExecutor)
        onDispose {
            try { cameraProviderFuture.get().unbindAll() } catch (_: Exception) {}
            scanner.close()
            executor.shutdown()
        }
    }

    Surface(Modifier.fillMaxSize(), color = Bg, shape = RoundedCornerShape(0.dp)) {
        Column(Modifier.fillMaxSize().background(Bg)) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null, tint = White) }
                Text("Сканировать QR", color = White, fontSize = 20.sp)
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp).background(Color.Black, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center) {
                AndroidView(factory = { PreviewView(it).also { view -> view.scaleType = PreviewView.ScaleType.FILL_CENTER; previewView = view } },
                    modifier = Modifier.fillMaxSize())
                if (cameraError != null) {
                    Text(cameraError!!, color = White, modifier = Modifier.padding(24.dp))
                } else {
                    Box(Modifier.size(250.dp).background(Color.Transparent)) {
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                            val stroke = 4.dp.toPx()
                            val length = 42.dp.toPx()
                            val c = Color(0xFFB88CFF)
                            drawLine(c, androidx.compose.ui.geometry.Offset(0f,0f), androidx.compose.ui.geometry.Offset(length,0f), stroke)
                            drawLine(c, androidx.compose.ui.geometry.Offset(0f,0f), androidx.compose.ui.geometry.Offset(0f,length), stroke)
                            drawLine(c, androidx.compose.ui.geometry.Offset(size.width,0f), androidx.compose.ui.geometry.Offset(size.width-length,0f), stroke)
                            drawLine(c, androidx.compose.ui.geometry.Offset(size.width,0f), androidx.compose.ui.geometry.Offset(size.width,length), stroke)
                            drawLine(c, androidx.compose.ui.geometry.Offset(0f,size.height), androidx.compose.ui.geometry.Offset(length,size.height), stroke)
                            drawLine(c, androidx.compose.ui.geometry.Offset(0f,size.height), androidx.compose.ui.geometry.Offset(0f,size.height-length), stroke)
                            drawLine(c, androidx.compose.ui.geometry.Offset(size.width,size.height), androidx.compose.ui.geometry.Offset(size.width-length,size.height), stroke)
                            drawLine(c, androidx.compose.ui.geometry.Offset(size.width,size.height), androidx.compose.ui.geometry.Offset(size.width,size.height-length), stroke)
                        }
                    }
                }
            }
            Text("Наведите камеру на QR-код получателя или платёжной ссылки.",
                color = Muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(24.dp))
        }
    }
}
