package com.neonwallet.app

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
    }
    DisposableEffect(lifecycleOwner, previewView) {
        val view = previewView
        if (view == null) return@DisposableEffect onDispose { }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var boundProvider: ProcessCameraProvider? = null
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                boundProvider = provider
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
                val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                cameraControl = camera.cameraControl
            } catch (error: Exception) {
                cameraError = error.localizedMessage ?: "Не удалось запустить камеру."
            }
        }, mainExecutor)
        onDispose {
            try { boundProvider?.unbindAll() } catch (_: Exception) {}
            cameraControl = null
            scanner.close()
            executor.shutdown()
        }
    }

    val transition = rememberInfiniteTransition(label = "qr_scan_line")
    val scanProgress by transition.animateFloat(
        initialValue = 0.12f, targetValue = 0.88f,
        animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "qr_scan_progress"
    )

    Surface(Modifier.fillMaxSize(), color = Bg) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.size(46.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = White)
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("Оплата по QR", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("NEON WALLET", color = Purple, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Panel),
                    contentAlignment = Alignment.Center
                ) {
                    Text("QR", color = White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Наведите камеру на код", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(
                "Расположите QR-код внутри рамки — мы автоматически распознаем его",
                color = Muted, fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(26.dp))

            Box(
                Modifier.fillMaxWidth().weight(1f, fill = false)
                    .heightIn(min = 280.dp, max = 390.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xFF09080D)),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { PreviewView(it).also { view ->
                        view.scaleType = PreviewView.ScaleType.FILL_CENTER
                        view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        previewView = view
                    } },
                    modifier = Modifier.fillMaxSize()
                )
                if (cameraError != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                        Text("Камера недоступна", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(cameraError ?: "", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                            val frame = size.minDimension * 0.68f
                            val left = (size.width - frame) / 2f
                            val top = (size.height - frame) / 2f
                            val right = left + frame
                            val bottom = top + frame
                            val corner = 30.dp.toPx()
                            val stroke = 4.dp.toPx()
                            val color = Color(0xFFD8B8FF)
                            // Four clean corner brackets keep the scan target visually precise.
                            drawLine(color, Offset(left, top), Offset(left + corner, top), stroke, cap = StrokeCap.Round)
                            drawLine(color, Offset(left, top), Offset(left, top + corner), stroke, cap = StrokeCap.Round)
                            drawLine(color, Offset(right, top), Offset(right - corner, top), stroke, cap = StrokeCap.Round)
                            drawLine(color, Offset(right, top), Offset(right, top + corner), stroke, cap = StrokeCap.Round)
                            drawLine(color, Offset(left, bottom), Offset(left + corner, bottom), stroke, cap = StrokeCap.Round)
                            drawLine(color, Offset(left, bottom), Offset(left, bottom - corner), stroke, cap = StrokeCap.Round)
                            drawLine(color, Offset(right, bottom), Offset(right - corner, bottom), stroke, cap = StrokeCap.Round)
                            drawLine(color, Offset(right, bottom), Offset(right, bottom - corner), stroke, cap = StrokeCap.Round)
                            val scanY = top + frame * scanProgress
                            drawLine(Color(0xFFB88CFF).copy(alpha = 0.85f), Offset(left + 8.dp.toPx(), scanY),
                                Offset(right - 8.dp.toPx(), scanY), 2.dp.toPx(), cap = StrokeCap.Round)
                        }
                    }
                }
                if (cameraError == null) {
                    Surface(
                        onClick = {
                            val next = !torchEnabled
                            cameraControl?.enableTorch(next)
                            torchEnabled = next
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(48.dp),
                        shape = CircleShape, color = Color(0xB8201B29), contentColor = White
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (torchEnabled) Icons.Default.FlashOff else Icons.Default.FlashOn,
                                contentDescription = if (torchEnabled) "Выключить вспышку" else "Включить вспышку")
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Panel).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF332344)), contentAlignment = Alignment.Center) {
                    Text("✦", color = Color(0xFFD8B8FF), fontSize = 20.sp)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Быстро и безопасно", color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Не закрывайте камеру до распознавания", color = Muted, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 3.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
