package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.LayoutPreset
import com.example.model.ScreenCaptureMode
import com.example.model.SourceType
import com.example.monitoring.DeviceDiagnosticsState
import com.example.sources.CameraHardwareState
import com.example.sources.ScreenCaptureState
import com.example.streaming.StreamEngineTelemetry
import com.example.ui.theme.StudioAccentSky
import com.example.ui.theme.StudioAccentViolet
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCardBg
import com.example.ui.theme.StudioElevatedBg
import com.example.ui.theme.StudioLiveRed
import com.example.ui.theme.StudioMainBg
import com.example.ui.theme.StudioSecondaryBg
import com.example.ui.theme.StudioSuccessGreen
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.viewmodel.SceneCastViewModel
import com.example.sources.MoonlightPcDiscovery
import com.example.sources.MoonlightSessionController
import com.example.viewmodel.StudioBottomPanel
import com.example.viewmodel.StudioNavTab
import com.example.viewmodel.StudioUiState

internal fun formatDurationSec(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hrs, mins, secs)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioMainScreen(
    viewModel: SceneCastViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraState by viewModel.cameraState.collectAsStateWithLifecycle()
    val screenCaptureState by viewModel.screenCaptureState.collectAsStateWithLifecycle()
    val networkFeedState by viewModel.networkFeedState.collectAsStateWithLifecycle()
    val audioState by viewModel.audioState.collectAsStateWithLifecycle()
    val telemetry by viewModel.streamTelemetry.collectAsStateWithLifecycle()
    val diagnostics by viewModel.deviceDiagnostics.collectAsStateWithLifecycle()

    // Keep screen awake & orientation lock
    DisposableEffect(uiState.keepScreenAwake, uiState.lockLandscapeOrientation) {
        val activity = context as? Activity
        if (uiState.keepScreenAwake) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (uiState.lockLandscapeOrientation) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {}
    }

    // Back button behavior for Program Mode, Bottom Sheets, and Secondary Tabs
    BackHandler(
        enabled = uiState.isProgramMode ||
            uiState.activeBottomPanel != StudioBottomPanel.NONE ||
            uiState.activeTab != StudioNavTab.STUDIO
    ) {
        when {
            uiState.isProgramMode -> viewModel.setProgramMode(false)
            uiState.activeBottomPanel != StudioBottomPanel.NONE -> viewModel.closeBottomPanel()
            uiState.activeTab != StudioNavTab.STUDIO -> viewModel.setActiveTab(StudioNavTab.STUDIO)
        }
    }

    // Runtime permissions for Camera, Microphone, and Android 13+ Notifications
    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasMicPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCameraPerm = grants[Manifest.permission.CAMERA] ?: hasCameraPerm
        hasMicPerm = grants[Manifest.permission.RECORD_AUDIO] ?: hasMicPerm
        viewModel.bindLifecycle(lifecycleOwner, hasCameraPerm, hasMicPerm)
    }

    LaunchedEffect(Unit) {
        val needed = mutableListOf<String>()
        if (!hasCameraPerm) needed.add(Manifest.permission.CAMERA)
        if (!hasMicPerm) needed.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            viewModel.bindLifecycle(lifecycleOwner, hasCameraPerm, hasMicPerm)
        }
    }

    // MediaProjection Launcher for phone screen capture
    var pendingCaptureMode by remember {
        mutableStateOf(ScreenCaptureMode.ANDROID_14_SINGLE_APP)
    }
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.onMediaProjectionPermissionGranted(
                resultCode = result.resultCode,
                data = result.data!!,
                mode = pendingCaptureMode
            )
        } else {
            viewModel.showStatusBanner("Screen capture permission declined")
        }
    }

    val requestScreenCapture: (ScreenCaptureMode) -> Unit = { mode ->
        pendingCaptureMode = mode
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mgr.createScreenCaptureIntent())
    }

    // Zero-permission Photo Picker for PNG Overlays, Images, Videos & Custom Borders
    var pendingImageSourceType by remember { mutableStateOf(SourceType.PNG_OVERLAY) }
    var pendingAttachBorder by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importAssetFromUri(
                uri = uri,
                targetType = pendingImageSourceType,
                attachToSelectedBorder = pendingAttachBorder
            )
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importAssetFromUri(
                uri = uri,
                targetType = SourceType.VIDEO_FILE,
                attachToSelectedBorder = false
            )
        }
    }

    // Program Mode (Full-Screen Clean Broadcast Output without Editor Chrome)
    if (uiState.isProgramMode) {
        ProgramCleanMonitorView(
            uiState = uiState,
            telemetry = telemetry,
            compositor = viewModel.compositor,
            onExitProgramMode = { viewModel.setProgramMode(false) },
            onTogglePillVisibility = { viewModel.toggleProgramFloatingPill() },
            onStopStream = { viewModel.stopLiveStream() }
        )
        return
    }

    Scaffold(
        containerColor = StudioMainBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            StudioTopBar(
                uiState = uiState,
                telemetry = telemetry,
                screenCaptureState = screenCaptureState,
                cameraState = cameraState,
                onEnterProgramMode = { viewModel.setProgramMode(true) },
                onOpenSettingsTab = { viewModel.setActiveTab(StudioNavTab.SETTINGS) }
            )
        },
        bottomBar = {
            StudioBottomNavigationBar(
                activeTab = uiState.activeTab,
                isStreaming = telemetry.isStreaming,
                onSelectTab = { tab -> viewModel.setActiveTab(tab) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(StudioMainBg)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (uiState.activeTab != StudioNavTab.STUDIO) {
                    PersistentPreviewPane(
                        uiState = uiState,
                        telemetry = telemetry,
                        compositor = viewModel.compositor,
                        viewModel = viewModel,
                        modifier = Modifier.weight(0.42f).fillMaxWidth()
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(if (uiState.activeTab == StudioNavTab.STUDIO) 1f else 0.58f)
                        .fillMaxWidth()
                        .background(StudioMainBg)
                ) {
            when (uiState.activeTab) {
                StudioNavTab.STUDIO -> {
                    PortraitStudioWorkspace(
                        uiState = uiState,
                        telemetry = telemetry,
                        screenCaptureState = screenCaptureState,
                        cameraState = cameraState,
                        diagnostics = diagnostics,
                        compositor = viewModel.compositor,
                        onSelectLayer = { viewModel.selectLayer(it) },
                        onMoveLayer = { id, dx, dy -> viewModel.moveLayerByDelta(id, dx, dy) },
                        onResizeLayer = { id, h, dx, dy -> viewModel.resizeLayerFromHandle(id, h, dx, dy) },
                        onGestureFinished = { viewModel.finishGestureAndPersist() },
                        onUndo = { viewModel.undoSceneEdit() },
                        onRedo = { viewModel.redoSceneEdit() },
                        onToggleScaleMode = { id -> viewModel.toggleLayerScaleMode(id) },
                        onOpenLayerInspector = { id ->
                            viewModel.selectLayer(id)
                            viewModel.setBottomPanel(StudioBottomPanel.INSPECTOR)
                        },
                        onToggleLockLayer = { id -> viewModel.toggleLayerLock(id) },
                        onDeleteLayer = { id -> viewModel.deleteLayer(id) },
                        onQuickSelectOrAddSource = { type ->
                            val existing = uiState.activeScene.layers.firstOrNull { it.type == type }
                            if (type == SourceType.CAMERA && existing != null) {
                                viewModel.selectLayer(existing.id)
                                viewModel.setBottomPanel(StudioBottomPanel.INSPECTOR)
                            } else if (type == SourceType.SCREEN_CAPTURE && !screenCaptureState.isCapturing) {
                                requestScreenCapture(ScreenCaptureMode.ANDROID_14_SINGLE_APP)
                                if (existing == null) viewModel.addSourceLayer(SourceType.SCREEN_CAPTURE)
                            } else {
                                viewModel.addSourceLayer(type)
                            }
                        },
                        onPickImageFile = { type ->
                            pendingImageSourceType = type
                            pendingAttachBorder = false
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onPickVideoFile = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        onOpenScenes = { viewModel.setActiveTab(StudioNavTab.SCENES) },
                        onOpenLayersSheet = { viewModel.setBottomPanel(StudioBottomPanel.LAYERS) },
                        onOpenAudio = { viewModel.setActiveTab(StudioNavTab.AUDIO) },
                        onOpenLayoutSheet = { viewModel.setBottomPanel(StudioBottomPanel.LAYOUT_PRESETS) },
                        onStartStream = { viewModel.startLiveStream() },
                        onStopStream = { viewModel.stopLiveStream() }
                    )
                }

                StudioNavTab.SCENES -> {
                    SceneManagerSheetContent(
                        uiState = uiState,
                        onSelectScene = { viewModel.selectScene(it) },
                        onCreateScene = { name, preset -> viewModel.createNewScene(name, preset) },
                        onDuplicateScene = { viewModel.duplicateScene(it) },
                        onRenameScene = { id, name -> viewModel.renameScene(id, name) },
                        onDeleteScene = { viewModel.deleteScene(it) },
                        onApplyLayoutPreset = { viewModel.applyLayoutPreset(it) },
                        onUpdateCanvasPreset = { preset ->
                            viewModel.updateSceneCanvasResolution(preset, preset.defaultWidth, preset.defaultHeight)
                        },
                        onSelectTransition = { viewModel.setSceneTransitionType(it) },
                        onSaveSceneExplicitly = { viewModel.saveCurrentSceneExplicitly() },
                        onDeleteSourceFromScene = { sceneId, layerId ->
                            viewModel.deleteSourceFromScene(sceneId, layerId)
                        }
                    )
                }

                StudioNavTab.SOURCES -> {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                    AddSourceSheetContent(
                        uiState = uiState,
                        cameraState = cameraState,
                        screenCaptureState = screenCaptureState,
                        onAddSource = { type, resId -> viewModel.addSourceLayer(type, builtInRes = resId) },
                        onSelectLayer = { viewModel.selectLayer(it) },
                        onToggleVisibility = { viewModel.toggleLayerVisibility(it) },
                        onOpenLayerInspector = { id ->
                            viewModel.selectLayer(id)
                            viewModel.setBottomPanel(StudioBottomPanel.INSPECTOR)
                        },
                        onRequestScreenCapture = requestScreenCapture,
                        onStopScreenCapture = { viewModel.stopScreenCapture() },
                        onPickImageFile = { type ->
                            pendingImageSourceType = type
                            pendingAttachBorder = false
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onPickVideoFile = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        onDeleteLayer = { viewModel.deleteLayer(it) },
                        onRenameLayer = { id, name -> viewModel.renameLayer(id, name) }
                    )
                    MoonlightPcManagerPanel(
                        discovery = viewModel.moonlightDiscovery,
                        session = viewModel.moonlightSession,
                        onAddLayerAndConnect = { host, port ->
                            viewModel.addSourceLayer(SourceType.MOONLIGHT_PC, customName = "Moonlight · $host")
                            val layerId = viewModel.uiState.value.activeScene.layers.lastOrNull()?.id
                            if (layerId != null) (context as? Activity)?.let { activity ->
                                viewModel.connectMoonlight(activity, host, layerId, port)
                            }
                        }
                    )
                    }
                }

                StudioNavTab.AUDIO -> {
                    AudioMixerSheetContent(
                        audioState = audioState,
                        onChannelVolumeChanged = { id, vol -> viewModel.audioMixer.setChannelVolume(id, vol) },
                        onToggleChannelMute = { id -> viewModel.audioMixer.toggleChannelMute(id) },
                        onMasterVolumeChanged = { vol -> viewModel.audioMixer.setChannelVolume("master", vol) },
                        onToggleMasterMute = { viewModel.audioMixer.toggleChannelMute("master") },
                        onToggleNoiseSuppression = {
                            viewModel.audioMixer.setNoiseSuppression(!audioState.noiseSuppressionEnabled)
                        },
                        onToggleEchoCancellation = {
                            viewModel.audioMixer.setEchoCancellation(!audioState.echoCancellationEnabled)
                        },
                        onMicSyncOffsetChanged = { ms -> viewModel.audioMixer.setAvSyncOffsetMs(ms) }
                    )
                }

                StudioNavTab.SETTINGS -> {
                    StreamSettingsSheetContent(
                        uiState = uiState,
                        telemetry = telemetry,
                        diagnostics = diagnostics,
                        audioState = audioState,
                        cameraState = cameraState,
                        onUpdateCredentials = { viewModel.updateStreamCredentials(it) },
                        onTestConnection = { viewModel.testYouTubeConnection() },
                        onStartStream = { viewModel.startLiveStream() },
                        onStopStream = { viewModel.stopLiveStream() },
                        onToggleNoiseSuppression = {
                            viewModel.audioMixer.setNoiseSuppression(!audioState.noiseSuppressionEnabled)
                        },
                        onToggleEchoCancellation = {
                            viewModel.audioMixer.setEchoCancellation(!audioState.echoCancellationEnabled)
                        },
                        onMicSyncOffsetChanged = { ms -> viewModel.audioMixer.setAvSyncOffsetMs(ms) },
                        onUpdateCameraConfig = { transform ->
                            val camLayer = uiState.activeScene.layers.find { it.type == SourceType.CAMERA }
                            if (camLayer != null) {
                                viewModel.updateCameraLayerAndHardware(lifecycleOwner, camLayer.id, transform)
                            }
                        },
                        onToggleCenterSnap = { viewModel.toggleCenterSnap() },
                        onToggleGridSnap = { viewModel.toggleGridSnap() },
                        onToggleSafeAreaGuides = { viewModel.toggleSafeAreaGuides() },
                        onSetKeepScreenAwake = { viewModel.setKeepScreenAwake(it) },
                        onSetLockLandscape = { viewModel.setLockLandscapeOrientation(it) },
                        onSaveSceneExplicitly = { viewModel.saveCurrentSceneExplicitly() }
                    )
                }
            }
                }
            }

            // Transient Toast Banner (tap anywhere to dismiss immediately)
            AnimatedVisibility(
                visible = uiState.statusBannerMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                Surface(
                    color = StudioElevatedBg,
                    shape = RoundedCornerShape(999.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioAccentViolet),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .clickable { viewModel.dismissStatusBanner() }
                        .testTag("status_toast_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = uiState.statusBannerMessage.orEmpty(),
                            color = StudioTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = StudioTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }

    // Contextual Modal Bottom Sheets (Layers, Source Inspector, PC Stream Quick Connect, Layout Presets, Stream Key prompt)
    if (uiState.activeBottomPanel != StudioBottomPanel.NONE) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeBottomPanel() },
            sheetState = sheetState,
            containerColor = StudioSecondaryBg,
            contentColor = StudioTextPrimary,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            when (uiState.activeBottomPanel) {
                StudioBottomPanel.LAYERS -> {
                    LayersSheetContent(
                        uiState = uiState,
                        onSelectLayer = { viewModel.selectLayer(it) },
                        onBringForward = { viewModel.bringLayerForward(it) },
                        onSendBackward = { viewModel.sendLayerBackward(it) },
                        onToggleVisibility = { viewModel.toggleLayerVisibility(it) },
                        onToggleLock = { viewModel.toggleLayerLock(it) },
                        onDuplicateLayer = { viewModel.duplicateLayer(it) },
                        onDeleteLayer = { viewModel.deleteLayer(it) },
                        onOpenInspector = { viewModel.setBottomPanel(StudioBottomPanel.INSPECTOR) }
                    )
                }

                StudioBottomPanel.INSPECTOR -> {
                    InspectorSheetContent(
                        selectedLayer = uiState.selectedLayer,
                        cameraState = cameraState,
                        networkFeedState = networkFeedState,
                        onUpdateLayer = { transform ->
                            val selId = uiState.selectedLayerId
                            if (selId != null) viewModel.updateLayer(selId, transform = transform)
                        },
                        onSetExactWidthPercent = { pct ->
                            val selId = uiState.selectedLayerId
                            if (selId != null) viewModel.setLayerExactPercentageWidth(selId, pct)
                        },
                        onUpdateCameraConfig = { transform ->
                            val selId = uiState.selectedLayerId
                            if (selId != null) {
                                viewModel.updateCameraLayerAndHardware(lifecycleOwner, selId, transform)
                            }
                        },
                        onPickCustomPngBorder = {
                            pendingImageSourceType = SourceType.PNG_OVERLAY
                            pendingAttachBorder = true
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }

                StudioBottomPanel.LAYOUT_PRESETS -> {
                    QuickLayoutPresetSheet(
                        activeSceneName = uiState.activeScene.name,
                        onApplyLayoutPreset = { preset ->
                            viewModel.applyLayoutPreset(preset)
                            viewModel.closeBottomPanel()
                        }
                    )
                }

                StudioBottomPanel.STREAM_SETTINGS -> {
                    StreamSettingsSheetContent(
                        uiState = uiState,
                        telemetry = telemetry,
                        diagnostics = diagnostics,
                        audioState = audioState,
                        cameraState = cameraState,
                        onUpdateCredentials = { viewModel.updateStreamCredentials(it) },
                        onTestConnection = { viewModel.testYouTubeConnection() },
                        onStartStream = { viewModel.startLiveStream() },
                        onStopStream = { viewModel.stopLiveStream() },
                        onToggleNoiseSuppression = {
                            viewModel.audioMixer.setNoiseSuppression(!audioState.noiseSuppressionEnabled)
                        },
                        onToggleEchoCancellation = {
                            viewModel.audioMixer.setEchoCancellation(!audioState.echoCancellationEnabled)
                        },
                        onMicSyncOffsetChanged = { ms -> viewModel.audioMixer.setAvSyncOffsetMs(ms) },
                        onUpdateCameraConfig = { transform ->
                            val camLayer = uiState.activeScene.layers.find { it.type == SourceType.CAMERA }
                            if (camLayer != null) {
                                viewModel.updateCameraLayerAndHardware(lifecycleOwner, camLayer.id, transform)
                            }
                        },
                        onToggleCenterSnap = { viewModel.toggleCenterSnap() },
                        onToggleGridSnap = { viewModel.toggleGridSnap() },
                        onToggleSafeAreaGuides = { viewModel.toggleSafeAreaGuides() },
                        onSetKeepScreenAwake = { viewModel.setKeepScreenAwake(it) },
                        onSetLockLandscape = { viewModel.setLockLandscapeOrientation(it) },
                        onSaveSceneExplicitly = { viewModel.saveCurrentSceneExplicitly() }
                    )
                }

                StudioBottomPanel.ADD_SOURCE -> {
                    AddSourceSheetContent(
                        uiState = uiState,
                        cameraState = cameraState,
                        screenCaptureState = screenCaptureState,
                        onAddSource = { type, resId -> viewModel.addSourceLayer(type, builtInRes = resId) },
                        onSelectLayer = { viewModel.selectLayer(it) },
                        onToggleVisibility = { viewModel.toggleLayerVisibility(it) },
                        onOpenLayerInspector = { id ->
                            viewModel.selectLayer(id)
                            viewModel.setBottomPanel(StudioBottomPanel.INSPECTOR)
                        },
                        onRequestScreenCapture = requestScreenCapture,
                        onStopScreenCapture = { viewModel.stopScreenCapture() },
                        onPickImageFile = { type ->
                            pendingImageSourceType = type
                            pendingAttachBorder = false
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onPickVideoFile = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        onDeleteLayer = { viewModel.deleteLayer(it) },
                        onRenameLayer = { id, name -> viewModel.renameLayer(id, name) }
                    )
                }

                StudioBottomPanel.SCENES -> {
                    SceneManagerSheetContent(
                        uiState = uiState,
                        onSelectScene = { viewModel.selectScene(it) },
                        onCreateScene = { name, preset -> viewModel.createNewScene(name, preset) },
                        onDuplicateScene = { viewModel.duplicateScene(it) },
                        onRenameScene = { id, name -> viewModel.renameScene(id, name) },
                        onDeleteScene = { viewModel.deleteScene(it) },
                        onApplyLayoutPreset = { viewModel.applyLayoutPreset(it) },
                        onUpdateCanvasPreset = { preset ->
                            viewModel.updateSceneCanvasResolution(preset, preset.defaultWidth, preset.defaultHeight)
                        },
                        onSelectTransition = { viewModel.setSceneTransitionType(it) },
                        onSaveSceneExplicitly = { viewModel.saveCurrentSceneExplicitly() },
                        onDeleteSourceFromScene = { sceneId, layerId ->
                            viewModel.deleteSourceFromScene(sceneId, layerId)
                        }
                    )
                }

                StudioBottomPanel.AUDIO -> {
                    AudioMixerSheetContent(
                        audioState = audioState,
                        onChannelVolumeChanged = { id, vol -> viewModel.audioMixer.setChannelVolume(id, vol) },
                        onToggleChannelMute = { id -> viewModel.audioMixer.toggleChannelMute(id) },
                        onMasterVolumeChanged = { vol -> viewModel.audioMixer.setChannelVolume("master", vol) },
                        onToggleMasterMute = { viewModel.audioMixer.toggleChannelMute("master") },
                        onToggleNoiseSuppression = {
                            viewModel.audioMixer.setNoiseSuppression(!audioState.noiseSuppressionEnabled)
                        },
                        onToggleEchoCancellation = {
                            viewModel.audioMixer.setEchoCancellation(!audioState.echoCancellationEnabled)
                        },
                        onMicSyncOffsetChanged = { ms -> viewModel.audioMixer.setAvSyncOffsetMs(ms) }
                    )
                }

                StudioBottomPanel.NONE -> Unit
            }
        }
    }
}

/** Persistent canvas shown while browsing every studio dock section. */
@Composable
private fun PersistentPreviewPane(
    uiState: StudioUiState,
    telemetry: StreamEngineTelemetry,
    compositor: com.example.compositor.SceneVideoCompositor,
    viewModel: SceneCastViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(StudioMainBg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "LIVE PROGRAM CANVAS",
                color = StudioTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            StudioStatusPill(
                label = if (telemetry.isStreaming) "ON AIR" else "PREVIEW",
                dotColor = if (telemetry.isStreaming) StudioLiveRed else StudioAccentSky
            )
        }
        SceneCanvasEditor(
            uiState = uiState,
            compositor = compositor,
            isStreaming = telemetry.isStreaming,
            onSelectLayer = { viewModel.selectLayer(it) },
            onMoveLayer = { id, dx, dy -> viewModel.moveLayerByDelta(id, dx, dy) },
            onResizeLayer = { id, handle, dx, dy -> viewModel.resizeLayerFromHandle(id, handle, dx, dy) },
            onGestureFinished = { viewModel.finishGestureAndPersist() },
            onUndo = { viewModel.undoSceneEdit() },
            onRedo = { viewModel.redoSceneEdit() },
            onToggleScaleMode = { viewModel.toggleLayerScaleMode(it) },
            onOpenLayerInspector = {
                viewModel.selectLayer(it)
                viewModel.setBottomPanel(StudioBottomPanel.INSPECTOR)
            },
            onToggleLockLayer = { viewModel.toggleLayerLock(it) },
            onDeleteLayer = { viewModel.deleteLayer(it) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Top Bar (Section 4):
 * - SceneCast Live logo/icon
 * - Current scene name (1-line)
 * - Connection status pill
 * - Compact Program Mode & Settings icon buttons (48dp)
 */
@Composable
private fun StudioTopBar(
    uiState: StudioUiState,
    telemetry: StreamEngineTelemetry,
    screenCaptureState: ScreenCaptureState,
    cameraState: CameraHardwareState,
    onEnterProgramMode: () -> Unit,
    onOpenSettingsTab: () -> Unit
) {
    Surface(
        color = StudioSecondaryBg,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .testTag("studio_top_bar")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                // Brand Icon + Title + Current Scene Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (telemetry.isStreaming) StudioLiveRed
                                else StudioAccentViolet
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SceneCast Live",
                            color = StudioTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.activeScene.name,
                            color = StudioTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Connection Status Pill + Program Monitor Icon + Settings Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val statusLabel = when {
                        telemetry.isStreaming -> "LIVE • ${formatDurationSec(telemetry.streamDurationSec)}"
                        screenCaptureState.isCapturing -> "Screen Live"
                        cameraState.isBound -> "Camera Ready"
                        else -> "Standby"
                    }
                    val statusColor = when {
                        telemetry.isStreaming -> StudioLiveRed
                        screenCaptureState.isCapturing -> StudioSuccessGreen
                        cameraState.isBound -> StudioAccentSky
                        else -> StudioTextSecondary
                    }

                    StudioStatusPill(
                        label = statusLabel,
                        dotColor = statusColor,
                        textColor = if (telemetry.isStreaming) StudioLiveRed else StudioTextPrimary
                    )

                    StudioIconButton(
                        icon = Icons.Default.Tv,
                        contentDescription = "Program Monitor Mode",
                        onClick = onEnterProgramMode,
                        testTag = "btn_program_mode"
                    )

                    StudioIconButton(
                        icon = Icons.Default.Settings,
                        contentDescription = "Studio Settings",
                        isActive = uiState.activeTab == StudioNavTab.SETTINGS,
                        onClick = onOpenSettingsTab,
                        testTag = "btn_top_settings"
                    )
                }
            }

            // Subtle bottom border line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(StudioBorder)
            )
        }
    }
}

/**
 * Vertical Phone Home Screen (`Studio` Tab, Section 4):
 * 1. Centered 16:9 Live Scene Preview + Contextual Floating Source Toolbar
 * 2. Compact 2×3 Source Shortcuts Grid (`PC Stream`, `Camera`, `Image`, `PNG Overlay`, `Video`, `Text`)
 * 3. Compact 4-Button Scene Controls Row (`Scenes`, `Layers`, `Audio`, `Layout`)
 * 4. Prominent `Start Streaming` / `Stop Streaming` Bottom Control + Stream Status Indicator
 */
@Composable
private fun PortraitStudioWorkspace(
    uiState: StudioUiState,
    telemetry: StreamEngineTelemetry,
    screenCaptureState: ScreenCaptureState,
    cameraState: CameraHardwareState,
    diagnostics: DeviceDiagnosticsState,
    compositor: com.example.compositor.SceneVideoCompositor,
    onSelectLayer: (String?) -> Unit,
    onMoveLayer: (String, Float, Float) -> Unit,
    onResizeLayer: (String, com.example.viewmodel.ResizeHandle, Float, Float) -> Unit,
    onGestureFinished: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleScaleMode: (String) -> Unit,
    onOpenLayerInspector: (String) -> Unit,
    onToggleLockLayer: (String) -> Unit,
    onDeleteLayer: (String) -> Unit,
    onQuickSelectOrAddSource: (SourceType) -> Unit,
    onPickImageFile: (SourceType) -> Unit,
    onPickVideoFile: () -> Unit,
    onOpenScenes: () -> Unit,
    onOpenLayersSheet: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenLayoutSheet: () -> Unit,
    onStartStream: () -> Unit,
    onStopStream: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("studio_home_screen")
    ) {
        // 1. Main 16:9 Live Scene Preview + Contextual Floating Toolbar when source is selected
        SceneCanvasEditor(
            uiState = uiState,
            compositor = compositor,
            isStreaming = telemetry.isStreaming,
            onSelectLayer = onSelectLayer,
            onMoveLayer = onMoveLayer,
            onResizeLayer = onResizeLayer,
            onGestureFinished = onGestureFinished,
            onUndo = onUndo,
            onRedo = onRedo,
            onToggleScaleMode = onToggleScaleMode,
            onOpenLayerInspector = onOpenLayerInspector,
            onToggleLockLayer = onToggleLockLayer,
            onDeleteLayer = onDeleteLayer
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
        // 2. Source Shortcuts Section (Screen, Camera, Image, PNG Overlay, Video, Text)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Sources",
                color = StudioTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = if (screenCaptureState.isCapturing) {
                    "Screen Capture: Active"
                } else {
                    "Tap Screen to capture phone display"
                },
                color = if (screenCaptureState.isCapturing) StudioSuccessGreen else StudioTextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        StudioSourceShortcutsGrid(
            cameraState = cameraState,
            screenCaptureState = screenCaptureState,
            onQuickSelectOrAddSource = onQuickSelectOrAddSource,
            onPickImageFile = onPickImageFile,
            onPickVideoFile = onPickVideoFile
        )

        // 3. Scene Controls Action Row (Section 4: Scenes, Layers, Audio, Layout — all 1-line, 48dp height)
        Text(
            text = "Scene Controls",
            color = StudioTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StudioSecondaryButton(
                text = "Scenes",
                icon = Icons.Default.GridView,
                onClick = onOpenScenes,
                modifier = Modifier.weight(1f),
                testTag = "btn_studio_scenes"
            )
            StudioSecondaryButton(
                text = "Layers",
                icon = Icons.Default.Layers,
                onClick = onOpenLayersSheet,
                modifier = Modifier.weight(1f),
                testTag = "btn_studio_layers"
            )
            StudioSecondaryButton(
                text = "Audio",
                icon = Icons.Default.Equalizer,
                onClick = onOpenAudio,
                modifier = Modifier.weight(1f),
                testTag = "btn_studio_audio"
            )
            StudioSecondaryButton(
                text = "Layout",
                icon = Icons.Default.DashboardCustomize,
                onClick = onOpenLayoutSheet,
                modifier = Modifier.weight(1f),
                testTag = "btn_studio_layout"
            )
        }

        // 4. Real-time Network Broadcast Stability Monitor Component (Bitrate, FPS, Packet Loss %)
        BroadcastNetworkMonitorCard(
            telemetry = telemetry,
            diagnostics = diagnostics
        )

        // 5. Bottom Control: Prominent Start Streaming / Stop Streaming + Compact Stream Status Indicator
        StudioCard(
            backgroundColor = StudioCardBg,
            borderColor = if (telemetry.isStreaming) StudioLiveRed else StudioBorder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (telemetry.isStreaming) StudioLiveRed else StudioSuccessGreen)
                    )
                    Text(
                        text = if (telemetry.isStreaming) {
                            "LIVE • ${formatDurationSec(telemetry.streamDurationSec)} • ${telemetry.currentUploadKbps} kbps"
                        } else {
                            "Ready • ${uiState.credentials.resolutionPreset.label}"
                        },
                        color = StudioTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "${telemetry.encodedFps} FPS • ${diagnostics.batteryPercent}%",
                    color = StudioTextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
            }

            if (!telemetry.isStreaming) {
                StudioPrimaryButton(
                    text = "Start Streaming",
                    icon = Icons.Default.RadioButtonChecked,
                    containerColor = StudioAccentViolet,
                    onClick = onStartStream,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "btn_primary_go_live"
                )
            } else {
                StudioDangerButton(
                    text = "Stop Streaming",
                    icon = Icons.Default.Stop,
                    onClick = onStopStream,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "btn_primary_go_live"
                )
            }
        }
        }
    }
}

/**
 * Quick Layout Preset Sheet (Opened when user taps `Layout` in Scene Controls)
 */
@Composable
private fun QuickLayoutPresetSheet(
    activeSceneName: String,
    onApplyLayoutPreset: (LayoutPreset) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("quick_layout_preset_sheet")
    ) {
        Text(
            text = "Scene Layout Presets",
            color = StudioTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = "Apply instant Screen + Camera proportions to '$activeSceneName'",
            color = StudioTextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val presets = listOf(
            Triple(LayoutPreset.SCREEN_50_CAM_50, "50% Screen / 50% Camera", "Equal side-by-side split"),
            Triple(LayoutPreset.SCREEN_60_CAM_40, "60% Screen / 40% Camera", "Wide screen capture + portrait camera"),
            Triple(LayoutPreset.SCREEN_65_CAM_35, "65% Screen / 35% Camera", "Recommended broadcast split"),
            Triple(LayoutPreset.SCREEN_70_CAM_30, "70% Screen / 30% Camera", "Gameplay focus + compact camera"),
            Triple(LayoutPreset.SCREEN_FULL_CAM_PIP, "Full Screen + Camera PiP", "100% screen capture with corner camera"),
            Triple(LayoutPreset.CAM_LEFT_SCREEN_RIGHT, "Camera Left + Screen Right", "Camera left 35%, Screen right 65%"),
            Triple(LayoutPreset.SCREEN_TOP_CAM_BOTTOM, "Screen Top + Camera Bottom", "Screen top 65%, Camera bottom 35%"),
            Triple(LayoutPreset.CUSTOM, "Freeform Custom", "Unlock all windows for free positioning")
        )

        presets.chunked(2).forEach { rowPresets ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowPresets.forEach { (preset, shortLabel, desc) ->
                    StudioCard(
                        backgroundColor = StudioCardBg,
                        borderColor = StudioBorder,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onApplyLayoutPreset(preset) }
                            .testTag("sheet_preset_${preset.name.lowercase()}")
                    ) {
                        Text(
                            text = shortLabel,
                            color = StudioTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = desc,
                            color = StudioTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Simple Bottom Navigation Bar with 5 Destinations (Section 8):
 * 1. Studio
 * 2. Scenes
 * 3. Sources
 * 4. Audio
 * 5. Settings
 */
@Composable
private fun StudioBottomNavigationBar(
    activeTab: StudioNavTab,
    isStreaming: Boolean,
    onSelectTab: (StudioNavTab) -> Unit
) {
    val navItems: List<Pair<StudioNavTab, ImageVector>> = listOf(
        StudioNavTab.STUDIO to Icons.Default.Videocam,
        StudioNavTab.SCENES to Icons.Default.GridView,
        StudioNavTab.SOURCES to Icons.Default.AddBox,
        StudioNavTab.AUDIO to Icons.Default.Equalizer,
        StudioNavTab.SETTINGS to Icons.Default.RadioButtonChecked
    )

    NavigationBar(
        containerColor = StudioSecondaryBg,
        contentColor = StudioTextPrimary,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets.navigationBars,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = StudioBorder)
            .testTag("studio_bottom_dock")
    ) {
        navItems.forEach { (tab, icon) ->
            val selected = activeTab == tab
            val testTag = when (tab) {
                StudioNavTab.STUDIO -> "dock_studio"
                StudioNavTab.SCENES -> "dock_scenes"
                StudioNavTab.SOURCES -> "dock_add_source"
                StudioNavTab.AUDIO -> "dock_audio"
                StudioNavTab.SETTINGS -> "dock_stream"
            }

            NavigationBarItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(20.dp)
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = if (tab == StudioNavTab.STUDIO && isStreaming) StudioLiveRed else StudioAccentViolet,
                    selectedTextColor = StudioTextPrimary,
                    indicatorColor = StudioElevatedBg,
                    unselectedIconColor = StudioTextSecondary,
                    unselectedTextColor = StudioTextSecondary
                ),
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

@Composable
private fun ProgramCleanMonitorView(
    uiState: StudioUiState,
    telemetry: StreamEngineTelemetry,
    compositor: com.example.compositor.SceneVideoCompositor,
    onExitProgramMode: () -> Unit,
    onTogglePillVisibility: () -> Unit,
    onStopStream: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onTogglePillVisibility() }
            .testTag("program_clean_monitor_root")
    ) {
        SceneCanvasEditor(
            uiState = uiState,
            compositor = compositor,
            isStreaming = telemetry.isStreaming,
            onSelectLayer = {},
            onMoveLayer = { _, _, _ -> },
            onResizeLayer = { _, _, _, _ -> },
            onGestureFinished = {},
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = uiState.showProgramFloatingPill,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(StudioSecondaryBg.copy(alpha = 0.92f))
                    .border(1.dp, StudioBorder, RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("program_floating_pill")
            ) {
                StudioStatusPill(
                    label = if (telemetry.isStreaming) "LIVE ${formatDurationSec(telemetry.streamDurationSec)}" else "CLEAN OUTPUT",
                    dotColor = if (telemetry.isStreaming) StudioLiveRed else StudioSuccessGreen
                )

                if (telemetry.isStreaming) {
                    BroadcastCompactNetworkPill(telemetry = telemetry)
                }

                StudioSecondaryButton(
                    text = "Edit Studio",
                    onClick = onExitProgramMode,
                    testTag = "btn_exit_program_mode"
                )

                if (telemetry.isStreaming) {
                    StudioDangerButton(
                        text = "Stop",
                        onClick = onStopStream,
                        testTag = "btn_program_stop_stream"
                    )
                }
            }
        }
    }
}
