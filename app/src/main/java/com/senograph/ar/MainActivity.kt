package com.senograph.ar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.senograph.ar.core.AudioController
import com.senograph.ar.core.PreferencesStore
import com.senograph.ar.core.UltraFrameAnalyzer
import com.senograph.ar.core.UltraTemplateDetector
import com.senograph.ar.ui.AppTheme
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                UltimateApp()
            }
        }
    }
}

@Composable
private fun UltimateApp() {
    val context = LocalContext.current
    val prefs = remember { PreferencesStore(context) }
    val detector = remember { UltraTemplateDetector(context) }
    val audio = remember { AudioController(context.applicationContext) }

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var controlsHidden by rememberSaveable { mutableStateOf(prefs.controlsHidden) }
    var pinkTheme by rememberSaveable { mutableStateOf(prefs.pinkTheme) }
    var detectionEnabled by rememberSaveable { mutableStateOf(true) }
    var targetVisible by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    var referenceUri by rememberSaveable { mutableStateOf(prefs.referenceUri) }
    var referenceName by rememberSaveable { mutableStateOf(prefs.referenceName) }
    var audioUri by rememberSaveable { mutableStateOf(prefs.audioUri) }
    var audioName by rememberSaveable { mutableStateOf(prefs.audioName) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(referenceUri) {
        referenceUri?.let { detector.loadReference(Uri.parse(it)) } ?: detector.clearReference()
        prefs.referenceUri = referenceUri
    }
    LaunchedEffect(referenceName) { prefs.referenceName = referenceName }
    LaunchedEffect(audioUri) { prefs.audioUri = audioUri }
    LaunchedEffect(audioName) { prefs.audioName = audioName }
    LaunchedEffect(controlsHidden) { prefs.controlsHidden = controlsHidden }
    LaunchedEffect(pinkTheme) { prefs.pinkTheme = pinkTheme }

    LaunchedEffect(detectionEnabled, targetVisible, audioUri) {
        if (!detectionEnabled) {
            targetVisible = false
            audio.stopAndRewind()
        } else if (targetVisible && audioUri != null) {
            audio.playFromStart(audioUri!!)
        } else {
            audio.stopAndRewind()
        }
    }

    val bg = if (pinkTheme) {
        Brush.radialGradient(
            colors = listOf(Color(0xFF301429), Color(0xFF16111B), Color(0xFF090B10)),
            center = Offset.Zero,
            radius = 1600f
        )
    } else {
        Brush.radialGradient(
            colors = listOf(Color(0xFF10274A), Color(0xFF0F1420), Color(0xFF080B12)),
            center = Offset.Zero,
            radius = 1600f
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        if (cameraGranted) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                detector = detector,
                detectionEnabled = detectionEnabled,
                onTargetVisibleChanged = { targetVisible = it }
            )
        } else {
            PermissionScreen(onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.68f to Color.Transparent,
                        1f to Color(0xAA000000)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            TopBar(
                controlsHidden = controlsHidden,
                pinkTheme = pinkTheme,
                onToggleHide = { controlsHidden = !controlsHidden },
                onToggleTheme = { pinkTheme = !pinkTheme }
            )

            StatusCard(
                detectionEnabled = detectionEnabled,
                targetVisible = targetVisible,
                controlsHidden = controlsHidden
            )

            BottomBar(
                controlsHidden = controlsHidden,
                pinkTheme = pinkTheme,
                detectionEnabled = detectionEnabled,
                targetVisible = targetVisible,
                replayEnabled = audio.isPlaying,
                onOpenSettings = { showSettings = true },
                onToggleDetection = { detectionEnabled = !detectionEnabled },
                onReplay = { audio.playFromStart(audioUri) }
            )
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            SettingsSheet(
                currentReferenceName = referenceName,
                currentAudioName = audioName,
                onPickReference = { uri, name ->
                    referenceUri = uri?.toString()
                    referenceName = name
                },
                onPickAudio = { uri, name ->
                    audioUri = uri?.toString()
                    audioName = name
                },
                onClearReference = {
                    referenceUri = null
                    referenceName = null
                },
                onClearAudio = {
                    audioUri = null
                    audioName = null
                    audio.stopAndRewind()
                },
                onDismiss = { showSettings = false }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { audio.release() }
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08111D)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121B2B)),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.White)
                Spacer(Modifier.height(10.dp))
                Text("مجوز دوربین لازم است", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "برای شروع تشخیص تصویر، اجازه دسترسی به دوربین را بده.",
                    color = Color.White.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onGrant) { Text("اجازه دادن") }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier,
    detector: UltraTemplateDetector,
    detectionEnabled: Boolean,
    onTargetVisibleChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val analyzer = remember { UltraFrameAnalyzer(detector, onTargetVisibleChanged) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(detectionEnabled) {
        analyzer.enabled = detectionEnabled
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            try {
                val provider = cameraProviderFuture.get()
                val preview = androidx.camera.core.Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(android.util.Size(960, 720))
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (_: Throwable) {
            }
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            executor.shutdown()
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Throwable) {
            }
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
private fun TopBar(
    controlsHidden: Boolean,
    pinkTheme: Boolean,
    onToggleHide: () -> Unit,
    onToggleTheme: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        GlassButton(
            icon = if (controlsHidden) Icons.Filled.RemoveRedEye else Icons.Filled.VisibilityOff,
            label = if (controlsHidden) "نمایش" else "مخفی",
            active = !controlsHidden,
            onClick = onToggleHide
        )
        GlassButton(
            icon = if (pinkTheme) Icons.Filled.Palette else Icons.Filled.DashboardCustomize,
            label = if (pinkTheme) "صورتی" else "آبی",
            active = pinkTheme,
            onClick = onToggleTheme
        )
    }
}

@Composable
private fun StatusCard(
    detectionEnabled: Boolean,
    targetVisible: Boolean,
    controlsHidden: Boolean
) {
    val title = when {
        !detectionEnabled -> "تشخیص خاموش است"
        targetVisible -> "هدف پیدا شد"
        else -> "در حال جست‌وجو"
    }
    val subtitle = when {
        !detectionEnabled -> "دکمه وسط را بزن تا تشخیص فعال شود"
        targetVisible -> "صدا باید شروع شده باشد"
        else -> "تصویر مرجع را در کادر دوربین قرار بده"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (controlsHidden) 0.18f else 1f),
        color = Color.White.copy(alpha = 0.08f),
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun BottomBar(
    controlsHidden: Boolean,
    pinkTheme: Boolean,
    detectionEnabled: Boolean,
    targetVisible: Boolean,
    replayEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onToggleDetection: () -> Unit,
    onReplay: () -> Unit
) {
    val blue = if (pinkTheme) Color(0xFFFF82B7) else Color(0xFF73BDFF)
    val green = Color(0xFF25D366)
    val red = Color(0xFFFF5A66)
    val muted = Color(0xFF65708A).copy(alpha = 0.36f)
    val alphaFactor = if (controlsHidden) 0.22f else 1f

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleAction(
            icon = Icons.Filled.Image,
            label = "تنظیمات",
            enabled = !controlsHidden,
            containerColor = blue.copy(alpha = alphaFactor),
            onClick = onOpenSettings
        )
        CircleAction(
            icon = if (detectionEnabled) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            label = if (detectionEnabled) "خاموش" else "روشن",
            enabled = !controlsHidden,
            containerColor = (if (detectionEnabled) red else green).copy(alpha = alphaFactor),
            size = 84.dp,
            onClick = onToggleDetection
        )
        CircleAction(
            icon = Icons.Filled.Cached,
            label = "تکرار",
            enabled = !controlsHidden && targetVisible && replayEnabled,
            containerColor = if (!controlsHidden && targetVisible && replayEnabled) blue.copy(alpha = alphaFactor) else muted,
            onClick = onReplay
        )
    }
}

@Composable
private fun GlassButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(52.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = if (active) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
    }
}

@Composable
private fun CircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    containerColor: Color,
    size: androidx.compose.ui.unit.Dp = 68.dp,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(size)
            .clickable(enabled = enabled) { onClick() },
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.13f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
    }
}

@Composable
private fun SettingsSheet(
    currentReferenceName: String?,
    currentAudioName: String?,
    onPickReference: (Uri?, String?) -> Unit,
    onPickAudio: (Uri?, String?) -> Unit,
    onClearReference: () -> Unit,
    onClearAudio: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val refPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persistPermission(context, uri)
            onPickReference(uri, displayName(context, uri))
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persistPermission(context, uri)
            onPickAudio(uri, displayName(context, uri))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable { onDismiss() }
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .clickable(enabled = false) {},
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121B2B)),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 44.dp, height = 5.dp)
                        .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(99.dp))
                )
                Spacer(Modifier.height(14.dp))
                Text("پنل تنظیمات", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "در این بخش می‌توانی تصویر مرجع و فایل صوتی را عوض کنی و دوباره از اول شروع کنی.",
                    color = Color.White.copy(alpha = 0.78f)
                )

                Spacer(Modifier.height(18.dp))
                SectionTitle("تصویر مرجع")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { refPicker.launch(arrayOf("image/*")) }) {
                        Icon(Icons.Filled.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("انتخاب تصویر")
                    }
                    OutlinedButton(onClick = onClearReference, enabled = currentReferenceName != null) {
                        Text("حذف")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    currentReferenceName?.let { "فایل: $it" } ?: "هنوز تصویری انتخاب نشده",
                    color = Color.White.copy(alpha = 0.78f)
                )

                Spacer(Modifier.height(16.dp))
                SectionTitle("فایل صدا")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClic
