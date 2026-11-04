package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterFrames
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.CanvasPreset
import com.example.model.LayoutPreset
import com.example.model.SceneLayer
import com.example.model.SceneTransitionType
import com.example.model.ScreenCaptureMode
import com.example.model.SourceType
import com.example.sources.CameraHardwareState
import com.example.sources.ScreenCaptureState
import com.example.ui.theme.StudioAccentSky
import com.example.ui.theme.StudioAccentViolet
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCardBg
import com.example.ui.theme.StudioElevatedBg
import com.example.ui.theme.StudioLiveRed
import com.example.ui.theme.StudioSecondaryBg
import com.example.ui.theme.StudioSuccessGreen
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.StudioWarningAmber
import com.example.viewmodel.StudioUiState

private data class SourceShortcutSpec(
    val type: SourceType,
    val shortTitle: String,
    val icon: ImageVector,
    val accent: Color
)

/**
 * Compact 2-Row × 3-Column Source Shortcuts Grid for the Vertical Home Screen (Section 4)
 */
@Composable
fun StudioSourceShortcutsGrid(
    cameraState: CameraHardwareState,
    screenCaptureState: ScreenCaptureState,
    onQuickSelectOrAddSource: (SourceType) -> Unit,
    onPickImageFile: (SourceType) -> Unit,
    onPickVideoFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shortcuts = listOf(
        SourceShortcutSpec(
            type = SourceType.SCREEN_CAPTURE,
            shortTitle = "Screen",
            icon = Icons.Default.ScreenShare,
            accent = if (screenCaptureState.isCapturing) StudioSuccessGreen else StudioAccentViolet
        ),
        SourceShortcutSpec(
            type = SourceType.CAMERA,
            shortTitle = "Camera",
            icon = Icons.Default.CameraFront,
            accent = if (cameraState.isBound) StudioAccentSky else StudioAccentViolet
        ),
        SourceShortcutSpec(
            type = SourceType.IMAGE,
            shortTitle = "Image",
            icon = Icons.Default.Image,
            accent = StudioAccentSky
        ),
        SourceShortcutSpec(
            type = SourceType.PNG_OVERLAY,
            shortTitle = "PNG Overlay",
            icon = Icons.Default.FilterFrames,
            accent = StudioAccentViolet
        ),
        SourceShortcutSpec(
            type = SourceType.VIDEO_FILE,
            shortTitle = "Video",
            icon = Icons.Default.Movie,
            accent = StudioAccentSky
        ),
        SourceShortcutSpec(
            type = SourceType.TEXT,
            shortTitle = "Text",
            icon = Icons.Default.TextFields,
            accent = StudioSuccessGreen
        )
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("studio_source_shortcuts_grid")
    ) {
        shortcuts.chunked(3).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowItems.forEach { item ->
                    Surface(
                        onClick = {
                            when (item.type) {
                                SourceType.IMAGE -> onPickImageFile(SourceType.IMAGE)
                                SourceType.VIDEO_FILE -> onPickVideoFile()
                                else -> onQuickSelectOrAddSource(item.type)
                            }
                        },
                        color = StudioCardBg,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("shortcut_source_${item.type.name.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(item.accent.copy(alpha = 0.16f))
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = item.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = item.shortTitle,
                                color = StudioTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Simple Source Manager for the `Sources` Tab & Add Source Sheet (Section 6)
 */
@Composable
fun AddSourceSheetContent(
    uiState: StudioUiState,
    cameraState: CameraHardwareState,
    screenCaptureState: ScreenCaptureState,
    onAddSource: (SourceType, Int?) -> Unit,
    onSelectLayer: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onOpenLayerInspector: (String) -> Unit,
    onRequestScreenCapture: (ScreenCaptureMode) -> Unit,
    onStopScreenCapture: () -> Unit,
    onPickImageFile: (SourceType) -> Unit,
    onPickVideoFile: () -> Unit,
    onDeleteLayer: (String) -> Unit = {},
    onRenameLayer: (String, String) -> Unit = { _, _ -> }
) {
    val activeScene = uiState.activeScene

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("add_source_panel")
    ) {
        // Section 6: Active Scene Sources List with Icons, Status Indicators, Visibility Toggle & More Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Active Scene Sources",
                    color = StudioTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "${activeScene.name} • ${activeScene.layers.size} sources",
                    color = StudioTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Source Cards with Layer Names & Delete Button
        activeScene.sortedLayers().asReversed().forEachIndexed { index, layer ->
            val isSelected = layer.id == uiState.selectedLayerId
            val (statusLabel, statusColor) = resolveSourceStatus(
                layer = layer,
                cameraState = cameraState,
                screenCaptureState = screenCaptureState
            )

            Surface(
                onClick = { onSelectLayer(layer.id) },
                color = if (isSelected) StudioElevatedBg else StudioCardBg,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) StudioAccentViolet else StudioBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("source_manager_row_${layer.id}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Left: Source Icon + Layer Name + Connection Status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(statusColor.copy(alpha = 0.16f))
                        ) {
                            Icon(
                                imageVector = iconForSourceType(layer.type),
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(StudioAccentViolet.copy(alpha = 0.22f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Layer ${index + 1}",
                                        color = StudioAccentViolet,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = layer.name,
                                    color = StudioTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Text(
                                    text = "$statusLabel • ${layer.widthPercent}%×${layer.heightPercent}% • Z:${layer.zIndex}",
                                    color = StudioTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Right: Visibility toggle + More options/Inspector + Delete Source Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StudioIconButton(
                            icon = if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (layer.visible) "Hide Source" else "Show Source",
                            isActive = layer.visible,
                            activeColor = StudioAccentSky,
                            tint = if (layer.visible) StudioAccentSky else StudioTextMuted,
                            onClick = { onToggleVisibility(layer.id) },
                            testTag = "source_vis_${layer.id}"
                        )
                        StudioIconButton(
                            icon = Icons.Default.MoreVert,
                            contentDescription = "Source Options",
                            isActive = isSelected,
                            activeColor = StudioAccentViolet,
                            onClick = {
                                onSelectLayer(layer.id)
                                onOpenLayerInspector(layer.id)
                            },
                            testTag = "source_more_${layer.id}"
                        )
                        StudioIconButton(
                            icon = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Source ${layer.name}",
                            tint = StudioLiveRed,
                            onClick = { onDeleteLayer(layer.id) },
                            testTag = "source_delete_${layer.id}"
                        )
                    }
                }
            }
        }

        // Add Source Grid (Clean 2-Column Layout with 1-Line Titles)
        Text(
            text = "Add Source to Scene",
            color = StudioTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )

        val addableSources = listOf(
            SourceShortcutSpec(SourceType.SCREEN_CAPTURE, "Screen Capture", Icons.Default.ScreenShare, StudioAccentViolet),
            SourceShortcutSpec(SourceType.MOONLIGHT_PC, "Moonlight PC", Icons.Default.DesktopWindows, StudioAccentSky),
            SourceShortcutSpec(SourceType.CAMERA, "Camera", Icons.Default.CameraFront, StudioAccentSky),
            SourceShortcutSpec(SourceType.PNG_OVERLAY, "PNG Overlay", Icons.Default.FilterFrames, StudioAccentViolet),
            SourceShortcutSpec(SourceType.IMAGE, "Image", Icons.Default.Image, StudioAccentSky),
            SourceShortcutSpec(SourceType.VIDEO_FILE, "Video Clip", Icons.Default.Movie, StudioAccentSky),
            SourceShortcutSpec(SourceType.TEXT, "Text Label", Icons.Default.TextFields, StudioSuccessGreen),
            SourceShortcutSpec(SourceType.LOGO, "Logo Badge", Icons.Default.BrandingWatermark, StudioWarningAmber),
            SourceShortcutSpec(SourceType.BACKGROUND, "Background", Icons.Default.Gradient, StudioAccentViolet),
            SourceShortcutSpec(SourceType.NETWORK_VIDEO, "LAN Stream", Icons.Default.SettingsEthernet, StudioTextSecondary)
        )

        addableSources.chunked(2).forEach { pair ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                pair.forEach { spec ->
                    StudioSecondaryButton(
                        text = spec.shortTitle,
                        icon = spec.icon,
                        accentColor = spec.accent,
                        onClick = {
                            when (spec.type) {
                                SourceType.IMAGE -> onPickImageFile(SourceType.IMAGE)
                                SourceType.VIDEO_FILE -> onPickVideoFile()
                                else -> onAddSource(spec.type, null)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "add_source_${spec.type.name.lowercase()}"
                    )
                }
            }
        }

        // Transparent PNG Overlay Presets + Custom Picker
        StudioCard(
            backgroundColor = StudioCardBg,
            borderColor = StudioBorder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Transparent PNG Overlay Presets",
                color = StudioTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StudioSecondaryButton(
                    text = "Cyan Frame",
                    onClick = { onAddSource(SourceType.PNG_OVERLAY, R.drawable.img_frame_cyber_cyan) },
                    modifier = Modifier.weight(1f),
                    testTag = "add_png_cyan_frame"
                )
                StudioSecondaryButton(
                    text = "Gold Frame",
                    onClick = { onAddSource(SourceType.PNG_OVERLAY, R.drawable.img_frame_broadcast_gold) },
                    modifier = Modifier.weight(1f),
                    testTag = "add_png_gold_frame"
                )
                StudioPrimaryButton(
                    text = "Import PNG",
                    onClick = { onPickImageFile(SourceType.PNG_OVERLAY) },
                    modifier = Modifier.weight(1f),
                    testTag = "import_custom_png_overlay"
                )
            }
        }

        // Screen Capture Controls
        StudioCard(
            backgroundColor = StudioCardBg,
            borderColor = StudioBorder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Phone Screen Capture",
                        color = StudioTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = screenCaptureState.statusMessage,
                        color = StudioTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (screenCaptureState.isCapturing) {
                    StudioDangerButton(
                        text = "Stop",
                        icon = Icons.Default.Stop,
                        onClick = onStopScreenCapture
                    )
                } else {
                    StudioSecondaryButton(
                        text = "Capture Screen",
                        icon = Icons.Default.ScreenShare,
                        onClick = { onRequestScreenCapture(ScreenCaptureMode.ANDROID_14_SINGLE_APP) },
                        testTag = "btn_start_screen_capture"
                    )
                }
            }
        }
    }
}

private fun resolveSourceStatus(
    layer: SceneLayer,
    cameraState: CameraHardwareState,
    screenCaptureState: ScreenCaptureState
): Pair<String, Color> {
    if (!layer.visible) {
        return "Hidden" to StudioTextMuted
    }
    return when (layer.type) {
        SourceType.SCREEN_CAPTURE -> if (screenCaptureState.isCapturing) {
            "Capturing • Live" to StudioSuccessGreen
        } else {
            "Standby" to StudioTextSecondary
        }
        SourceType.CAMERA -> if (cameraState.isBound) {
            "Active" to StudioSuccessGreen
        } else {
            "Ready" to StudioAccentSky
        }
        SourceType.PNG_OVERLAY -> "Added" to StudioAccentViolet
        SourceType.IMAGE -> "Added" to StudioAccentSky
        SourceType.VIDEO_FILE -> "Loaded" to StudioAccentSky
        SourceType.TEXT -> "Active" to StudioSuccessGreen
        SourceType.LOGO -> "Active" to StudioWarningAmber
        SourceType.BACKGROUND -> "Active" to StudioAccentViolet
        SourceType.NETWORK_VIDEO -> "Standby" to StudioAccentSky
        SourceType.MOONLIGHT_PC -> "Streaming • PC" to StudioSuccessGreen
    }
}

fun iconForSourceType(type: SourceType): ImageVector = when (type) {
    SourceType.SCREEN_CAPTURE -> Icons.Default.ScreenShare
    SourceType.CAMERA -> Icons.Default.CameraFront
    SourceType.PNG_OVERLAY -> Icons.Default.FilterFrames
    SourceType.IMAGE -> Icons.Default.Image
    SourceType.VIDEO_FILE -> Icons.Default.Movie
    SourceType.TEXT -> Icons.Default.TextFields
    SourceType.LOGO -> Icons.Default.BrandingWatermark
    SourceType.BACKGROUND -> Icons.Default.Gradient
    SourceType.NETWORK_VIDEO -> Icons.Default.SettingsEthernet
    SourceType.MOONLIGHT_PC -> Icons.Default.DesktopWindows
}

/**
 * Scenes & Layout Presets Panel for the `Scenes` Tab & `Layout` Sheet
 */
@Composable
fun SceneManagerSheetContent(
    uiState: StudioUiState,
    onSelectScene: (String) -> Unit,
    onCreateScene: (String, CanvasPreset) -> Unit,
    onDuplicateScene: (String) -> Unit,
    onRenameScene: (String, String) -> Unit,
    onDeleteScene: (String) -> Unit,
    onApplyLayoutPreset: (LayoutPreset) -> Unit,
    onUpdateCanvasPreset: (CanvasPreset) -> Unit,
    onSelectTransition: (SceneTransitionType) -> Unit,
    onSaveSceneExplicitly: () -> Unit,
    onDeleteSourceFromScene: (sceneId: String, layerId: String) -> Unit = { _, _ -> }
) {
    var newSceneName by remember { mutableStateOf("") }
    var editingSceneId by remember { mutableStateOf<String?>(null) }
    var editingSceneName by remember { mutableStateOf("") }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("scene_manager_panel")
    ) {
        // Header Row + Explicit Save Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Scenes & Layouts",
                    color = StudioTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "Active: ${uiState.activeScene.name}",
                    color = StudioTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StudioPrimaryButton(
                text = "Save Scene",
                icon = Icons.Default.Save,
                onClick = onSaveSceneExplicitly,
                testTag = "save_scene_button"
            )
        }

        // Quick PC + Camera Split Layout Presets
        StudioCard(
            backgroundColor = StudioCardBg,
            borderColor = StudioBorder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Instant Split Layouts (Screen + Camera)",
                color = StudioTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                LayoutPreset.entries.forEach { preset ->
                    StudioSecondaryButton(
                        text = preset.shortLabel,
                        onClick = { onApplyLayoutPreset(preset) },
                        testTag = "layout_preset_${preset.name.lowercase()}"
                    )
                }
            }
        }

        // Scene List Cards
        Text(
            text = "Saved Scenes (${uiState.scenes.size})",
            color = StudioTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )

        uiState.scenes.forEach { scene ->
            val isActive = scene.id == uiState.activeSceneId
            StudioCard(
                backgroundColor = if (isActive) StudioElevatedBg else StudioCardBg,
                borderColor = if (isActive) StudioAccentViolet else StudioBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectScene(scene.id) }
                    .testTag("scene_card_${scene.id}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isActive) StudioAccentViolet.copy(alpha = 0.22f)
                                    else StudioSecondaryBg
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = if (isActive) StudioAccentViolet else StudioTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = scene.name,
                                color = StudioTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${scene.canvasWidth}×${scene.canvasHeight} • ${scene.layers.size} sources",
                                color = StudioTextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StudioIconButton(
                            icon = Icons.Default.Edit,
                            contentDescription = "Rename Scene",
                            onClick = {
                                editingSceneId = if (editingSceneId == scene.id) null else scene.id
                                editingSceneName = scene.name
                            }
                        )
                        StudioIconButton(
                            icon = Icons.Default.ContentCopy,
                            contentDescription = "Duplicate Scene",
                            onClick = { onDuplicateScene(scene.id) },
                            testTag = "duplicate_scene_${scene.id}"
                        )
                        StudioIconButton(
                            icon = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Scene",
                            tint = StudioLiveRed,
                            onClick = { onDeleteScene(scene.id) },
                            testTag = "delete_scene_${scene.id}"
                        )
                    }
                }

                if (editingSceneId == scene.id) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = editingSceneName,
                            onValueChange = { editingSceneName = it },
                            singleLine = true,
                            label = { Text("Scene Name", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        StudioPrimaryButton(
                            text = "Save",
                            onClick = {
                                onRenameScene(scene.id, editingSceneName)
                                editingSceneId = null
                            }
                        )
                    }
                }

                // Sources & Layers in this Scene with Layer Names and Delete Button
                if (scene.layers.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Sources & Layers (${scene.layers.size})",
                        color = StudioTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    scene.sortedLayers().asReversed().forEachIndexed { idx, layer ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(StudioSecondaryBg.copy(alpha = 0.7f))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(StudioAccentViolet.copy(alpha = 0.22f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "Layer ${idx + 1}",
                                        color = StudioAccentViolet,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = layer.name,
                                    color = StudioTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            StudioIconButton(
                                icon = Icons.Default.DeleteOutline,
                                contentDescription = "Delete ${layer.name} from scene",
                                tint = StudioLiveRed,
                                onClick = { onDeleteSourceFromScene(scene.id, layer.id) },
                                testTag = "delete_source_in_scene_${scene.id}_${layer.id}"
                            )
                        }
                    }
                }
            }
        }

        // Create New Scene Card
        StudioCard(
            backgroundColor = StudioCardBg,
            borderColor = StudioBorder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Create New Scene",
                color = StudioTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = newSceneName,
                    onValueChange = { newSceneName = it },
                    singleLine = true,
                    placeholder = { Text("New Scene Name", fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StudioAccentViolet,
                        unfocusedBorderColor = StudioBorder,
                        focusedTextColor = StudioTextPrimary,
                        unfocusedTextColor = StudioTextPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("new_scene_name_input")
                )
                StudioPrimaryButton(
                    text = "Create",
                    icon = Icons.Default.Add,
                    onClick = {
                        onCreateScene(newSceneName, CanvasPreset.LANDSCAPE_16_9)
                        newSceneName = ""
                    },
                    testTag = "create_scene_button"
                )
            }
        }

        // Canvas Aspect Ratio & Scene Transition Row
        StudioCard(
            backgroundColor = StudioCardBg,
            borderColor = StudioBorder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Canvas Aspect Ratio",
                color = StudioTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    CanvasPreset.LANDSCAPE_16_9 to "16:9 HD",
                    CanvasPreset.PORTRAIT_9_16 to "9:16 Vertical",
                    CanvasPreset.SQUARE_1_1 to "1:1 Square"
                ).forEach { (preset, shortLabel) ->
                    StudioSecondaryButton(
                        text = shortLabel,
                        isSelected = uiState.activeScene.canvasPreset == preset,
                        accentColor = StudioAccentSky,
                        onClick = { onUpdateCanvasPreset(preset) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Scene Switch Transition",
                color = StudioTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SceneTransitionType.entries.forEach { t ->
                    val shortName = when (t) {
                        SceneTransitionType.CUT -> "Cut"
                        SceneTransitionType.FADE -> "Fade"
                        SceneTransitionType.DIP_TO_BLACK -> "Dip Black"
                    }
                    StudioSecondaryButton(
                        text = shortName,
                        isSelected = uiState.transitionType == t,
                        accentColor = StudioAccentViolet,
                        onClick = { onSelectTransition(t) },
                        modifier = Modifier.weight(1f),
                        testTag = "transition_${t.name.lowercase()}"
                    )
                }
            }
        }
    }
}
