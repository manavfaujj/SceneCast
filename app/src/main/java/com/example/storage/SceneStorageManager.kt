package com.example.storage

import android.content.Context
import android.net.Uri
import com.example.R
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
import com.example.model.SceneFontOption
import com.example.model.SceneLayer
import com.example.model.ScreenCaptureMode
import com.example.model.ScreenCaptureSourceConfig
import com.example.model.SourceType
import com.example.model.TextSourceConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Persists scenes, layer orders, coordinates, crops, borders, and imported PNG/image/video assets
 * locally in structured JSON inside app-private storage.
 */
class SceneStorageManager(private val context: Context) {

    private val scenesFile = File(context.filesDir, "scenecast_scenes_v3.json")
    private val statePrefs = context.getSharedPreferences("scenecast_scene_state_v3", Context.MODE_PRIVATE)
    private val importedAssetsDir = File(context.filesDir, "imported_assets").apply { mkdirs() }

    fun loadScenes(): List<Scene> {
        if (!scenesFile.exists()) {
            val defaults = createDefaultScenes()
            saveScenes(defaults)
            return defaults
        }
        return try {
            val jsonText = scenesFile.readText()
            val arr = JSONArray(jsonText)
            val list = mutableListOf<Scene>()
            for (i in 0 until arr.length()) {
                list.add(parseScene(arr.getJSONObject(i)))
            }
            if (list.isEmpty()) createDefaultScenes() else list
        } catch (_: Exception) {
            val defaults = createDefaultScenes()
            saveScenes(defaults)
            defaults
        }
    }

    fun saveScenes(scenes: List<Scene>) {
        try {
            val arr = JSONArray()
            scenes.forEach { scene ->
                arr.put(serializeScene(scene))
            }
            scenesFile.writeText(arr.toString(2))
        } catch (_: Exception) {
        }
    }

    fun getActiveSceneId(availableScenes: List<Scene>): String {
        val saved = statePrefs.getString(KEY_ACTIVE_SCENE_ID, null)
        return if (saved != null && availableScenes.any { it.id == saved }) {
            saved
        } else {
            availableScenes.firstOrNull()?.id ?: "scene_main_65_35"
        }
    }

    fun saveActiveSceneId(id: String) {
        statePrefs.edit().putString(KEY_ACTIVE_SCENE_ID, id).apply()
    }

    fun copyImportedMediaUriToInternalStorage(uri: Uri, extensionHint: String = "png"): String? {
        return try {
            val fileName = "asset_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$extensionHint"
            val destFile = File(importedAssetsDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun createDefaultScenes(): List<Scene> {
        val mainSceneId = "scene_main_65_35"
        val mainScene = Scene(
            id = mainSceneId,
            name = "Screen 65% + Cam 35%",
            canvasPreset = CanvasPreset.LANDSCAPE_16_9,
            canvasWidth = 1920,
            canvasHeight = 1080,
            backgroundColorHex = 0xFF0A0E17,
            backgroundGradientEndHex = 0xFF151E30,
            useGradientBackground = true,
            layers = listOf(
                SceneLayer(
                    id = "layer_screen_main",
                    name = "Screen / App Capture",
                    type = SourceType.SCREEN_CAPTURE,
                    x = 0.0f,
                    y = 0.0f,
                    width = 0.65f,
                    height = 1.0f,
                    zIndex = 0,
                    scaleMode = ScaleMode.FIT,
                    border = BorderStyle(
                        enabled = false,
                        thicknessDp = 0f,
                        cornerRadiusPercent = 0f,
                        shadowEnabled = false,
                        builtInPngFrameRes = null
                    ),
                    screenCaptureConfig = ScreenCaptureSourceConfig()
                ),
                SceneLayer(
                    id = "layer_cam_main",
                    name = "Front Camera",
                    type = SourceType.CAMERA,
                    x = 0.65f,
                    y = 0.0f,
                    width = 0.35f,
                    height = 1.0f,
                    zIndex = 1,
                    scaleMode = ScaleMode.FILL,
                    border = BorderStyle(
                        enabled = false,
                        thicknessDp = 0f,
                        cornerRadiusPercent = 0f,
                        shadowEnabled = false,
                        glowEnabled = false,
                        builtInPngFrameRes = null
                    ),
                    cameraConfig = CameraSourceConfig(
                        facing = CameraFacing.FRONT,
                        targetWidth = 1920,
                        targetHeight = 1080,
                        targetFps = 30,
                        mirrorHorizontally = true
                    )
                )
            ),
            isSystemPreset = false
        )

        val pipScene = Scene(
            id = "scene_pip_gaming",
            name = "Full Screen + Corner Cam PiP",
            canvasPreset = CanvasPreset.LANDSCAPE_16_9,
            canvasWidth = 1920,
            canvasHeight = 1080,
            backgroundColorHex = 0xFF090C12,
            backgroundGradientEndHex = 0xFF101726,
            layers = listOf(
                SceneLayer(
                    id = "layer_screen_pip",
                    name = "Screen / App Capture",
                    type = SourceType.SCREEN_CAPTURE,
                    x = 0.0f,
                    y = 0.0f,
                    width = 1.0f,
                    height = 1.0f,
                    zIndex = 0,
                    scaleMode = ScaleMode.FILL,
                    border = BorderStyle(enabled = false, cornerRadiusPercent = 0f, shadowEnabled = false),
                    screenCaptureConfig = ScreenCaptureSourceConfig()
                ),
                SceneLayer(
                    id = "layer_cam_pip",
                    name = "Facecam PiP",
                    type = SourceType.CAMERA,
                    x = 0.70f,
                    y = 0.64f,
                    width = 0.27f,
                    height = 0.32f,
                    zIndex = 1,
                    scaleMode = ScaleMode.FILL,
                    border = BorderStyle(
                        enabled = false,
                        thicknessDp = 0f,
                        cornerRadiusPercent = 0f,
                        shadowEnabled = false,
                        glowEnabled = false,
                        builtInPngFrameRes = null
                    ),
                    cameraConfig = CameraSourceConfig(
                        facing = CameraFacing.FRONT,
                        targetWidth = 1920,
                        targetHeight = 1080,
                        targetFps = 30,
                        mirrorHorizontally = true
                    )
                )
            )
        )

        val startingSoonScene = Scene(
            id = "scene_starting_soon",
            name = "Starting Soon",
            canvasPreset = CanvasPreset.LANDSCAPE_16_9,
            canvasWidth = 1920,
            canvasHeight = 1080,
            backgroundColorHex = 0xFF0B0F1C,
            backgroundGradientEndHex = 0xFF231130,
            useGradientBackground = true,
            layers = listOf(
                SceneLayer(
                    id = "layer_starting_bg",
                    name = "Studio Background",
                    type = SourceType.BACKGROUND,
                    x = 0f,
                    y = 0f,
                    width = 1f,
                    height = 1f,
                    zIndex = 0,
                    border = BorderStyle(enabled = false, cornerRadiusPercent = 0f),
                    backgroundConfig = BackgroundSourceConfig(
                        primaryColorHex = 0xFF0B0F1C,
                        secondaryColorHex = 0xFF1E1333,
                        useGradient = true
                    )
                ),
                SceneLayer(
                    id = "layer_starting_title",
                    name = "Starting Soon Banner",
                    type = SourceType.TEXT,
                    x = 0.15f,
                    y = 0.36f,
                    width = 0.70f,
                    height = 0.26f,
                    zIndex = 1,
                    border = BorderStyle(
                        enabled = true,
                        thicknessDp = 3f,
                        colorHex = 0xFF00E5FF,
                        cornerRadiusPercent = 10f
                    ),
                    textConfig = TextSourceConfig(
                        text = "STREAM STARTING SOON",
                        fontKey = SceneFontOption.SPACE_GROTESK.fontKey,
                        fontSizeSp = 36f,
                        textColorHex = 0xFFFFFFFF,
                        backgroundColorHex = 0xAA101726
                    )
                )
            ),
            isSystemPreset = true
        )

        val brbScene = Scene(
            id = "scene_brb",
            name = "Be Right Back (BRB)",
            canvasPreset = CanvasPreset.LANDSCAPE_16_9,
            canvasWidth = 1920,
            canvasHeight = 1080,
            backgroundColorHex = 0xFF10131C,
            backgroundGradientEndHex = 0xFF281524,
            useGradientBackground = true,
            layers = listOf(
                SceneLayer(
                    id = "layer_brb_text",
                    name = "BRB Headline",
                    type = SourceType.TEXT,
                    x = 0.18f,
                    y = 0.38f,
                    width = 0.64f,
                    height = 0.24f,
                    zIndex = 0,
                    border = BorderStyle(
                        enabled = true,
                        thicknessDp = 3f,
                        colorHex = 0xFFFFB300,
                        cornerRadiusPercent = 10f
                    ),
                    textConfig = TextSourceConfig(
                        text = "BE RIGHT BACK • SHORT INTERMISSION",
                        fontKey = SceneFontOption.OSWALD.fontKey,
                        fontSizeSp = 34f,
                        textColorHex = 0xFFFFD166,
                        backgroundColorHex = 0xAA141824
                    )
                )
            ),
            isSystemPreset = true
        )

        val endingScene = Scene(
            id = "scene_ending",
            name = "Stream Ending",
            canvasPreset = CanvasPreset.LANDSCAPE_16_9,
            canvasWidth = 1920,
            canvasHeight = 1080,
            backgroundColorHex = 0xFF090C14,
            backgroundGradientEndHex = 0xFF1B1228,
            useGradientBackground = true,
            layers = listOf(
                SceneLayer(
                    id = "layer_ending_text",
                    name = "Thanks For Watching",
                    type = SourceType.TEXT,
                    x = 0.16f,
                    y = 0.38f,
                    width = 0.68f,
                    height = 0.24f,
                    zIndex = 0,
                    border = BorderStyle(
                        enabled = true,
                        thicknessDp = 3f,
                        colorHex = 0xFFFF3B5C,
                        cornerRadiusPercent = 10f
                    ),
                    textConfig = TextSourceConfig(
                        text = "STREAM ENDING • THANKS FOR WATCHING!",
                        fontKey = SceneFontOption.SPACE_GROTESK.fontKey,
                        fontSizeSp = 32f,
                        textColorHex = 0xFFFFFFFF,
                        backgroundColorHex = 0xAA141824
                    )
                )
            ),
            isSystemPreset = true
        )

        val blackScene = Scene(
            id = "scene_black_screen",
            name = "Black Screen",
            canvasPreset = CanvasPreset.LANDSCAPE_16_9,
            canvasWidth = 1920,
            canvasHeight = 1080,
            backgroundColorHex = 0xFF000000,
            backgroundGradientEndHex = 0xFF000000,
            useGradientBackground = false,
            layers = emptyList(),
            isSystemPreset = true
        )

        return listOf(mainScene, pipScene, startingSoonScene, brbScene, endingScene, blackScene)
    }

    /**
     * Applies one of the 8 ready-made layout presets to a scene while preserving all existing
     * border styles, camera settings, and supplemental overlay layers.
     */
    fun applyLayoutPresetToScene(scene: Scene, preset: LayoutPreset): Scene {
        val currentLayers = scene.layers.toMutableList()
        var screenIndex = currentLayers.indexOfFirst { it.type == SourceType.SCREEN_CAPTURE }
        if (screenIndex == -1) {
            screenIndex = currentLayers.indexOfFirst { it.type.isScreenOrNetwork }
        }
        if (screenIndex == -1) {
            currentLayers.add(
                0,
                SceneLayer(
                    name = "Screen / App Capture",
                    type = SourceType.SCREEN_CAPTURE,
                    zIndex = 0,
                    border = BorderStyle(
                        enabled = false,
                        thicknessDp = 0f,
                        cornerRadiusPercent = 0f,
                        shadowEnabled = false,
                        glowEnabled = false,
                        builtInPngFrameRes = null
                    )
                )
            )
            screenIndex = 0
        }

        var camIndex = currentLayers.indexOfFirst { it.type == SourceType.CAMERA }
        if (camIndex == -1) {
            currentLayers.add(
                SceneLayer(
                    name = "Front Camera",
                    type = SourceType.CAMERA,
                    zIndex = currentLayers.size,
                    border = BorderStyle(
                        enabled = false,
                        thicknessDp = 0f,
                        cornerRadiusPercent = 0f,
                        shadowEnabled = false,
                        glowEnabled = false,
                        builtInPngFrameRes = null
                    ),
                    cameraConfig = CameraSourceConfig(
                        facing = CameraFacing.FRONT,
                        targetWidth = 1920,
                        targetHeight = 1080,
                        targetFps = 30,
                        mirrorHorizontally = true
                    )
                )
            )
            camIndex = currentLayers.lastIndex
        }

        val screenLayer = currentLayers[screenIndex]
        val camLayer = currentLayers[camIndex]

        val (updatedScreen, updatedCam) = when (preset) {
            LayoutPreset.SCREEN_50_CAM_50 -> {
                screenLayer.copy(x = 0.0f, y = 0.0f, width = 0.50f, height = 1.0f, zIndex = 0, locked = false) to
                    camLayer.copy(x = 0.50f, y = 0.0f, width = 0.50f, height = 1.0f, zIndex = 1, locked = false)
            }
            LayoutPreset.SCREEN_60_CAM_40 -> {
                screenLayer.copy(x = 0.0f, y = 0.0f, width = 0.60f, height = 1.0f, zIndex = 0, locked = false) to
                    camLayer.copy(x = 0.60f, y = 0.0f, width = 0.40f, height = 1.0f, zIndex = 1, locked = false)
            }
            LayoutPreset.SCREEN_65_CAM_35 -> {
                screenLayer.copy(x = 0.0f, y = 0.0f, width = 0.65f, height = 1.0f, zIndex = 0, locked = false) to
                    camLayer.copy(x = 0.65f, y = 0.0f, width = 0.35f, height = 1.0f, zIndex = 1, locked = false)
            }
            LayoutPreset.SCREEN_70_CAM_30 -> {
                screenLayer.copy(x = 0.0f, y = 0.0f, width = 0.70f, height = 1.0f, zIndex = 0, locked = false) to
                    camLayer.copy(x = 0.70f, y = 0.0f, width = 0.30f, height = 1.0f, zIndex = 1, locked = false)
            }
            LayoutPreset.SCREEN_FULL_CAM_PIP -> {
                screenLayer.copy(x = 0.0f, y = 0.0f, width = 1.0f, height = 1.0f, zIndex = 0, locked = false) to
                    camLayer.copy(x = 0.69f, y = 0.63f, width = 0.28f, height = 0.33f, zIndex = 1, locked = false)
            }
            LayoutPreset.CAM_LEFT_SCREEN_RIGHT -> {
                screenLayer.copy(x = 0.35f, y = 0.0f, width = 0.65f, height = 1.0f, zIndex = 0, locked = false) to
                    camLayer.copy(x = 0.0f, y = 0.0f, width = 0.35f, height = 1.0f, zIndex = 1, locked = false)
            }
            LayoutPreset.SCREEN_LEFT_CAM_RIGHT -> {
                screenLayer.copy(x = 0.0f, y = 0.0f, width = 0.65f, height = 1.0f, zIndex = 0, locked = false) to
                    camLayer.copy(x = 0.65f, y = 0.0f, width = 0.35f, height = 1.0f, zIndex = 1, locked = false)
            }
            LayoutPreset.SCREEN_TOP_CAM_BOTTOM -> {
                screenLayer.copy(x = 0.0f, y = 0.0f, width = 1.0f, height = 0.65f, zIndex = 0, locked = false) to
                    camLayer.copy(x = 0.0f, y = 0.65f, width = 1.0f, height = 0.35f, zIndex = 1, locked = false)
            }
            LayoutPreset.CUSTOM -> {
                screenLayer.copy(locked = false, lockAspectRatio = false) to
                    camLayer.copy(locked = false, lockAspectRatio = false)
            }
        }

        currentLayers[screenIndex] = updatedScreen
        currentLayers[camIndex] = updatedCam

        val normalizedLayers = currentLayers
            .sortedBy { it.zIndex }
            .mapIndexed { idx, layer -> layer.copy(zIndex = idx) }

        return scene.copy(layers = normalizedLayers)
    }

    private fun serializeScene(scene: Scene): JSONObject {
        return JSONObject().apply {
            put("id", scene.id)
            put("name", scene.name)
            put("canvasPreset", scene.canvasPreset.name)
            put("canvasWidth", scene.canvasWidth)
            put("canvasHeight", scene.canvasHeight)
            put("backgroundColorHex", scene.backgroundColorHex)
            put("backgroundGradientEndHex", scene.backgroundGradientEndHex)
            put("useGradientBackground", scene.useGradientBackground)
            put("transparentBackground", scene.transparentBackground)
            put("isSystemPreset", scene.isSystemPreset)
            val layersArr = JSONArray()
            scene.layers.forEach { layer ->
                layersArr.put(serializeLayer(layer))
            }
            put("layers", layersArr)
        }
    }

    private fun serializeLayer(layer: SceneLayer): JSONObject {
        return JSONObject().apply {
            put("id", layer.id)
            put("name", layer.name)
            put("type", layer.type.name)
            put("x", layer.x.toDouble())
            put("y", layer.y.toDouble())
            put("width", layer.width.toDouble())
            put("height", layer.height.toDouble())
            put("zIndex", layer.zIndex)
            put("visible", layer.visible)
            put("locked", layer.locked)
            put("opacity", layer.opacity.toDouble())
            put("rotationDeg", layer.rotationDeg.toDouble())
            put("lockAspectRatio", layer.lockAspectRatio)
            put("scaleMode", layer.scaleMode.name)
            put("flipHorizontal", layer.flipHorizontal)
            put("flipVertical", layer.flipVertical)

            put("crop", JSONObject().apply {
                put("left", layer.crop.left.toDouble())
                put("top", layer.crop.top.toDouble())
                put("right", layer.crop.right.toDouble())
                put("bottom", layer.crop.bottom.toDouble())
            })

            put("border", JSONObject().apply {
                put("enabled", layer.border.enabled)
                put("thicknessDp", layer.border.thicknessDp.toDouble())
                put("colorHex", layer.border.colorHex)
                put("secondaryColorHex", layer.border.secondaryColorHex)
                put("useGradient", layer.border.useGradient)
                put("cornerRadiusPercent", layer.border.cornerRadiusPercent.toDouble())
                put("shadowEnabled", layer.border.shadowEnabled)
                put("shadowRadiusDp", layer.border.shadowRadiusDp.toDouble())
                put("glowEnabled", layer.border.glowEnabled)
                put("glowColorHex", layer.border.glowColorHex)
                layer.border.builtInPngFrameRes?.let { put("builtInPngFrameRes", it) }
                layer.border.customPngFramePath?.let { put("customPngFramePath", it) }
            })

            put("cameraConfig", JSONObject().apply {
                put("facing", layer.cameraConfig.facing.name)
                put("targetWidth", layer.cameraConfig.targetWidth)
                put("targetHeight", layer.cameraConfig.targetHeight)
                put("targetFps", layer.cameraConfig.targetFps)
                put("torchEnabled", layer.cameraConfig.torchEnabled)
                put("zoomRatio", layer.cameraConfig.zoomRatio.toDouble())
                put("exposureIndex", layer.cameraConfig.exposureIndex)
                put("mirrorHorizontally", layer.cameraConfig.mirrorHorizontally)
                put("flipVertically", layer.cameraConfig.flipVertically)
            })

            put("screenCaptureConfig", JSONObject().apply {
                put("captureMode", layer.screenCaptureConfig.captureMode.name)
                put("splitScreenLeftHalfCrop", layer.screenCaptureConfig.splitScreenLeftHalfCrop)
                put("showTelemetryOverlayInEdit", layer.screenCaptureConfig.showTelemetryOverlayInEdit)
                put("captureAudio", layer.screenCaptureConfig.captureAudio)
            })

            put("networkConfig", JSONObject().apply {
                put("streamUrl", layer.networkConfig.streamUrl)
                put("protocolLabel", layer.networkConfig.protocolLabel)
                put("lowLatencyBufferMs", layer.networkConfig.lowLatencyBufferMs)
                put("enableNetworkAudio", layer.networkConfig.enableNetworkAudio)
            })

            put("textConfig", JSONObject().apply {
                put("text", layer.textConfig.text)
                put("fontKey", layer.textConfig.fontKey)
                put("fontSizeSp", layer.textConfig.fontSizeSp.toDouble())
                put("textColorHex", layer.textConfig.textColorHex)
                put("shadowEnabled", layer.textConfig.shadowEnabled)
                put("backgroundColorHex", layer.textConfig.backgroundColorHex)
                put("bold", layer.textConfig.bold)
            })

            put("backgroundConfig", JSONObject().apply {
                put("primaryColorHex", layer.backgroundConfig.primaryColorHex)
                put("secondaryColorHex", layer.backgroundConfig.secondaryColorHex)
                put("useGradient", layer.backgroundConfig.useGradient)
                put("gradientAngleDeg", layer.backgroundConfig.gradientAngleDeg.toDouble())
                layer.backgroundConfig.imagePath?.let { put("imagePath", it) }
                put("transparentBackground", layer.backgroundConfig.transparentBackground)
            })

            put("mediaConfig", JSONObject().apply {
                layer.mediaConfig.localFilePath?.let { put("localFilePath", it) }
                layer.mediaConfig.builtInDrawableRes?.let { put("builtInDrawableRes", it) }
                put("loopVideo", layer.mediaConfig.loopVideo)
                put("videoVolume", layer.mediaConfig.videoVolume.toDouble())
            })
        }
    }

    private fun parseScene(obj: JSONObject): Scene {
        val presetName = obj.optString("canvasPreset", CanvasPreset.LANDSCAPE_16_9.name)
        val preset = CanvasPreset.entries.find { it.name == presetName } ?: CanvasPreset.LANDSCAPE_16_9
        val layersArr = obj.optJSONArray("layers") ?: JSONArray()
        val layers = mutableListOf<SceneLayer>()
        for (i in 0 until layersArr.length()) {
            layers.add(parseLayer(layersArr.getJSONObject(i)))
        }
        return Scene(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", "Scene"),
            canvasPreset = preset,
            canvasWidth = obj.optInt("canvasWidth", preset.defaultWidth),
            canvasHeight = obj.optInt("canvasHeight", preset.defaultHeight),
            backgroundColorHex = obj.optLong("backgroundColorHex", 0xFF0B0E17),
            backgroundGradientEndHex = obj.optLong("backgroundGradientEndHex", 0xFF141B2D),
            useGradientBackground = obj.optBoolean("useGradientBackground", true),
            transparentBackground = obj.optBoolean("transparentBackground", false),
            layers = layers,
            isSystemPreset = obj.optBoolean("isSystemPreset", false)
        )
    }

    private fun parseLayer(obj: JSONObject): SceneLayer {
        val rawTypeName = obj.optString("type", SourceType.CAMERA.name)
        val type = when (rawTypeName) {
            "PC_MOONLIGHT", "MOONLIGHT_CAPTURE" -> SourceType.SCREEN_CAPTURE
            else -> SourceType.entries.find { it.name == rawTypeName } ?: SourceType.CAMERA
        }
        val scaleName = obj.optString("scaleMode", ScaleMode.FILL.name)
        val scaleMode = ScaleMode.entries.find { it.name == scaleName } ?: ScaleMode.FILL

        val cropObj = obj.optJSONObject("crop")
        val crop = if (cropObj != null) {
            CropRect(
                left = cropObj.optDouble("left", 0.0).toFloat(),
                top = cropObj.optDouble("top", 0.0).toFloat(),
                right = cropObj.optDouble("right", 0.0).toFloat(),
                bottom = cropObj.optDouble("bottom", 0.0).toFloat()
            )
        } else CropRect()

        val borderObj = obj.optJSONObject("border")
        val border = if (borderObj != null) {
            BorderStyle(
                enabled = borderObj.optBoolean("enabled", false),
                thicknessDp = borderObj.optDouble("thicknessDp", 0.0).toFloat(),
                colorHex = borderObj.optLong("colorHex", 0xFF00E5FF),
                secondaryColorHex = borderObj.optLong("secondaryColorHex", 0xFFFF3B5C),
                useGradient = borderObj.optBoolean("useGradient", false),
                cornerRadiusPercent = borderObj.optDouble("cornerRadiusPercent", 0.0).toFloat(),
                shadowEnabled = borderObj.optBoolean("shadowEnabled", false),
                shadowRadiusDp = borderObj.optDouble("shadowRadiusDp", 0.0).toFloat(),
                glowEnabled = borderObj.optBoolean("glowEnabled", false),
                glowColorHex = borderObj.optLong("glowColorHex", 0xFF00E5FF),
                builtInPngFrameRes = if (borderObj.has("builtInPngFrameRes")) borderObj.optInt("builtInPngFrameRes") else null,
                customPngFramePath = borderObj.optString("customPngFramePath", "").takeIf { it.isNotEmpty() }
            )
        } else BorderStyle()

        val camObj = obj.optJSONObject("cameraConfig")
        val cameraConfig = if (camObj != null) {
            val facing = CameraFacing.entries.find { it.name == camObj.optString("facing") } ?: CameraFacing.FRONT
            CameraSourceConfig(
                facing = facing,
                targetWidth = camObj.optInt("targetWidth", 1920),
                targetHeight = camObj.optInt("targetHeight", 1080),
                targetFps = camObj.optInt("targetFps", 30),
                torchEnabled = camObj.optBoolean("torchEnabled", false),
                zoomRatio = camObj.optDouble("zoomRatio", 1.0).toFloat(),
                exposureIndex = camObj.optInt("exposureIndex", 0),
                mirrorHorizontally = camObj.optBoolean("mirrorHorizontally", facing == CameraFacing.FRONT),
                flipVertically = camObj.optBoolean("flipVertically", false)
            )
        } else CameraSourceConfig()

        val screenObj = obj.optJSONObject("screenCaptureConfig") ?: obj.optJSONObject("moonlightConfig")
        val screenCaptureConfig = if (screenObj != null) {
            val mode = ScreenCaptureMode.entries.find { it.name == screenObj.optString("captureMode") }
                ?: ScreenCaptureMode.ANDROID_14_SINGLE_APP
            ScreenCaptureSourceConfig(
                captureMode = mode,
                splitScreenLeftHalfCrop = screenObj.optBoolean("splitScreenLeftHalfCrop", false),
                showTelemetryOverlayInEdit = screenObj.optBoolean("showTelemetryOverlayInEdit", true),
                captureAudio = screenObj.optBoolean("captureAudio", true)
            )
        } else ScreenCaptureSourceConfig()

        val netObj = obj.optJSONObject("networkConfig")
        val networkConfig = if (netObj != null) {
            NetworkSourceConfig(
                streamUrl = netObj.optString("streamUrl", "rtsp://192.168.1.100:8554/live"),
                protocolLabel = netObj.optString("protocolLabel", "RTSP / MJPEG LAN"),
                lowLatencyBufferMs = netObj.optInt("lowLatencyBufferMs", 120),
                enableNetworkAudio = netObj.optBoolean("enableNetworkAudio", true)
            )
        } else NetworkSourceConfig()

        val txtObj = obj.optJSONObject("textConfig")
        val textConfig = if (txtObj != null) {
            TextSourceConfig(
                text = txtObj.optString("text", "LIVE STREAM"),
                fontKey = txtObj.optString("fontKey", SceneFontOption.SPACE_GROTESK.fontKey),
                fontSizeSp = txtObj.optDouble("fontSizeSp", 28.0).toFloat(),
                textColorHex = txtObj.optLong("textColorHex", 0xFFFFFFFF),
                shadowEnabled = txtObj.optBoolean("shadowEnabled", true),
                backgroundColorHex = txtObj.optLong("backgroundColorHex", 0x880B0D12),
                bold = txtObj.optBoolean("bold", true)
            )
        } else TextSourceConfig()

        val bgObj = obj.optJSONObject("backgroundConfig")
        val bgConfig = if (bgObj != null) {
            BackgroundSourceConfig(
                primaryColorHex = bgObj.optLong("primaryColorHex", 0xFF0D1322),
                secondaryColorHex = bgObj.optLong("secondaryColorHex", 0xFF1D1128),
                useGradient = bgObj.optBoolean("useGradient", true),
                gradientAngleDeg = bgObj.optDouble("gradientAngleDeg", 45.0).toFloat(),
                imagePath = bgObj.optString("imagePath", "").takeIf { it.isNotEmpty() },
                transparentBackground = bgObj.optBoolean("transparentBackground", false)
            )
        } else BackgroundSourceConfig()

        val mediaObj = obj.optJSONObject("mediaConfig")
        val mediaConfig = if (mediaObj != null) {
            MediaSourceConfig(
                localFilePath = mediaObj.optString("localFilePath", "").takeIf { it.isNotEmpty() },
                builtInDrawableRes = if (mediaObj.has("builtInDrawableRes")) mediaObj.optInt("builtInDrawableRes") else null,
                loopVideo = mediaObj.optBoolean("loopVideo", true),
                videoVolume = mediaObj.optDouble("videoVolume", 0.8).toFloat()
            )
        } else MediaSourceConfig()

        return SceneLayer(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", type.displayName),
            type = type,
            x = obj.optDouble("x", 0.0).toFloat(),
            y = obj.optDouble("y", 0.0).toFloat(),
            width = obj.optDouble("width", 0.5).toFloat(),
            height = obj.optDouble("height", 1.0).toFloat(),
            zIndex = obj.optInt("zIndex", 0),
            visible = obj.optBoolean("visible", true),
            locked = obj.optBoolean("locked", false),
            opacity = obj.optDouble("opacity", 1.0).toFloat(),
            rotationDeg = obj.optDouble("rotationDeg", 0.0).toFloat(),
            lockAspectRatio = obj.optBoolean("lockAspectRatio", false),
            scaleMode = scaleMode,
            flipHorizontal = obj.optBoolean("flipHorizontal", false),
            flipVertical = obj.optBoolean("flipVertical", false),
            crop = crop,
            border = border,
            cameraConfig = cameraConfig,
            screenCaptureConfig = screenCaptureConfig,
            networkConfig = networkConfig,
            textConfig = textConfig,
            backgroundConfig = bgConfig,
            mediaConfig = mediaConfig
        )
    }

    companion object {
        private const val KEY_ACTIVE_SCENE_ID = "active_scene_id_key"
    }
}
