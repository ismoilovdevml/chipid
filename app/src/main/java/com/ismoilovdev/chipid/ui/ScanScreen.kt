package com.ismoilovdev.chipid.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ismoilovdev.chipid.R
import com.ismoilovdev.chipid.core.Mrz
import com.ismoilovdev.chipid.scan.MrzAnalyzer
import java.util.concurrent.Executors

@Composable
fun ScanScreen(docType: DocType, onBack: () -> Unit, onManual: () -> Unit, onContinue: (Mrz.Result) -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    var result by remember { mutableStateOf<Mrz.Result?>(null) }
    var near by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val haptics = LocalHapticFeedback.current

    ScreenFrame(
        bg = C.scanBg,
        gap = 16.dp,
        scroll = false,
        bottom = {
            PrimaryButton(stringResource(R.string.scan_continue), enabled = result != null) { result?.let(onContinue) }
            GhostButton(stringResource(R.string.enter_manually), icon = Ic.pencil, onClick = onManual)
        },
    ) {
        TopBar(
            stringResource(R.string.step_of, 1, 3),
            left = { RoundIconButton(Ic.back, stringResource(R.string.back), onClick = onBack) },
            right = {
                RoundIconButton(Ic.flash, stringResource(R.string.torch), tint = if (torch) C.warn else C.text) {
                    torch = !torch; camera?.cameraControl?.enableTorch(torch)
                }
            },
        )
        StepBar(1)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(if (docType == DocType.PASSPORT) R.string.scan_title_passport else R.string.scan_title_id), style = T.h2, textAlign = TextAlign.Center)
            Text(stringResource(if (docType == DocType.PASSPORT) R.string.scan_sub_passport else R.string.scan_sub_id), style = T.bodyMuted.copy(fontSize = 14.sp), textAlign = TextAlign.Center)
        }
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).background(C.surface2), contentAlignment = Alignment.Center) {
            if (granted) {
                CameraPreview(
                    onCamera = { camera = it },
                    onCandidate = { near = it },
                    onMrz = { m -> result = m; haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                )
                FrameOverlay(found = result != null)
                StatusPill(
                    when {
                        result != null -> stringResource(R.string.scan_found)
                        near -> stringResource(R.string.scan_reading)
                        else -> stringResource(R.string.scan_looking)
                    },
                    color = if (result != null || near) C.success else C.text2,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                )
            } else {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(Ic.camera, C.iconBg, C.primary)
                    Text(stringResource(R.string.camera_needed), style = T.body, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.camera_local), style = T.small, textAlign = TextAlign.Center)
                    Box(Modifier.width(220.dp)) {
                        PrimaryButton(stringResource(if (asked) R.string.try_again else R.string.allow_camera)) { launcher.launch(Manifest.permission.CAMERA) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val r = result
            ValueTile(stringResource(R.string.tile_number), r?.let { mask(it.documentNumber) } ?: "—", Modifier.weight(1f), if (r != null) C.success else C.faint, compact = true)
            ValueTile(stringResource(R.string.tile_born), r?.let { ReportModel.date(it.dateOfBirth, birth = true) } ?: "—", Modifier.weight(1f), if (r != null) C.success else C.faint, compact = true)
            ValueTile(stringResource(R.string.tile_expires), r?.let { ReportModel.date(it.dateOfExpiry) } ?: "—", Modifier.weight(1f), if (r != null) C.success else C.faint, compact = true)
        }
        if (result != null) {
            GhostButton(stringResource(R.string.scan_again), height = 40.dp) { result = null; near = false }
        }
    }
}

/** First two characters, the rest hidden: enough to recognise the document, useless to a bystander. */
fun mask(doc: String): String = doc.take(2) + "•".repeat((doc.length - 2).coerceAtLeast(0))

@Composable
private fun CameraPreview(onCamera: (Camera) -> Unit, onCandidate: (Boolean) -> Unit, onMrz: (Mrz.Result) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember {
        MrzAnalyzer(
            onCandidate = { v -> ContextCompat.getMainExecutor(context).execute { onCandidate(v) } },
            onMrz = { m -> ContextCompat.getMainExecutor(context).execute { onMrz(m) } },
        )
    }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { it.setAnalyzer(executor, analyzer) }
            provider.unbindAll()
            onCamera(provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis))
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { future.get().unbindAll() }
            analyzer.close()
            executor.shutdown()
        }
    }
    AndroidView({ previewView }, Modifier.fillMaxSize())
}

@Composable
private fun FrameOverlay(found: Boolean) {
    val accent = if (found) C.success else C.primary
    Canvas(Modifier.fillMaxSize()) {
        val inset = 20.dp.toPx()
        // Frame keeps a document's 1.42:1 aspect inside whatever height the box gets.
        val frameH = (size.width - 2 * inset) / 1.42f
        val top = ((size.height - frameH) / 2).coerceAtLeast(16.dp.toPx())
        val l = inset; val r = size.width - inset; val t = top; val b = size.height - top
        val arm = 28.dp.toPx(); val rad = 10.dp.toPx(); val w = 3.dp.toPx()
        // Dim outside the frame.
        val outside = Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            addRoundRect(androidx.compose.ui.geometry.RoundRect(l, t, r, b, CornerRadius(rad)))
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        }
        drawPath(outside, Color.Black.copy(alpha = 0.45f))
        // MRZ band hint at the bottom of the frame.
        val bandTop = b - (b - t) * 0.30f
        drawRoundRect(accent.copy(alpha = if (found) 0.18f else 0.08f), Offset(l + 12.dp.toPx(), bandTop), GSize(r - l - 24.dp.toPx(), b - bandTop - 12.dp.toPx()), CornerRadius(8.dp.toPx()))
        drawRoundRect(accent.copy(alpha = 0.9f), Offset(l + 12.dp.toPx(), bandTop), GSize(r - l - 24.dp.toPx(), b - bandTop - 12.dp.toPx()), CornerRadius(8.dp.toPx()), style = Stroke(2.dp.toPx()))
        // Corner brackets.
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            val p = Path().apply {
                moveTo(x, y + dy * arm)
                lineTo(x, y + dy * rad)
                quadraticTo(x, y, x + dx * rad, y)
                lineTo(x + dx * arm, y)
            }
            drawPath(p, accent, style = Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        corner(l, t, 1f, 1f); corner(r, t, -1f, 1f); corner(l, b, 1f, -1f); corner(r, b, -1f, -1f)
    }
}

@Composable
private fun StatusPill(text: String, color: Color, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(999.dp)).background(C.bg.copy(alpha = 0.85f)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, style = T.small.copy(color = color, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
    }
}
