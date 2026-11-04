package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.audio.AudioMixerEngine
import com.example.audio.AudioMixerState
import com.example.compositor.SceneVideoCompositor
import com.example.model.BackgroundSourceConfig
import com.example.model.BorderStyle
import com.example.model.CameraFacing
import com.example.model.CameraSourceConfig
import com.example.model.CanvasPreset
import com.example.model.CropRect
import com.example.model.LayoutPreset
import com.example.model.MediaSourceConfig
import com.example.model.NetworkSourceConfig
import com.example.model.ScaleMode
import com.example.model.Scene
import com.example.model.SceneLayer
import com.example.model.SceneTransitionType
import com.example.model.ScreenCaptureMode
import com.example.model.ScreenCaptureSourceConfig
import com.example.model.SourceType
import com.example.model.TextSourceConfig
import com.example.monitoring.DeviceDiagnosticsState
import com.example.monitoring.StreamMonitorManager
import com.example.sources.CameraCaptureManager
import com.example.sources.CameraHardwareState
import com.example.sources.NetworkFeedStatus
import com.example.sources.ScreenCaptureManager
import com.example.sources.ScreenCaptureState
import com.example.sources.VideoSourceManager
import com.example.sources.MoonlightPcDiscovery
import com.example.sources.MoonlightSessionController
import com.example.storage.SceneStorageManager
import com.example.storage.SecureStreamCredentialStore
import com.example.storage.StreamCredentials
import com.example.storage.StreamQualityPreset
import com.example.streaming.RtmpStreamingEngine
import com.example.streaming.SceneCastStreamingService
import com.example.streaming.StreamEngineTelemetry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class StudioNavTab(val label: String) {
    STUDIO("Studio"),
    SCENES("Scenes"),
    SOURCES("Sources"),
    AUDIO("Audio"),
    SETTINGS("Stream")
}

enum class StudioBottomPanel {
    NONE,
    ADD_SOURCE,
    SCENES,
    LAYERS,
    INSPECTOR,
    AUDIO,
    STREAM_SETTINGS,
    LAYOUT_PRESETS
}

enum class ResizeHandle {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM
}

data class StudioUiState(
    val scenes: List<Scene> = emptyList(),
    val activeSceneId: String = "",
    val outgoingScene: Scene? = null,
    val transitionProgress: Float = 1f,
    val transitionType: SceneTransitionType = SceneTransitionType.FADE,
    val selectedLayerId: String? = null,
    val isProgramMode: Boolean = false,
    val showProgramFloatingPill: Boolean = true,
    val activeTab: StudioNavTab = StudioNavTab.STUDIO,
    val activeBottomPanel: StudioBottomPanel = StudioBottomPanel.NONE,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val centerSnapEnabled: Boolean = true,
    val gridSnapEnabled: Boolean = false,
    val safeAreaGuidesEnabled: Boolean = true,
    val activeSnapVerticalX: Float? = null,
    val activeSnapHorizontalY: Float? = null,
    val credentials: StreamCredentials = StreamCredentials(),
    val keepScreenAwake: Boolean = true,
    val lockLandscapeOrientation: Boolean = false,
    val statusBannerMessage: String? = null,
    val frameTick: Long = 0L
) {
    val activeScene: Scene
        get() = scenes.find { it.id == activeSceneId }
            ?: scenes.firstOrNull()
            ?: Scene(name = "Default 16:9 Scene")

    val selectedLayer: SceneLayer?
        get() = activeScene.layers.find { it.id == selectedLayerId }
}

class SceneCastViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    val sceneStorage = SceneStorageManager(appContext)
    val credentialStore = SecureStreamCredentialStore(appContext)

    val cameraManager = CameraCaptureManager(appContext)
    val screenCaptureManager = ScreenCaptureManager(appContext)
    val audioMixer = AudioMixerEngine()
    val sourceManager = VideoSourceManager(appContext, cameraManager, screenCaptureManager)
    val moonlightDiscovery = MoonlightPcDiscovery(appContext)
    val moonlightSession = MoonlightSessionController(appContext, sourceManager)

    fun startMoonlightPcDiscovery() = moonlightDiscovery.start()
    fun stopMoonlightPcDiscovery() = moonlightDiscovery.stop()
    fun connectMoonlight(activity: android.app.Activity, host: String, layerId: String, httpPort: Int) {
        viewModelScope.launch { moonlightSession.connect(activity, host, layerId, httpPort) }
    }
    val compositor = SceneVideoCompositor(appContext, sourceManager)
    val streamingEngine = RtmpStreamingEngine(compositor, audioMixer)
    val monitorManager = StreamMonitorManager(appContext)

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    val cameraState: StateFlow<CameraHardwareState> = cameraManager.hardwareState
    val screenCaptureState: StateFlow<ScreenCaptureState> = screenCaptureManager.state
    val networkFeedState: StateFlow<NetworkFeedStatus> = sourceManager.networkStatus
    val audioState: StateFlow<AudioMixerState> = audioMixer.state
    val streamTelemetry: StateFlow<StreamEngineTelemetry> = streamingEngine.telemetry
    val deviceDiagnostics: StateFlow<DeviceDiagnosticsState> = monitorManager.diagnostics

    private var transitionJob: Job? = null
    private var bannerJob: Job? = null
    private val undoStack = ArrayDeque<Scene>()
    private val redoStack = ArrayDeque<Scene>()
    private var gestureSnapshotCaptured = false

    init {
        val loadedScenes = sceneStorage.loadScenes()
        val savedActiveId = sceneStorage.getActiveSceneId(loadedScenes)
        val initialScene = loadedScenes.find { it.id == savedActiveId } ?: loadedScenes.first()
        val initialLayerId = initialScene.layers.firstOrNull()?.id
        val savedCreds = credentialStore.loadCredentials()

        _uiState.value = StudioUiState(
            scenes = loadedScenes,
            activeSceneId = initialScene.id,
            selectedLayerId = initialLayerId,
            credentials = savedCreds
        )

        streamingEngine.bindSceneProviders(
            sceneProvider = { _uiState.value.activeScene },
            transitionProvider = {
                val s = _uiState.value
                Triple(s.outgoingScene, s.transitionProgress, s.transitionType)
            }
        )

        SceneCastStreamingService.stopBroadcastCallbacks = {
            stopLiveStream()
            screenCaptureManager.stopCapture()
        }

        monitorManager.startMonitoring()

        // 30 FPS UI preview frame ticker so live CameraX, Screen Capture, and transitions repaint smoothly
        viewModelScope.launch {
            while (true) {
                delay(33L)
                _uiState.value = _uiState.value.copy(frameTick = _uiState.value.frameTick + 1L)
            }
        }
    }

    fun bindLifecycle(lifecycleOwner: LifecycleOwner, hasCameraPermission: Boolean, hasMicPermission: Boolean) {
        if (hasCameraPermission) {
            val camLayer = _uiState.value.activeScene.layers.find { it.type == SourceType.CAMERA }
            val cfg = camLayer?.cameraConfig ?: CameraSourceConfig()
            cameraManager.startOrUpdateCamera(lifecycleOwner, cfg)
        }
        if (hasMicPermission) {
            audioMixer.startAudioEngine(screenCaptureManager.getMediaProjection())
        }
    }

    fun showStatusBanner(message: String) {
        bannerJob?.cancel()
        _uiState.value = _uiState.value.copy(statusBannerMessage = message)
        bannerJob = viewModelScope.launch {
            delay(2800L)
            _uiState.value = _uiState.value.copy(statusBannerMessage = null)
        }
    }

    fun dismissStatusBanner() {
        bannerJob?.cancel()
        _uiState.value = _uiState.value.copy(statusBannerMessage = null)
    }

    fun setActiveTab(tab: StudioNavTab) {
        _uiState.value = _uiState.value.copy(
            activeTab = tab,
            activeBottomPanel = StudioBottomPanel.NONE
        )
    }

    fun setBottomPanel(panel: StudioBottomPanel) {
        val current = _uiState.value.activeBottomPanel
        _uiState.value = _uiState.value.copy(
            activeBottomPanel = if (current == panel) StudioBottomPanel.NONE else panel
        )
    }

    fun closeBottomPanel() {
        _uiState.value = _uiState.value.copy(activeBottomPanel = StudioBottomPanel.NONE)
    }

    // --- UNDO / REDO SCENE HISTORY ---

    private fun recordUndoSnapshot(scene: Scene) {
        if (undoStack.lastOrNull() == scene) return
        if (undoStack.size >= 25) {
            undoStack.removeFirst()
        }
        undoStack.addLast(scene)
        redoStack.clear()
    }

    fun beginGestureSnapshot() {
        if (!gestureSnapshotCaptured) {
            recordUndoSnapshot(_uiState.value.activeScene)
            gestureSnapshotCaptured = true
            _uiState.value = _uiState.value.copy(
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun undoSceneEdit() {
        if (undoStack.isEmpty()) return
        val current = _uiState.value.activeScene
        val previous = undoStack.removeLast()
        redoStack.addLast(current)
        val updatedScenes = _uiState.value.scenes.map {
            if (it.id == previous.id) previous else it
        }
        _uiState.value = _uiState.value.copy(
            scenes = updatedScenes,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
        sceneStorage.saveScenes(updatedScenes)
        showStatusBanner("Undid scene edit")
    }

    fun redoSceneEdit() {
        if (redoStack.isEmpty()) return
        val current = _uiState.value.activeScene
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        val updatedScenes = _uiState.value.scenes.map {
            if (it.id == next.id) next else it
        }
        _uiState.value = _uiState.value.copy(
            scenes = updatedScenes,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
        sceneStorage.saveScenes(updatedScenes)
        showStatusBanner("Redid scene edit")
    }

    fun toggleLayerScaleMode(layerId: String) {
        updateLayer(layerId) { layer ->
            val nextScale = when (layer.scaleMode) {
                ScaleMode.FIT -> ScaleMode.FILL
                ScaleMode.FILL -> ScaleMode.STRETCH
                ScaleMode.STRETCH -> ScaleMode.FIT
            }
            layer.copy(scaleMode = nextScale)
        }
    }

    // --- EDIT MODE VS CLEAN PROGRAM / LIVE MODE ---

    fun setProgramMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            isProgramMode = enabled,
            showProgramFloatingPill = true,
            activeBottomPanel = StudioBottomPanel.NONE,
            activeSnapVerticalX = null,
            activeSnapHorizontalY = null
        )
    }

    fun toggleProgramFloatingPill() {
        _uiState.value = _uiState.value.copy(
            showProgramFloatingPill = !_uiState.value.showProgramFloatingPill
        )
    }

    // --- SCENE MANAGEMENT & TRANSITIONS ---

    fun selectScene(sceneId: String) {
        val state = _uiState.value
        if (state.activeSceneId == sceneId) return
        val currentScene = state.activeScene
        val targetScene = state.scenes.find { it.id == sceneId } ?: return

        sceneStorage.saveActiveSceneId(sceneId)

        if (state.transitionType == SceneTransitionType.CUT) {
            _uiState.value = state.copy(
                activeSceneId = sceneId,
                outgoingScene = null,
                transitionProgress = 1f,
                selectedLayerId = targetScene.layers.firstOrNull()?.id
            )
            return
        }

        transitionJob?.cancel()
        val durationMs = state.transitionType.durationMs.coerceAtLeast(100L)
        _uiState.value = state.copy(
            activeSceneId = sceneId,
            outgoingScene = currentScene,
            transitionProgress = 0f,
            selectedLayerId = targetScene.layers.firstOrNull()?.id
        )

        transitionJob = viewModelScope.launch {
            val steps = 16
            val stepDelay = durationMs / steps
            for (i in 1..steps) {
                delay(stepDelay)
                _uiState.value = _uiState.value.copy(
                    transitionProgress = i.toFloat() / steps.toFloat()
                )
            }
            _uiState.value = _uiState.value.copy(
                outgoingScene = null,
                transitionProgress = 1f
            )
        }
    }

    fun setSceneTransitionType(type: SceneTransitionType) {
        _uiState.value = _uiState.value.copy(transitionType = type)
    }

    fun createNewScene(
        name: String,
        preset: CanvasPreset = CanvasPreset.LANDSCAPE_16_9,
        customW: Int = preset.defaultWidth,
        customH: Int = preset.defaultHeight
    ) {
        val newScene = Scene(
            name = name.ifBlank { "Scene ${_uiState.value.scenes.size + 1}" },
            canvasPreset = preset,
            canvasWidth = customW.coerceIn(360, 3840),
            canvasHeight = customH.coerceIn(360, 3840),
            layers = listOf(
                SceneLayer(
                    name = "Screen / App Capture",
                    type = SourceType.SCREEN_CAPTURE,
                    x = 0f,
                    y = 0f,
                    width = 0.65f,
                    height = 1.0f,
                    zIndex = 0,
                    scaleMode = ScaleMode.FIT,
                    border = BorderStyle(enabled = false)
                ),
                SceneLayer(
                    name = "Front Camera",
                    type = SourceType.CAMERA,
                    x = 0.65f,
                    y = 0f,
                    width = 0.35f,
                    height = 1.0f,
                    zIndex = 1,
                    scaleMode = ScaleMode.FILL,
                    border = BorderStyle(enabled = false)
                )
            )
        )
        val updated = _uiState.value.scenes + newScene
        persistScenesAndSelect(updated, newScene.id, newScene.layers.firstOrNull()?.id)
        showStatusBanner("Created scene '${newScene.name}'")
    }

    fun duplicateScene(sceneId: String) {
        val orig = _uiState.value.scenes.find { it.id == sceneId } ?: return
        val copyScene = orig.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = "${orig.name} Copy",
            isSystemPreset = false,
            layers = orig.layers.map { it.copy(id = java.util.UUID.randomUUID().toString()) }
        )
        val updated = _uiState.value.scenes + copyScene
        persistScenesAndSelect(updated, copyScene.id, copyScene.layers.firstOrNull()?.id)
        showStatusBanner("Duplicated '${orig.name}'")
    }

    fun renameScene(sceneId: String, newName: String) {
        if (newName.isBlank()) return
        val updated = _uiState.value.scenes.map {
            if (it.id == sceneId) it.copy(name = newName.trim()) else it
        }
        persistScenesAndSelect(updated, _uiState.value.activeSceneId, _uiState.value.selectedLayerId)
    }

    fun deleteScene(sceneId: String) {
        val currentList = _uiState.value.scenes
        if (currentList.size <= 1) {
            showStatusBanner("At least one scene must remain in the project")
            return
        }
        val remaining = currentList.filterNot { it.id == sceneId }
        val nextActiveId = if (_uiState.value.activeSceneId == sceneId) {
            remaining.first().id
        } else {
            _uiState.value.activeSceneId
        }
        persistScenesAndSelect(remaining, nextActiveId, remaining.first().layers.firstOrNull()?.id)
        showStatusBanner("Scene deleted")
    }

    fun updateSceneCanvasResolution(preset: CanvasPreset, width: Int, height: Int) {
        val safeW = ((width.coerceIn(360, 3840)) / 2) * 2
        val safeH = ((height.coerceIn(360, 3840)) / 2) * 2
        updateActiveScene { scene ->
            scene.copy(canvasPreset = preset, canvasWidth = safeW, canvasHeight = safeH)
        }
    }

    fun updateSceneBackground(
        primaryHex: Long,
        secondaryHex: Long,
        useGradient: Boolean,
        transparent: Boolean
    ) {
        updateActiveScene { scene ->
            scene.copy(
                backgroundColorHex = primaryHex,
                backgroundGradientEndHex = secondaryHex,
                useGradientBackground = useGradient,
                transparentBackground = transparent
            )
        }
    }

    fun applyLayoutPreset(preset: LayoutPreset) {
        updateActiveScene { scene ->
            sceneStorage.applyLayoutPresetToScene(scene, preset)
        }
        showStatusBanner("Applied layout: ${preset.title}")
    }

    fun saveCurrentSceneExplicitly() {
        sceneStorage.saveScenes(_uiState.value.scenes)
        sceneStorage.saveActiveSceneId(_uiState.value.activeSceneId)
        showStatusBanner("Scene '${_uiState.value.activeScene.name}' saved to local storage")
    }

    // --- LAYER & SOURCE MANAGEMENT ---

    fun selectLayer(layerId: String?) {
        _uiState.value = _uiState.value.copy(selectedLayerId = layerId)
    }

    fun addSourceLayer(
        type: SourceType,
        customName: String? = null,
        localFilePath: String? = null,
        builtInRes: Int? = null
    ) {
        val active = _uiState.value.activeScene
        val nextZ = (active.layers.maxOfOrNull { it.zIndex } ?: -1) + 1
        val defaultLayer = when (type) {
            SourceType.SCREEN_CAPTURE -> SceneLayer(
                name = customName ?: "Screen / App Capture",
                type = SourceType.SCREEN_CAPTURE,
                x = 0.0f,
                y = 0.0f,
                width = 0.60f,
                height = 1.0f,
                zIndex = nextZ,
                scaleMode = ScaleMode.FIT,
                border = BorderStyle(enabled = false)
            )
            SourceType.CAMERA -> SceneLayer(
                name = customName ?: "Front Camera",
                type = SourceType.CAMERA,
                x = 0.65f,
                y = 0.0f,
                width = 0.35f,
                height = 1.0f,
                zIndex = nextZ,
                border = BorderStyle(enabled = false)
            )
            SourceType.MOONLIGHT_PC -> SceneLayer(
                name = customName ?: "Moonlight PC",
                type = SourceType.MOONLIGHT_PC,
                x = 0.05f,
                y = 0.05f,
                width = 0.65f,
                height = 0.65f,
                zIndex = nextZ,
                scaleMode = ScaleMode.FIT
            )
            SourceType.NETWORK_VIDEO -> SceneLayer(
                name = customName ?: "PC LAN Stream",
                type = SourceType.NETWORK_VIDEO,
                x = 0.05f,
                y = 0.05f,
                width = 0.55f,
                height = 0.55f,
                zIndex = nextZ,
                scaleMode = ScaleMode.FIT
            )
            SourceType.PNG_OVERLAY -> SceneLayer(
                name = customName ?: "Transparent PNG Frame",
                type = SourceType.PNG_OVERLAY,
                x = 0.58f,
                y = 0.02f,
                width = 0.40f,
                height = 0.96f,
                zIndex = nextZ,
                scaleMode = ScaleMode.STRETCH,
                border = BorderStyle(enabled = false, cornerRadiusPercent = 0f, shadowEnabled = false),
                mediaConfig = MediaSourceConfig(
                    localFilePath = localFilePath,
                    builtInDrawableRes = builtInRes ?: R.drawable.img_frame_cyber_cyan
                )
            )
            SourceType.IMAGE -> SceneLayer(
                name = customName ?: "Image Graphic",
                type = SourceType.IMAGE,
                x = 0.15f,
                y = 0.15f,
                width = 0.35f,
                height = 0.35f,
                zIndex = nextZ,
                scaleMode = ScaleMode.FIT,
                mediaConfig = MediaSourceConfig(
                    localFilePath = localFilePath,
                    builtInDrawableRes = builtInRes ?: R.drawable.img_frame_broadcast_gold
                )
            )
            SourceType.VIDEO_FILE -> SceneLayer(
                name = customName ?: "Video Clip",
                type = SourceType.VIDEO_FILE,
                x = 0.10f,
                y = 0.10f,
                width = 0.45f,
                height = 0.45f,
                zIndex = nextZ,
                mediaConfig = MediaSourceConfig(localFilePath = localFilePath)
            )
            SourceType.TEXT -> SceneLayer(
                name = customName ?: "Stream Label",
                type = SourceType.TEXT,
                x = 0.04f,
                y = 0.84f,
                width = 0.42f,
                height = 0.12f,
                zIndex = nextZ,
                border = BorderStyle(enabled = true, thicknessDp = 2f, colorHex = 0xFF00E5FF, cornerRadiusPercent = 18f),
                textConfig = TextSourceConfig(text = "LIVE • SCREEN + CAM")
            )
            SourceType.LOGO -> SceneLayer(
                name = customName ?: "Stream Watermark",
                type = SourceType.LOGO,
                x = 0.02f,
                y = 0.03f,
                width = 0.12f,
                height = 0.12f,
                zIndex = nextZ,
                scaleMode = ScaleMode.FIT,
                border = BorderStyle(enabled = false, cornerRadiusPercent = 0f, shadowEnabled = false),
                mediaConfig = MediaSourceConfig(
                    localFilePath = localFilePath,
                    builtInDrawableRes = builtInRes ?: R.drawable.img_badge_live_studio
                )
            )
            SourceType.BACKGROUND -> SceneLayer(
                name = customName ?: "Backdrop Gradient",
                type = SourceType.BACKGROUND,
                x = 0f,
                y = 0f,
                width = 1f,
                height = 1f,
                zIndex = 0,
                border = BorderStyle(enabled = false, cornerRadiusPercent = 0f, shadowEnabled = false),
                backgroundConfig = BackgroundSourceConfig(imagePath = localFilePath)
            )
        }

        updateActiveScene(selectLayerId = defaultLayer.id) { scene ->
            val all = if (type == SourceType.BACKGROUND) {
                (listOf(defaultLayer) + scene.layers).mapIndexed { idx, l -> l.copy(zIndex = idx) }
            } else {
                (scene.layers + defaultLayer).mapIndexed { idx, l -> l.copy(zIndex = idx) }
            }
            scene.copy(layers = all)
        }
        showStatusBanner("Added ${defaultLayer.name}")
    }

    fun importAssetFromUri(uri: Uri, targetType: SourceType, attachToSelectedBorder: Boolean = false) {
        val ext = if (targetType == SourceType.VIDEO_FILE) "mp4" else "png"
        val path = sceneStorage.copyImportedMediaUriToInternalStorage(uri, extensionHint = ext)
        if (path == null) {
            showStatusBanner("Could not import selected media file")
            return
        }
        if (attachToSelectedBorder) {
            val selected = _uiState.value.selectedLayer ?: return
            updateLayer(selected.id) { layer ->
                layer.copy(
                    border = layer.border.copy(
                        customPngFramePath = path,
                        builtInPngFrameRes = null
                    )
                )
            }
            showStatusBanner("Applied custom PNG frame to ${selected.name}")
        } else {
            addSourceLayer(targetType, localFilePath = path)
        }
    }

    fun moveLayerByDelta(layerId: String, deltaNormX: Float, deltaNormY: Float) {
        val state = _uiState.value
        val layer = state.activeScene.layers.find { it.id == layerId } ?: return
        if (layer.locked) return
        beginGestureSnapshot()

        var newX = (layer.x + deltaNormX).coerceIn(-0.4f, 0.95f)
        var newY = (layer.y + deltaNormY).coerceIn(-0.4f, 0.95f)

        var snapVert: Float? = null
        var snapHoriz: Float? = null

        if (state.gridSnapEnabled) {
            newX = (Math.round(newX * 20f) / 20f)
            newY = (Math.round(newY * 20f) / 20f)
        }

        if (state.centerSnapEnabled) {
            val centerX = newX + layer.width / 2f
            val centerY = newY + layer.height / 2f
            if (abs(centerX - 0.5f) < 0.018f) {
                newX = 0.5f - layer.width / 2f
                snapVert = 0.5f
            } else if (abs(newX) < 0.015f) {
                newX = 0f
                snapVert = 0f
            } else if (abs((newX + layer.width) - 1f) < 0.015f) {
                newX = 1f - layer.width
                snapVert = 1f
            }

            if (abs(centerY - 0.5f) < 0.018f) {
                newY = 0.5f - layer.height / 2f
                snapHoriz = 0.5f
            } else if (abs(newY) < 0.015f) {
                newY = 0f
                snapHoriz = 0f
            } else if (abs((newY + layer.height) - 1f) < 0.015f) {
                newY = 1f - layer.height
                snapHoriz = 1f
            }
        }

        _uiState.value = _uiState.value.copy(
            activeSnapVerticalX = snapVert,
            activeSnapHorizontalY = snapHoriz
        )
        updateLayer(layerId, persistImmediately = false) {
            it.copy(x = newX, y = newY)
        }
    }

    fun resizeLayerFromHandle(
        layerId: String,
        handle: ResizeHandle,
        deltaNormX: Float,
        deltaNormY: Float
    ) {
        val layer = _uiState.value.activeScene.layers.find { it.id == layerId } ?: return
        if (layer.locked) return
        beginGestureSnapshot()

        val minSize = 0.06f
        val aspect = (layer.width / layer.height.coerceAtLeast(0.05f)).coerceIn(0.2f, 5f)

        var newX = layer.x
        var newY = layer.y
        var newW = layer.width
        var newH = layer.height

        when (handle) {
            ResizeHandle.RIGHT -> {
                newW = (layer.width + deltaNormX).coerceIn(minSize, 1.5f)
                if (layer.lockAspectRatio) newH = (newW / aspect).coerceIn(minSize, 1.5f)
            }
            ResizeHandle.LEFT -> {
                val candidateW = (layer.width - deltaNormX).coerceIn(minSize, 1.5f)
                newX = layer.x + (layer.width - candidateW)
                newW = candidateW
                if (layer.lockAspectRatio) newH = (newW / aspect).coerceIn(minSize, 1.5f)
            }
            ResizeHandle.BOTTOM -> {
                newH = (layer.height + deltaNormY).coerceIn(minSize, 1.5f)
                if (layer.lockAspectRatio) newW = (newH * aspect).coerceIn(minSize, 1.5f)
            }
            ResizeHandle.TOP -> {
                val candidateH = (layer.height - deltaNormY).coerceIn(minSize, 1.5f)
                newY = layer.y + (layer.height - candidateH)
                newH = candidateH
                if (layer.lockAspectRatio) newW = (newH * aspect).coerceIn(minSize, 1.5f)
            }
            ResizeHandle.BOTTOM_RIGHT -> {
                newW = (layer.width + deltaNormX).coerceIn(minSize, 1.5f)
                newH = if (layer.lockAspectRatio) {
                    (newW / aspect).coerceIn(minSize, 1.5f)
                } else {
                    (layer.height + deltaNormY).coerceIn(minSize, 1.5f)
                }
            }
            ResizeHandle.BOTTOM_LEFT -> {
                val candidateW = (layer.width - deltaNormX).coerceIn(minSize, 1.5f)
                newX = layer.x + (layer.width - candidateW)
                newW = candidateW
                newH = if (layer.lockAspectRatio) {
                    (newW / aspect).coerceIn(minSize, 1.5f)
                } else {
                    (layer.height + deltaNormY).coerceIn(minSize, 1.5f)
                }
            }
            ResizeHandle.TOP_RIGHT -> {
                newW = (layer.width + deltaNormX).coerceIn(minSize, 1.5f)
                val candidateH = if (layer.lockAspectRatio) {
                    (newW / aspect).coerceIn(minSize, 1.5f)
                } else {
                    (layer.height - deltaNormY).coerceIn(minSize, 1.5f)
                }
                newY = layer.y + (layer.height - candidateH)
                newH = candidateH
            }
            ResizeHandle.TOP_LEFT -> {
                val candidateW = (layer.width - deltaNormX).coerceIn(minSize, 1.5f)
                val candidateH = if (layer.lockAspectRatio) {
                    (candidateW / aspect).coerceIn(minSize, 1.5f)
                } else {
                    (layer.height - deltaNormY).coerceIn(minSize, 1.5f)
                }
                newX = layer.x + (layer.width - candidateW)
                newY = layer.y + (layer.height - candidateH)
                newW = candidateW
                newH = candidateH
            }
        }

        updateLayer(layerId, persistImmediately = false) {
            it.copy(x = newX, y = newY, width = newW, height = newH)
        }
    }

    fun finishGestureAndPersist() {
        gestureSnapshotCaptured = false
        _uiState.value = _uiState.value.copy(
            activeSnapVerticalX = null,
            activeSnapHorizontalY = null,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
        sceneStorage.saveScenes(_uiState.value.scenes)
    }

    fun setLayerExactPercentageWidth(layerId: String, widthPercent: Int, autoFitRemainingCamera: Boolean = true) {
        val ratio = (widthPercent.coerceIn(5, 100)) / 100f
        updateActiveScene { scene ->
            val target = scene.layers.find { it.id == layerId } ?: return@updateActiveScene scene
            val updatedLayers = scene.layers.map { l ->
                when {
                    l.id == layerId -> l.copy(x = 0f, y = 0f, width = ratio, height = 1f)
                    autoFitRemainingCamera &&
                        target.type == SourceType.SCREEN_CAPTURE &&
                        l.type == SourceType.CAMERA &&
                        ratio < 0.95f -> {
                        l.copy(x = ratio, y = 0f, width = (1f - ratio).coerceAtLeast(0.1f), height = 1f)
                    }
                    else -> l
                }
            }
            scene.copy(layers = updatedLayers)
        }
    }

    fun bringLayerForward(layerId: String) {
        reorderLayerRelative(layerId, +1)
    }

    fun sendLayerBackward(layerId: String) {
        reorderLayerRelative(layerId, -1)
    }

    private fun reorderLayerRelative(layerId: String, direction: Int) {
        updateActiveScene { scene ->
            val sorted = scene.sortedLayers().toMutableList()
            val idx = sorted.indexOfFirst { it.id == layerId }
            if (idx == -1) return@updateActiveScene scene
            val targetIdx = (idx + direction).coerceIn(0, sorted.lastIndex)
            if (idx == targetIdx) return@updateActiveScene scene
            val item = sorted.removeAt(idx)
            sorted.add(targetIdx, item)
            scene.copy(layers = sorted.mapIndexed { i, l -> l.copy(zIndex = i) })
        }
    }

    fun duplicateLayer(layerId: String) {
        val orig = _uiState.value.activeScene.layers.find { it.id == layerId } ?: return
        val copy = orig.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = "${orig.name} Copy",
            x = (orig.x + 0.04f).coerceAtMost(0.85f),
            y = (orig.y + 0.04f).coerceAtMost(0.85f),
            zIndex = _uiState.value.activeScene.layers.size,
            locked = false
        )
        updateActiveScene(selectLayerId = copy.id) { scene ->
            scene.copy(layers = scene.layers + copy)
        }
    }

    fun deleteLayer(layerId: String) {
        updateActiveScene { scene ->
            val filtered = scene.layers.filterNot { it.id == layerId }
                .mapIndexed { idx, l -> l.copy(zIndex = idx) }
            scene.copy(layers = filtered)
        }
    }

    fun renameLayer(layerId: String, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        updateLayer(layerId) { it.copy(name = clean) }
    }

    fun deleteSourceFromScene(sceneId: String, layerId: String) {
        if (sceneId == _uiState.value.activeSceneId) {
            deleteLayer(layerId)
        } else {
            val updated = _uiState.value.scenes.map { scene ->
                if (scene.id == sceneId) {
                    val filtered = scene.layers.filterNot { it.id == layerId }
                        .mapIndexed { idx, l -> l.copy(zIndex = idx) }
                    scene.copy(layers = filtered)
                } else {
                    scene
                }
            }
            _uiState.value = _uiState.value.copy(scenes = updated)
            sceneStorage.saveScenes(updated)
        }
    }

    fun toggleLayerVisibility(layerId: String) {
        updateLayer(layerId) { it.copy(visible = !it.visible) }
    }

    fun toggleLayerLock(layerId: String) {
        updateLayer(layerId) { it.copy(locked = !it.locked) }
    }

    fun updateLayer(
        layerId: String,
        persistImmediately: Boolean = true,
        transform: (SceneLayer) -> SceneLayer
    ) {
        updateActiveScene(persistImmediately = persistImmediately) { scene ->
            scene.copy(
                layers = scene.layers.map { l ->
                    if (l.id == layerId) transform(l) else l
                }
            )
        }
    }

    fun updateCameraLayerAndHardware(
        lifecycleOwner: LifecycleOwner,
        layerId: String,
        transform: (CameraSourceConfig) -> CameraSourceConfig
    ) {
        val layer = _uiState.value.activeScene.layers.find { it.id == layerId } ?: return
        val oldCfg = layer.cameraConfig
        val newCfg = transform(oldCfg)
        updateLayer(layerId) { it.copy(cameraConfig = newCfg) }

        val needsRebind = oldCfg.facing != newCfg.facing ||
            oldCfg.targetWidth != newCfg.targetWidth ||
            oldCfg.targetHeight != newCfg.targetHeight ||
            oldCfg.targetFps != newCfg.targetFps
        if (needsRebind) {
            cameraManager.startOrUpdateCamera(lifecycleOwner, newCfg)
        } else {
            cameraManager.updateLiveControls(newCfg)
        }
    }

    // --- GUIDES & STUDIO PREFERENCES ---

    fun toggleCenterSnap() {
        _uiState.value = _uiState.value.copy(centerSnapEnabled = !_uiState.value.centerSnapEnabled)
    }

    fun toggleGridSnap() {
        _uiState.value = _uiState.value.copy(gridSnapEnabled = !_uiState.value.gridSnapEnabled)
    }

    fun toggleSafeAreaGuides() {
        _uiState.value = _uiState.value.copy(safeAreaGuidesEnabled = !_uiState.value.safeAreaGuidesEnabled)
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(keepScreenAwake = enabled)
    }

    fun setLockLandscapeOrientation(locked: Boolean) {
        _uiState.value = _uiState.value.copy(lockLandscapeOrientation = locked)
    }

    // --- MEDIA PROJECTION (SCREEN & APP CAPTURE) ---

    fun onMediaProjectionPermissionGranted(resultCode: Int, data: Intent, mode: ScreenCaptureMode) {
        // Start foreground service with mediaProjection type BEFORE initializing virtual display
        SceneCastStreamingService.startOrUpdate(
            context = appContext,
            title = "SceneCast Live • Screen Capture Active",
            body = "Capturing screen & compositing 16:9 scene",
            includeProjection = true
        )
        val activeScene = _uiState.value.activeScene
        screenCaptureManager.startCapture(
            resultCode = resultCode,
            resultData = data,
            width = activeScene.canvasWidth,
            height = activeScene.canvasHeight,
            mode = mode
        )
        screenCaptureManager.getMediaProjection()?.let { proj ->
            audioMixer.initPlaybackCapture(proj)
        }
        showStatusBanner("Screen / App Capture started")
    }

    fun stopScreenCapture() {
        screenCaptureManager.stopCapture("Stopped by user")
        if (!streamTelemetry.value.isStreaming) {
            SceneCastStreamingService.stop(appContext)
        }
    }

    // --- YOUTUBE LIVE RTMPS STREAMING ---

    fun updateStreamCredentials(transform: (StreamCredentials) -> StreamCredentials) {
        val updated = transform(_uiState.value.credentials)
        _uiState.value = _uiState.value.copy(credentials = updated)
        credentialStore.saveCredentials(updated)
    }

    fun testYouTubeConnection() {
        streamingEngine.runConnectionTest(_uiState.value.credentials)
    }

    fun startLiveStream() {
        val creds = _uiState.value.credentials
        credentialStore.saveCredentials(creds)
        if (creds.streamKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                activeBottomPanel = StudioBottomPanel.STREAM_SETTINGS
            )
            showStatusBanner("Enter your YouTube RTMPS Stream Key to start streaming")
            return
        }

        val hasProjection = screenCaptureState.value.isCapturing
        SceneCastStreamingService.startOrUpdate(
            context = appContext,
            title = "SceneCast Live • ON AIR",
            body = "Streaming ${creds.resolutionPreset.label} to YouTube Live",
            includeProjection = hasProjection
        )

        streamingEngine.startStreaming(creds) {
            showStatusBanner("LIVE on YouTube (${creds.resolutionPreset.label})")
        }
    }

    fun stopLiveStream() {
        if (!streamTelemetry.value.isStreaming && streamTelemetry.value.status == com.example.streaming.StreamConnectionStatus.DISCONNECTED) {
            return
        }
        streamingEngine.stopStreaming()
        if (!screenCaptureState.value.isCapturing) {
            SceneCastStreamingService.stop(appContext)
        }
        showStatusBanner("Livestream stopped safely")
    }

    private fun updateActiveScene(
        selectLayerId: String? = _uiState.value.selectedLayerId,
        persistImmediately: Boolean = true,
        transform: (Scene) -> Scene
    ) {
        val activeId = _uiState.value.activeSceneId
        if (persistImmediately) {
            _uiState.value.scenes.find { it.id == activeId }?.let { currentScene ->
                recordUndoSnapshot(currentScene)
            }
        }
        val updatedScenes = _uiState.value.scenes.map { scene ->
            if (scene.id == activeId) transform(scene) else scene
        }
        val activeSceneAfter = updatedScenes.find { it.id == activeId }
        val validSelection = if (activeSceneAfter?.layers?.any { it.id == selectLayerId } == true) {
            selectLayerId
        } else {
            activeSceneAfter?.layers?.lastOrNull()?.id
        }
        _uiState.value = _uiState.value.copy(
            scenes = updatedScenes,
            selectedLayerId = validSelection,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
        if (persistImmediately) {
            sceneStorage.saveScenes(updatedScenes)
        }
    }

    private fun persistScenesAndSelect(
        scenes: List<Scene>,
        activeSceneId: String,
        selectedLayerId: String?
    ) {
        _uiState.value = _uiState.value.copy(
            scenes = scenes,
            activeSceneId = activeSceneId,
            selectedLayerId = selectedLayerId
        )
        sceneStorage.saveScenes(scenes)
        sceneStorage.saveActiveSceneId(activeSceneId)
    }

    override fun onCleared() {
        super.onCleared()
        monitorManager.stopMonitoring()
        streamingEngine.stopStreaming()
        audioMixer.stopAudioEngine()
        moonlightDiscovery.stop()
        moonlightSession.stop()
        sourceManager.releaseAll()
    }
}
