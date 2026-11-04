package com.example.model

import com.example.R
import java.util.UUID

enum class CanvasPreset(
    val label: String,
    val aspectLabel: String,
    val defaultWidth: Int,
    val defaultHeight: Int
) {
    LANDSCAPE_16_9("1080p Landscape", "16:9", 1920, 1080),
    PORTRAIT_9_16("1080p Vertical", "9:16", 1080, 1920),
    SQUARE_1_1("Square 1:1", "1:1", 1080, 1080),
    STANDARD_4_3("Classic 4:3", "4:3", 1440, 1080),
    CUSTOM("Custom Resolution", "Custom", 1920, 1080)
}

enum class SourceType(val displayName: String, val badge: String) {
    CAMERA("Phone Camera", "CAM"),
    SCREEN_CAPTURE("Screen / App Capture", "SCR"),
    NETWORK_VIDEO("Network Stream (RTSP/MJPEG)", "NET"),
    MOONLIGHT_PC("Moonlight PC Stream", "PC"),
    PNG_OVERLAY("Transparent PNG Overlay", "PNG"),
    IMAGE("Image Graphic", "IMG"),
    VIDEO_FILE("Video Clip", "VID"),
    TEXT("Text Label / Ticker", "TXT"),
    BACKGROUND("Background Layer", "BG"),
    LOGO("Watermark / Logo", "LOGO");

    val isScreenOrNetwork: Boolean
        get() = this == SCREEN_CAPTURE || this == NETWORK_VIDEO
}

enum class ScaleMode(val displayName: String) {
    FIT("Fit Inside"),
    FILL("Fill & Crop"),
    STRETCH("Free Stretch")
}

enum class CameraFacing(val displayName: String) {
    FRONT("Front Camera"),
    REAR("Rear Camera")
}

enum class ScreenCaptureMode(val displayName: String, val subtitle: String) {
    ANDROID_14_SINGLE_APP(
        "Single-App Window (Android 14+)",
        "Select app window in the system capture dialog"
    ),
    ANDROID_13_SPLIT_CROP(
        "Full Display + Region Crop (Android 13+)",
        "Captures display with adjustable split-screen / region crop"
    )
}

enum class LayoutPreset(
    val id: String,
    val title: String,
    val shortLabel: String,
    val description: String
) {
    SCREEN_50_CAM_50("50_50", "50% Screen + 50% Camera", "50 / 50", "Equal side-by-side split"),
    SCREEN_60_CAM_40("60_40", "60% Screen + 40% Camera", "60 / 40", "Screen left 60%, Camera right 40%"),
    SCREEN_65_CAM_35("65_35", "65% Screen + 35% Camera", "65 / 35", "Screen left 65%, Camera right 35%"),
    SCREEN_70_CAM_30("70_30", "70% Screen + 30% Camera", "70 / 30", "Wide Screen left 70%, Camera right 30%"),
    SCREEN_FULL_CAM_PIP("pip", "Screen Full + Camera Overlay", "PiP", "Full 16:9 Screen with corner camera PiP"),
    CAM_LEFT_SCREEN_RIGHT("cam_left", "Camera Left + Screen Right", "Cam | Scr", "Camera left 35%, Screen right 65%"),
    SCREEN_LEFT_CAM_RIGHT("scr_left", "Screen Left + Camera Right", "Scr | Cam", "Screen left 65%, Camera right 35%"),
    SCREEN_TOP_CAM_BOTTOM("top_bottom", "Screen Top + Camera Bottom", "Top / Bot", "Screen top 65%, Camera bottom 35%"),
    CUSTOM("custom", "Custom Freeform Layout", "Custom", "Unlock all windows for free positioning")
}

enum class SceneTransitionType(val displayName: String, val durationMs: Long) {
    CUT("Instant Cut", 0L),
    FADE("Smooth Crossfade", 420L),
    DIP_TO_BLACK("Dip to Black", 500L)
}

enum class SceneFontOption(val displayName: String, val fontKey: String) {
    SPACE_GROTESK("Space Grotesk", "space_grotesk"),
    ROBOTO_MONO("Roboto Mono", "roboto_mono"),
    MONTSERRAT("Montserrat", "montserrat"),
    OSWALD("Oswald", "oswald")
}

data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
) {
    fun clamped(): CropRect = CropRect(
        left = left.coerceIn(0f, 0.8f),
        top = top.coerceIn(0f, 0.8f),
        right = right.coerceIn(0f, (0.85f - left).coerceAtLeast(0f)),
        bottom = bottom.coerceIn(0f, (0.85f - top).coerceAtLeast(0f))
    )
}

data class BorderStyle(
    val enabled: Boolean = false,
    val thicknessDp: Float = 0f,
    val colorHex: Long = 0xFF00E5FF,
    val secondaryColorHex: Long = 0xFFFF3B5C,
    val useGradient: Boolean = false,
    val cornerRadiusPercent: Float = 0f,
    val shadowEnabled: Boolean = false,
    val shadowRadiusDp: Float = 0f,
    val glowEnabled: Boolean = false,
    val glowColorHex: Long = 0xFF00E5FF,
    val builtInPngFrameRes: Int? = null,
    val customPngFramePath: String? = null
)

data class CameraSourceConfig(
    val facing: CameraFacing = CameraFacing.FRONT,
    val targetWidth: Int = 1920,
    val targetHeight: Int = 1080,
    val targetFps: Int = 30,
    val torchEnabled: Boolean = false,
    val zoomRatio: Float = 1.0f,
    val exposureIndex: Int = 0,
    val mirrorHorizontally: Boolean = true,
    val flipVertically: Boolean = false
)

data class ScreenCaptureSourceConfig(
    val captureMode: ScreenCaptureMode = ScreenCaptureMode.ANDROID_14_SINGLE_APP,
    val splitScreenLeftHalfCrop: Boolean = false,
    val showTelemetryOverlayInEdit: Boolean = true,
    val captureAudio: Boolean = true
)

data class NetworkSourceConfig(
    val streamUrl: String = "rtsp://192.168.1.100:8554/live",
    val protocolLabel: String = "RTSP / MJPEG LAN",
    val lowLatencyBufferMs: Int = 120,
    val enableNetworkAudio: Boolean = true
)

data class TextSourceConfig(
    val text: String = "LIVE STREAM",
    val fontKey: String = SceneFontOption.SPACE_GROTESK.fontKey,
    val fontSizeSp: Float = 28f,
    val textColorHex: Long = 0xFFFFFFFF,
    val shadowEnabled: Boolean = true,
    val backgroundColorHex: Long = 0x880B0D12,
    val bold: Boolean = true
)

data class BackgroundSourceConfig(
    val primaryColorHex: Long = 0xFF0D1322,
    val secondaryColorHex: Long = 0xFF1D1128,
    val useGradient: Boolean = true,
    val gradientAngleDeg: Float = 45f,
    val imagePath: String? = null,
    val transparentBackground: Boolean = false
)

data class MediaSourceConfig(
    val localFilePath: String? = null,
    val builtInDrawableRes: Int? = null,
    val loopVideo: Boolean = true,
    val videoVolume: Float = 0.8f
)

/**
 * Coordinates (x, y, width, height) are normalized 0.0f..1.0f relative to the logical scene canvas
 * (e.g., 1920x1080) so scenes remain device-independent across phones, foldables, and tablets.
 */
data class SceneLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: SourceType,
    val x: Float = 0.0f,
    val y: Float = 0.0f,
    val width: Float = 0.5f,
    val height: Float = 1.0f,
    val zIndex: Int = 0,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1.0f,
    val rotationDeg: Float = 0f,
    val lockAspectRatio: Boolean = false,
    val scaleMode: ScaleMode = ScaleMode.FILL,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val crop: CropRect = CropRect(),
    val border: BorderStyle = BorderStyle(),
    val cameraConfig: CameraSourceConfig = CameraSourceConfig(),
    val screenCaptureConfig: ScreenCaptureSourceConfig = ScreenCaptureSourceConfig(),
    val networkConfig: NetworkSourceConfig = NetworkSourceConfig(),
    val textConfig: TextSourceConfig = TextSourceConfig(),
    val backgroundConfig: BackgroundSourceConfig = BackgroundSourceConfig(),
    val mediaConfig: MediaSourceConfig = MediaSourceConfig()
) {
    val widthPercent: Int get() = Math.round(width * 100f).coerceIn(1, 100)
    val heightPercent: Int get() = Math.round(height * 100f).coerceIn(1, 100)
    val xPercent: Int get() = Math.round(x * 100f).coerceIn(-100, 100)
    val yPercent: Int get() = Math.round(y * 100f).coerceIn(-100, 100)

    fun pixelX(canvasWidth: Int): Int = Math.round(x * canvasWidth)
    fun pixelY(canvasHeight: Int): Int = Math.round(y * canvasHeight)
    fun pixelWidth(canvasWidth: Int): Int = Math.round(width * canvasWidth).coerceAtLeast(16)
    fun pixelHeight(canvasHeight: Int): Int = Math.round(height * canvasHeight).coerceAtLeast(16)
}

data class Scene(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val canvasPreset: CanvasPreset = CanvasPreset.LANDSCAPE_16_9,
    val canvasWidth: Int = 1920,
    val canvasHeight: Int = 1080,
    val backgroundColorHex: Long = 0xFF0B0E17,
    val backgroundGradientEndHex: Long = 0xFF141B2D,
    val useGradientBackground: Boolean = true,
    val transparentBackground: Boolean = false,
    val layers: List<SceneLayer> = emptyList(),
    val isSystemPreset: Boolean = false
) {
    val aspectRatio: Float
        get() = if (canvasHeight > 0) canvasWidth.toFloat() / canvasHeight.toFloat() else 16f / 9f

    fun sortedLayers(): List<SceneLayer> = layers.sortedBy { it.zIndex }
}

object BuiltInOverlayCatalog {
    data class BuiltInOverlayItem(
        val id: String,
        val name: String,
        val drawableRes: Int,
        val description: String
    )

    val items = listOf(
        BuiltInOverlayItem(
            id = "cyber_cyan",
            name = "Cyber Cyan HUD Frame",
            drawableRes = R.drawable.img_frame_cyber_cyan,
            description = "Transparent neon cyan & crimson corner HUD camera border"
        ),
        BuiltInOverlayItem(
            id = "broadcast_gold",
            name = "Pro Broadcast Dual Frame",
            drawableRes = R.drawable.img_frame_broadcast_gold,
            description = "Transparent crimson & gold rounded broadcast frame"
        ),
        BuiltInOverlayItem(
            id = "live_badge",
            name = "Studio Watermark Badge",
            drawableRes = R.drawable.img_badge_live_studio,
            description = "Transparent broadcast corner logo watermark"
        )
    )
}
