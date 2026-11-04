package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.AudioChannelConfig
import com.example.audio.AudioMixerState
import com.example.model.CameraFacing
import com.example.model.CameraSourceConfig
import com.example.model.CropRect
import com.example.model.ScaleMode
import com.example.model.SceneLayer
import com.example.model.SourceType
import com.example.monitoring.DeviceDiagnosticsState
import com.example.sources.CameraHardwareState
import com.example.sources.NetworkFeedStatus
import com.example.storage.StreamCredentials
import com.example.storage.StreamQualityPreset
import com.example.streaming.StreamConnectionStatus
import com.example.streaming.StreamEngineTelemetry
import com.example.ui.theme.StudioAccentSky
import com.example.ui.theme.StudioAccentViolet
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCardBg
import com.example.ui.theme.StudioElevatedBg
import com.example.ui.theme.StudioLiveRed
import com.example.ui.theme.StudioMainBg
import com.example.ui.theme.StudioSuccessGreen
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.StudioWarningAmber
import com.example.viewmodel.StudioUiState
import kotlin.math.roundToInt

@Composable
fun LayersSheetContent(
    uiState: StudioUiState,
    onSelectLayer: (String) -> Unit,
    onBringForward: (String) -> Unit,
    onSendBackward: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    onDuplicateLayer: (String) -> Unit,
    onDeleteLayer: (String) -> Unit,
    onOpenInspector: () -> Unit
) {
    val layersTopFirst = uiState.activeScene.sortedLayers().asReversed()

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("layers_stack_panel")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Scene Layers (${layersTopFirst.size})",
                    color = StudioTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "Top layers render in front of lower layers",
                    color = StudioTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StudioPrimaryButton(
                text = "Properties",
                icon = Icons.Default.Tune,
                onClick = onOpenInspector,
                testTag = "open_inspector_from_layers"
            )
        }

        layersTopFirst.forEach { layer ->
            val isSelected = layer.id == uiState.selectedLayerId
            StudioCard(
                backgroundColor = if (isSelected) StudioElevatedBg else StudioCardBg,
                borderColor = if (isSelected) StudioAccentViolet else StudioBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectLayer(layer.id) }
                    .testTag("layer_row_${layer.id}")
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
                                .background(StudioAccentViolet.copy(alpha = 0.16f))
                        ) {
                            Icon(
                                imageVector = iconForSourceType(layer.type),
                                contentDescription = null,
                                tint = StudioAccentViolet,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = layer.name,
                                color = StudioTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${layer.widthPercent}% × ${layer.heightPercent}% • Z:${layer.zIndex}",
                                color = StudioTextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        StudioIconButton(
                            icon = Icons.Default.ArrowUpward,
                            contentDescription = "Bring Forward",
                            onClick = { onBringForward(layer.id) },
                            testTag = "layer_up_${layer.id}"
                        )
                        StudioIconButton(
                            icon = Icons.Default.ArrowDownward,
                            contentDescription = "Send Backward",
                            onClick = { onSendBackward(layer.id) },
                            testTag = "layer_down_${layer.id}"
                        )
                        StudioIconButton(
                            icon = if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle Visibility",
                            isActive = layer.visible,
                            activeColor = StudioAccentSky,
                            onClick = { onToggleVisibility(layer.id) },
                            testTag = "layer_vis_${layer.id}"
                        )
                        StudioIconButton(
                            icon = if (layer.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Toggle Lock",
                            isActive = layer.locked,
                            activeColor = StudioWarningAmber,
                            onClick = { onToggleLock(layer.id) },
                            testTag = "layer_lock_${layer.id}"
                        )
                        StudioIconButton(
                            icon = Icons.Default.ContentCopy,
                            contentDescription = "Duplicate Layer",
                            onClick = { onDuplicateLayer(layer.id) },
                            testTag = "layer_dup_${layer.id}"
                        )
                        StudioIconButton(
                            icon = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Layer",
                            tint = StudioLiveRed,
                            onClick = { onDeleteLayer(layer.id) },
                            testTag = "layer_del_${layer.id}"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InspectorSheetContent(
    selectedLayer: SceneLayer?,
    cameraState: CameraHardwareState,
    networkFeedState: NetworkFeedStatus,
    onUpdateLayer: ((SceneLayer) -> SceneLayer) -> Unit,
    onSetExactWidthPercent: (Int) -> Unit,
    onUpdateCameraConfig: ((CameraSourceConfig) -> CameraSourceConfig) -> Unit,
    onPickCustomPngBorder: () -> Unit
) {
    if (selectedLayer == null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Tap any source on the 16:9 preview to customize its size, crop, border, or hardware settings.",
                color = StudioTextSecondary,
                fontSize = 13.sp
            )
        }
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("layer_inspector_panel")
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedLayer.name,
                    color = StudioTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${selectedLayer.type.displayName} • ${selectedLayer.widthPercent}% × ${selectedLayer.heightPercent}%",
                    color = StudioAccentSky,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            StudioSecondaryButton(
                text = if (selectedLayer.lockAspectRatio) "Aspect Locked" else "Free Aspect",
                isSelected = selectedLayer.lockAspectRatio,
                onClick = { onUpdateLayer { it.copy(lockAspectRatio = !it.lockAspectRatio) } }
            )
        }

        // Exact Percentage Width Presets (50%, 60%, 65%, 70%, 100%)
        StudioCard(
            backgroundColor = StudioCardBg,
            borderColor = StudioBorder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Quick Scene Width (Auto-balances PC + Camera)",
                color = StudioTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(50, 60, 65, 70, 100).forEach { pct ->
                    StudioSecondaryButton(
                        text = "$pct%",
                        isSelected = selectedLayer.widthPercent == pct,
                        accentColor = StudioAccentViolet,
                        onClick = { onSetExactWidthPercent(pct) },
                        modifier = Modifier.weight(1f),
                        testTag = "pct_width_$pct"
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Width & Height Sliders
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Width: ${selectedLayer.widthPercent}%", color = StudioTextSecondary, fontSize = 12.sp)
                Text("X: ${selectedLayer.xPercent}% • Y: ${selectedLayer.yPercent}%", color = StudioTextSecondary, fontSize = 12.sp)
            }
            Slider(
                value = selectedLayer.width,
                onValueChange = { w -> onUpdateLayer { it.copy(width = w.coerceIn(0.08f, 1.0f)) } },
                valueRange = 0.08f..1.0f,
                colors = SliderDefaults.colors(thumbColor = StudioAccentViolet, activeTrackColor = StudioAccentViolet),
                modifier = Modifier.testTag("slider_layer_width")
            )

            Text("Height: ${selectedLayer.heightPercent}%", color = StudioTextSecondary, fontSize = 12.sp)
            Slider(
                value = selectedLayer.height,
                onValueChange = { h -> onUpdateLayer { it.copy(height = h.coerceIn(0.08f, 1.0f)) } },
                valueRange = 0.08f..1.0f,
                colors = SliderDefaults.colors(thumbColor = StudioAccentViolet, activeTrackColor = StudioAccentViolet),
                modifier = Modifier.testTag("slider_layer_height")
            )

            // Scale Mode + Rotate Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ScaleMode.entries.forEach { mode ->
                    StudioSecondaryButton(
                        text = mode.displayName,
                        isSelected = selectedLayer.scaleMode == mode,
                        accentColor = StudioAccentSky,
                        onClick = { onUpdateLayer { it.copy(scaleMode = mode) } },
                        modifier = Modifier.weight(1f),
                        testTag = "scale_mode_${mode.name.lowercase()}"
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StudioSecondaryButton(
                    text = "Rotate 90°",
                    onClick = {
                        onUpdateLayer {
                            it.copy(rotationDeg = (it.rotationDeg + 90f) % 360f)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                StudioSecondaryButton(
                    text = "Reset Rotation",
                    onClick = {
                        onUpdateLayer { it.copy(rotationDeg = 0f) }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Crop & Opacity Section
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
                Text(
                    text = "Edge Crop & Opacity",
                    color = StudioTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                StudioSecondaryButton(
                    text = "Reset Crop",
                    onClick = {
                        onUpdateLayer {
                            it.copy(
                                crop = CropRect(),
                                opacity = 1f,
                                rotationDeg = 0f
                            )
                        }
                    }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Left (${(selectedLayer.crop.left * 100).roundToInt()}%)", color = StudioTextSecondary, fontSize = 11.sp)
                    Slider(
                        value = selectedLayer.crop.left,
                        onValueChange = { v -> onUpdateLayer { it.copy(crop = it.crop.copy(left = v)) } },
                        valueRange = 0f..0.4f
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Right (${(selectedLayer.crop.right * 100).roundToInt()}%)", color = StudioTextSecondary, fontSize = 11.sp)
                    Slider(
                        value = selectedLayer.crop.right,
                        onValueChange = { v -> onUpdateLayer { it.copy(crop = it.crop.copy(right = v)) } },
                        valueRange = 0f..0.4f
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Top (${(selectedLayer.crop.top * 100).roundToInt()}%)", color = StudioTextSecondary, fontSize = 11.sp)
                    Slider(
                        value = selectedLayer.crop.top,
                        onValueChange = { v -> onUpdateLayer { it.copy(crop = it.crop.copy(top = v)) } },
                        valueRange = 0f..0.4f
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bottom (${(selectedLayer.crop.bottom * 100).roundToInt()}%)", color = StudioTextSecondary, fontSize = 11.sp)
                    Slider(
                        value = selectedLayer.crop.bottom,
                        onValueChange = { v -> onUpdateLayer { it.copy(crop = it.crop.copy(bottom = v)) } },
                        valueRange = 0f..0.4f
                    )
                }
            }

            Text("Opacity (${(selectedLayer.opacity * 100).roundToInt()}%)", color = StudioTextSecondary, fontSize = 11.sp)
            Slider(
                value = selectedLayer.opacity,
                onValueChange = { v -> onUpdateLayer { it.copy(opacity = v) } },
                valueRange = 0.1f..1f
            )
        }

        // Border, Corner Radius & PNG Frame Overlay Section
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
                Text(
                    text = "Border, Shape & PNG Frame",
                    color = StudioTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Switch(
                    checked = selectedLayer.border.enabled,
                    onCheckedChange = { enabled ->
                        onUpdateLayer { it.copy(border = it.border.copy(enabled = enabled)) }
                    },
                    modifier = Modifier.testTag("switch_border_enabled")
                )
            }

            Spacer(Modifier.height(8.dp))

            // Color Swatches
            val borderColors = listOf(
                0xFF8B5CF6L to "Violet",
                0xFF38BDF8L to "Sky",
                0xFFEF4444L to "Red",
                0xFF22C55EL to "Green",
                0xFFF59E0BL to "Gold",
                0xFFFFFFFFL to "White"
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                borderColors.forEach { (hex, label) ->
                    val selected = selectedLayer.border.colorHex == hex
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) Color.White else StudioBorder,
                                shape = CircleShape
                            )
                            .clickable {
                                onUpdateLayer {
                                    it.copy(border = it.border.copy(enabled = true, colorHex = hex))
                                }
                            }
                            .testTag("border_color_$label")
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Border Thickness (${selectedLayer.border.thicknessDp.roundToInt()} px)",
                color = StudioTextSecondary,
                fontSize = 11.sp
            )
            Slider(
                value = selectedLayer.border.thicknessDp,
                onValueChange = { t ->
                    onUpdateLayer { it.copy(border = it.border.copy(thicknessDp = t)) }
                },
                valueRange = 0f..18f,
                modifier = Modifier.testTag("slider_border_thickness")
            )

            Text(
                text = "Corner Shape (${selectedLayer.border.cornerRadiusPercent.roundToInt()}% — 0% Rect • 50% Circle)",
                color = StudioTextSecondary,
                fontSize = 11.sp
            )
            Slider(
                value = selectedLayer.border.cornerRadiusPercent,
                onValueChange = { r ->
                    onUpdateLayer { it.copy(border = it.border.copy(cornerRadiusPercent = r)) }
                },
                valueRange = 0f..50f,
                modifier = Modifier.testTag("slider_corner_radius")
            )

            // PNG Frame Overlays
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StudioSecondaryButton(
                    text = "No Frame",
                    isSelected = selectedLayer.border.builtInPngFrameRes == null &&
                        selectedLayer.border.customPngFramePath == null,
                    onClick = {
                        onUpdateLayer {
                            it.copy(
                                border = it.border.copy(
                                    builtInPngFrameRes = null,
                                    customPngFramePath = null
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                StudioSecondaryButton(
                    text = "Cyan PNG",
                    isSelected = selectedLayer.border.builtInPngFrameRes == R.drawable.img_frame_cyber_cyan,
                    onClick = {
                        onUpdateLayer {
                            it.copy(
                                border = it.border.copy(
                                    builtInPngFrameRes = R.drawable.img_frame_cyber_cyan,
                                    customPngFramePath = null
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "border_png_cyan"
                )
                StudioSecondaryButton(
                    text = "Custom PNG",
                    onClick = onPickCustomPngBorder,
                    modifier = Modifier.weight(1f),
                    testTag = "border_png_custom"
                )
            }
        }

        // Source-specific controls

        if (selectedLayer.type == SourceType.CAMERA) {
            CameraHardwareCard(
                cfg = selectedLayer.cameraConfig,
                cameraState = cameraState,
                onUpdateCameraConfig = onUpdateCameraConfig
            )
        }

        if (selectedLayer.type == SourceType.TEXT) {
            StudioCard(
                backgroundColor = StudioCardBg,
                borderColor = StudioBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Text Overlay Content",
                    color = StudioTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = selectedLayer.textConfig.text,
                    onValueChange = { txt ->
                        onUpdateLayer { it.copy(textConfig = it.textConfig.copy(text = txt)) }
                    },
                    singleLine = true,
                    label = { Text("Overlay Text") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CameraHardwareCard(
    cfg: CameraSourceConfig,
    cameraState: CameraHardwareState,
    onUpdateCameraConfig: ((CameraSourceConfig) -> CameraSourceConfig) -> Unit
) {
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
                    text = "Camera Hardware Controls",
                    color = StudioTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${cameraState.resolutionLabel} • ${cameraState.measuredFps} FPS",
                    color = StudioAccentSky,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StudioPrimaryButton(
                text = if (cfg.facing == CameraFacing.FRONT) "Front Cam" else "Rear Cam",
                icon = Icons.Default.Cameraswitch,
                onClick = {
                    val next = if (cfg.facing == CameraFacing.FRONT) CameraFacing.REAR else CameraFacing.FRONT
                    onUpdateCameraConfig {
                        it.copy(facing = next, mirrorHorizontally = next == CameraFacing.FRONT)
                    }
                },
                testTag = "btn_switch_camera_facing"
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StudioSecondaryButton(
                text = "720p",
                isSelected = cfg.targetHeight == 720,
                onClick = { onUpdateCameraConfig { it.copy(targetWidth = 1280, targetHeight = 720) } },
                modifier = Modifier.weight(1f)
            )
            StudioSecondaryButton(
                text = "1080p",
                isSelected = cfg.targetHeight == 1080,
                onClick = { onUpdateCameraConfig { it.copy(targetWidth = 1920, targetHeight = 1080) } },
                modifier = Modifier.weight(1f)
            )
            StudioSecondaryButton(
                text = "30 FPS",
                isSelected = cfg.targetFps == 30,
                onClick = { onUpdateCameraConfig { it.copy(targetFps = 30) } },
                modifier = Modifier.weight(1f)
            )
            StudioSecondaryButton(
                text = "60 FPS",
                isSelected = cfg.targetFps == 60,
                onClick = { onUpdateCameraConfig { it.copy(targetFps = 60) } },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StudioSecondaryButton(
                text = "Mirror H",
                isSelected = cfg.mirrorHorizontally,
                onClick = { onUpdateCameraConfig { it.copy(mirrorHorizontally = !it.mirrorHorizontally) } },
                modifier = Modifier.weight(1f)
            )
            StudioSecondaryButton(
                text = "Flip V",
                isSelected = cfg.flipVertically,
                onClick = { onUpdateCameraConfig { it.copy(flipVertically = !it.flipVertically) } },
                modifier = Modifier.weight(1f)
            )
            StudioSecondaryButton(
                text = "Flashlight",
                isSelected = cfg.torchEnabled,
                onClick = { onUpdateCameraConfig { it.copy(torchEnabled = !it.torchEnabled) } },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Text("Zoom (${String.format("%.1fx", cfg.zoomRatio)})", color = StudioTextSecondary, fontSize = 11.sp)
        Slider(
            value = cfg.zoomRatio,
            onValueChange = { z -> onUpdateCameraConfig { it.copy(zoomRatio = z) } },
            valueRange = 1f..cameraState.maxZoom.coerceAtLeast(2f)
        )
    }
}

/**
 * Audio Mixer Tab & Bottom Sheet (`Audio` Tab)
 */
@Composable
fun AudioMixerSheetContent(
    audioState: AudioMixerState,
    onChannelVolumeChanged: (String, Float) -> Unit,
    onToggleChannelMute: (String) -> Unit,
    onMasterVolumeChanged: (Float) -> Unit,
    onToggleMasterMute: () -> Unit,
    onToggleNoiseSuppression: () -> Unit,
    onToggleEchoCancellation: () -> Unit,
    onMicSyncOffsetChanged: (Int) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("audio_mixer_panel")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Multi-Channel Audio Mixer",
                    color = StudioTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "44.1 kHz Stereo AAC • Real-time Peak Meters",
                    color = StudioTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            StudioSecondaryButton(
                text = if (audioState.masterMuted) "Master Muted" else "Master ${(audioState.masterVolume * 100).roundToInt()}%",
                icon = if (audioState.masterMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                isSelected = !audioState.masterMuted,
                accentColor = if (audioState.masterMuted) StudioLiveRed else StudioSuccessGreen,
                onClick = onToggleMasterMute,
                testTag = "btn_master_mute"
            )
        }

        // Individual Channel Cards
        AudioChannelCard(
            channel = audioState.micChannel,
            accentColor = StudioAccentSky,
            onVolumeChanged = { onChannelVolumeChanged("mic", it) },
            onToggleMute = { onToggleChannelMute("mic") }
        )
        AudioChannelCard(
            channel = audioState.devicePlaybackChannel,
            accentColor = StudioSuccessGreen,
            onVolumeChanged = { onChannelVolumeChanged("device_playback", it) },
            onToggleMute = { onToggleChannelMute("device_playback") }
        )
        AudioChannelCard(
            channel = audioState.networkAudioChannel,
            accentColor = StudioWarningAmber,
            onVolumeChanged = { onChannelVolumeChanged("network_audio", it) },
            onToggleMute = { onToggleChannelMute("network_audio") }
        )

        // Master Bus & DSP Processing Card
        StudioCard(
            backgroundColor = StudioCardBg,
            borderColor = StudioBorder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Master Output Gain",
                    color = StudioTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${(audioState.masterVolume * 100).roundToInt()}%",
                    color = StudioAccentViolet,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = audioState.masterVolume,
                onValueChange = onMasterVolumeChanged,
                valueRange = 0f..1.25f,
                colors = SliderDefaults.colors(
                    thumbColor = StudioAccentViolet,
                    activeTrackColor = StudioAccentViolet
                ),
                modifier = Modifier.testTag("slider_master_volume")
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StudioSecondaryButton(
                    text = "Noise Gate",
                    isSelected = audioState.noiseSuppressionEnabled,
                    accentColor = StudioAccentSky,
                    onClick = onToggleNoiseSuppression,
                    modifier = Modifier.weight(1f),
                    testTag = "chip_noise_suppression"
                )
                StudioSecondaryButton(
                    text = "Echo Cancel",
                    isSelected = audioState.echoCancellationEnabled,
                    accentColor = StudioAccentSky,
                    onClick = onToggleEchoCancellation,
                    modifier = Modifier.weight(1f),
                    testTag = "chip_echo_cancel"
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "A/V Sync Offset: ${audioState.avSyncOffsetMs} ms",
                color = StudioTextSecondary,
                fontSize = 11.sp
            )
            Slider(
                value = audioState.avSyncOffsetMs.toFloat(),
                onValueChange = { onMicSyncOffsetChanged(it.roundToInt()) },
                valueRange = -250f..250f
            )
        }
    }
}

@Composable
private fun AudioChannelCard(
    channel: AudioChannelConfig,
    accentColor: Color,
    onVolumeChanged: (Float) -> Unit,
    onToggleMute: () -> Unit
) {
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
                    text = channel.name,
                    color = StudioTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (channel.muted) "Muted" else "${(channel.volume * 100).roundToInt()}% Gain • ${channel.dbFs.roundToInt()} dBFS",
                    color = if (channel.muted) StudioLiveRed else StudioTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            StudioIconButton(
                icon = if (channel.muted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (channel.muted) "Unmute ${channel.name}" else "Mute ${channel.name}",
                isActive = !channel.muted,
                activeColor = accentColor,
                tint = if (channel.muted) StudioLiveRed else accentColor,
                onClick = onToggleMute,
                testTag = "mute_channel_${channel.id}"
            )
        }

        Spacer(Modifier.height(6.dp))

        // Peak Meter Bar
        val meterFill = if (channel.muted) 0f else channel.peakLevel.coerceIn(0.03f, 1f)
        val meterColor = when {
            channel.muted -> StudioTextMuted
            meterFill > 0.85f -> StudioLiveRed
            meterFill > 0.65f -> StudioWarningAmber
            else -> StudioSuccessGreen
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(StudioMainBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(meterFill)
                    .fillMaxHeight()
                    .background(meterColor)
            )
        }

        Slider(
            value = channel.volume,
            onValueChange = onVolumeChanged,
            valueRange = 0f..1.25f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            ),
            modifier = Modifier.testTag("slider_channel_${channel.id}")
        )
    }
}

/**
 * Redesigned Settings Screen with 7 Expandable Sections (Section 9):
 * 1. Streaming
 * 2. Video Quality
 * 3. Audio
 * 4. Camera
 * 5. PC Connection
 * 6. Appearance
 * 7. Storage
 */
@Composable
fun StreamSettingsSheetContent(
    uiState: StudioUiState,
    telemetry: StreamEngineTelemetry,
    diagnostics: DeviceDiagnosticsState,
    audioState: AudioMixerState,
    cameraState: CameraHardwareState,
    onUpdateCredentials: ((StreamCredentials) -> StreamCredentials) -> Unit,
    onTestConnection: () -> Unit,
    onStartStream: () -> Unit,
    onStopStream: () -> Unit,
    onToggleNoiseSuppression: () -> Unit,
    onToggleEchoCancellation: () -> Unit,
    onMicSyncOffsetChanged: (Int) -> Unit,
    onUpdateCameraConfig: ((CameraSourceConfig) -> CameraSourceConfig) -> Unit,
    onToggleCenterSnap: () -> Unit,
    onToggleGridSnap: () -> Unit,
    onToggleSafeAreaGuides: () -> Unit,
    onSetKeepScreenAwake: (Boolean) -> Unit,
    onSetLockLandscape: (Boolean) -> Unit,
    onSaveSceneExplicitly: () -> Unit
) {
    val creds = uiState.credentials
    var showStreamKey by remember { mutableStateOf(false) }

    var expandStreaming by remember { mutableStateOf(true) }
    var expandVideoQuality by remember { mutableStateOf(true) }
    var expandAudio by remember { mutableStateOf(false) }
    var expandCamera by remember { mutableStateOf(false) }
    var expandAppearance by remember { mutableStateOf(false) }
    var expandStorage by remember { mutableStateOf(false) }

    val cameraLayer = uiState.activeScene.layers.find { it.type == SourceType.CAMERA }
    val cameraCfg = cameraLayer?.cameraConfig ?: CameraSourceConfig()

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("stream_settings_panel")
    ) {
        Text(
            text = "Studio Settings",
            color = StudioTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        // 1. STREAMING SECTION
        StudioExpandableSection(
            title = "Streaming",
            subtitle = "YouTube RTMPS URL, Stream Key & Connection Test",
            icon = Icons.Default.WifiTethering,
            expanded = expandStreaming,
            onToggle = { expandStreaming = !expandStreaming },
            badgeText = if (telemetry.isStreaming) "LIVE" else telemetry.status.badgeText,
            badgeColor = if (telemetry.isStreaming) StudioLiveRed else StudioAccentSky,
            testTag = "settings_section_streaming"
        ) {
            OutlinedTextField(
                value = creds.rtmpServerUrl,
                onValueChange = { url -> onUpdateCredentials { it.copy(rtmpServerUrl = url.trim()) } },
                singleLine = true,
                label = { Text("YouTube RTMPS Server URL", fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StudioAccentViolet,
                    unfocusedBorderColor = StudioBorder,
                    focusedTextColor = StudioTextPrimary,
                    unfocusedTextColor = StudioTextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_rtmp_url")
            )

            OutlinedTextField(
                value = creds.streamKey,
                onValueChange = { key -> onUpdateCredentials { it.copy(streamKey = key.trim()) } },
                singleLine = true,
                visualTransformation = if (showStreamKey) VisualTransformation.None else PasswordVisualTransformation(),
                label = { Text("Stream Key (Encrypted in Android Keystore)", fontSize = 12.sp) },
                trailingIcon = {
                    StudioIconButton(
                        icon = if (showStreamKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showStreamKey) "Hide Stream Key" else "Show Stream Key",
                        onClick = { showStreamKey = !showStreamKey },
                        testTag = "btn_toggle_stream_key_visibility"
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StudioAccentViolet,
                    unfocusedBorderColor = StudioBorder,
                    focusedTextColor = StudioTextPrimary,
                    unfocusedTextColor = StudioTextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_stream_key")
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StudioSecondaryButton(
                    text = "Test Connection",
                    onClick = onTestConnection,
                    modifier = Modifier.weight(1f),
                    testTag = "btn_test_connection"
                )
                if (!telemetry.isStreaming) {
                    StudioPrimaryButton(
                        text = "Start Streaming",
                        icon = Icons.Default.PlayArrow,
                        containerColor = StudioLiveRed,
                        onClick = onStartStream,
                        modifier = Modifier.weight(1f),
                        testTag = "btn_go_live_sheet"
                    )
                } else {
                    StudioDangerButton(
                        text = "Stop Streaming",
                        icon = Icons.Default.Stop,
                        onClick = onStopStream,
                        modifier = Modifier.weight(1f),
                        testTag = "btn_stop_live_sheet"
                    )
                }
            }

            Text(
                text = telemetry.statusDetail,
                color = if (telemetry.status == StreamConnectionStatus.ERROR) StudioLiveRed else StudioSuccessGreen,
                fontSize = 12.sp
            )

            BroadcastNetworkMonitorCard(
                telemetry = telemetry,
                diagnostics = diagnostics
            )
        }

        // 2. VIDEO QUALITY SECTION
        StudioExpandableSection(
            title = "Video Quality",
            subtitle = "${creds.resolutionPreset.label} • ${creds.videoBitrateKbps} kbps H.264",
            icon = Icons.Default.HighQuality,
            expanded = expandVideoQuality,
            onToggle = { expandVideoQuality = !expandVideoQuality },
            badgeText = "${creds.resolutionPreset.height}p${creds.resolutionPreset.fps}",
            badgeColor = StudioAccentViolet,
            testTag = "settings_section_video_quality"
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StreamQualityPreset.entries.forEach { preset ->
                    val shortLabel = "${preset.height}p${preset.fps}"
                    StudioSecondaryButton(
                        text = shortLabel,
                        isSelected = creds.resolutionPreset == preset,
                        accentColor = StudioAccentViolet,
                        onClick = {
                            onUpdateCredentials {
                                it.copy(
                                    resolutionPreset = preset,
                                    videoBitrateKbps = preset.recommendedBitrateKbps
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "preset_${preset.name.lowercase()}"
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Encoder Bitrate", color = StudioTextSecondary, fontSize = 12.sp)
                Text(
                    text = "${creds.videoBitrateKbps} kbps (${String.format("%.1f", creds.videoBitrateKbps / 1000f)} Mbps)",
                    color = StudioAccentSky,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Slider(
                value = creds.videoBitrateKbps.toFloat(),
                onValueChange = { b ->
                    onUpdateCredentials { it.copy(videoBitrateKbps = (b.roundToInt() / 100) * 100) }
                },
                valueRange = 2500f..12000f,
                colors = SliderDefaults.colors(thumbColor = StudioAccentViolet, activeTrackColor = StudioAccentViolet),
                modifier = Modifier.testTag("slider_video_bitrate")
            )

            SettingsSwitchRow(
                title = "Auto-Reconnect on Network Drop",
                checked = creds.autoReconnect,
                onCheckedChange = { v -> onUpdateCredentials { it.copy(autoReconnect = v) } }
            )
        }

        // 3. AUDIO SECTION
        StudioExpandableSection(
            title = "Audio",
            subtitle = "44.1 kHz Stereo AAC (${creds.audioBitrateKbps} kbps) • Noise & Sync",
            icon = Icons.Default.GraphicEq,
            expanded = expandAudio,
            onToggle = { expandAudio = !expandAudio },
            badgeText = "${creds.audioBitrateKbps}k AAC",
            badgeColor = StudioSuccessGreen,
            testTag = "settings_section_audio"
        ) {
            SettingsSwitchRow(
                title = "Microphone Noise Suppression",
                checked = audioState.noiseSuppressionEnabled,
                onCheckedChange = { onToggleNoiseSuppression() }
            )
            SettingsSwitchRow(
                title = "Acoustic Echo Cancellation",
                checked = audioState.echoCancellationEnabled,
                onCheckedChange = { onToggleEchoCancellation() }
            )
            Text(
                text = "A/V Sync Offset: ${audioState.avSyncOffsetMs} ms",
                color = StudioTextSecondary,
                fontSize = 12.sp
            )
            Slider(
                value = audioState.avSyncOffsetMs.toFloat(),
                onValueChange = { onMicSyncOffsetChanged(it.roundToInt()) },
                valueRange = -250f..250f
            )
        }

        // 4. CAMERA SECTION
        StudioExpandableSection(
            title = "Camera",
            subtitle = "${cameraCfg.facing.displayName} • ${cameraCfg.targetWidth}×${cameraCfg.targetHeight} @ ${cameraCfg.targetFps} FPS",
            icon = Icons.Default.CameraFront,
            expanded = expandCamera,
            onToggle = { expandCamera = !expandCamera },
            badgeText = cameraCfg.facing.displayName,
            badgeColor = StudioAccentSky,
            testTag = "settings_section_camera"
        ) {
            CameraHardwareCard(
                cfg = cameraCfg,
                cameraState = cameraState,
                onUpdateCameraConfig = onUpdateCameraConfig
            )
        }

        // 5. APPEARANCE & EDITOR GUIDES SECTION
        StudioExpandableSection(
            title = "Appearance",
            subtitle = "Dark Studio Theme • Alignment Guides & Screen Lock",
            icon = Icons.Default.Palette,
            expanded = expandAppearance,
            onToggle = { expandAppearance = !expandAppearance },
            badgeText = "Dark Pro",
            badgeColor = StudioAccentViolet,
            testTag = "settings_section_appearance"
        ) {
            SettingsSwitchRow(
                title = "Center & Edge Snap Guides",
                checked = uiState.centerSnapEnabled,
                onCheckedChange = { onToggleCenterSnap() }
            )
            SettingsSwitchRow(
                title = "5% Grid Snap",
                checked = uiState.gridSnapEnabled,
                onCheckedChange = { onToggleGridSnap() }
            )
            SettingsSwitchRow(
                title = "16:9 Broadcast Safe-Area Frame",
                checked = uiState.safeAreaGuidesEnabled,
                onCheckedChange = { onToggleSafeAreaGuides() }
            )
            SettingsSwitchRow(
                title = "Keep Screen Awake in Studio",
                checked = uiState.keepScreenAwake,
                onCheckedChange = onSetKeepScreenAwake
            )
            SettingsSwitchRow(
                title = "Lock Landscape Orientation",
                checked = uiState.lockLandscapeOrientation,
                onCheckedChange = onSetLockLandscape
            )
        }

        // 7. STORAGE & DIAGNOSTICS SECTION
        StudioExpandableSection(
            title = "Storage",
            subtitle = "${uiState.scenes.size} Saved Scenes • Battery ${diagnostics.batteryPercent}% (${diagnostics.batteryTempCelsius}°C)",
            icon = Icons.Default.Storage,
            expanded = expandStorage,
            onToggle = { expandStorage = !expandStorage },
            badgeText = "CPU ${diagnostics.cpuUsagePercent}%",
            badgeColor = StudioAccentSky,
            testTag = "settings_section_storage"
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Persist Active Scene & Overlays",
                        color = StudioTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Saves layout, crop, borders & paired PC host",
                        color = StudioTextSecondary,
                        fontSize = 11.sp
                    )
                }
                StudioPrimaryButton(
                    text = "Save Now",
                    icon = Icons.Default.Save,
                    onClick = onSaveSceneExplicitly
                )
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Device Thermal / Battery", color = StudioTextSecondary, fontSize = 12.sp)
                Text(
                    text = "${diagnostics.batteryPercent}% • ${diagnostics.batteryTempCelsius}°C (${diagnostics.thermalStatusLabel})",
                    color = StudioTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Network & CPU Health", color = StudioTextSecondary, fontSize = 12.sp)
                Text(
                    text = "${diagnostics.networkTypeLabel} • CPU ${diagnostics.cpuUsagePercent}% (${diagnostics.healthGrade.label})",
                    color = StudioTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Text(
            text = title,
            color = StudioTextPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = StudioTextPrimary,
                checkedTrackColor = StudioAccentViolet,
                uncheckedThumbColor = StudioTextSecondary,
                uncheckedTrackColor = StudioElevatedBg
            )
        )
    }
}
