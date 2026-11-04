package com.example.ui

import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compositor.SceneVideoCompositor
import com.example.model.SceneLayer
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
import com.example.viewmodel.ResizeHandle
import com.example.viewmodel.StudioUiState
import kotlin.math.hypot

@Composable
fun SceneCanvasEditor(
    uiState: StudioUiState,
    compositor: SceneVideoCompositor,
    isStreaming: Boolean = false,
    onSelectLayer: (String?) -> Unit,
    onMoveLayer: (String, Float, Float) -> Unit,
    onResizeLayer: (String, ResizeHandle, Float, Float) -> Unit,
    onGestureFinished: () -> Unit,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onToggleScaleMode: (String) -> Unit = {},
    onOpenLayerInspector: (String) -> Unit = {},
    onToggleLockLayer: (String) -> Unit = {},
    onDeleteLayer: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeScene = uiState.activeScene
    val aspect = (activeScene.canvasWidth.toFloat() / activeScene.canvasHeight.toFloat())
        .coerceIn(0.4f, 2.5f)
    val selectedLayer = uiState.selectedLayer

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // Preview Header Row (outside the encoded 16:9 canvas so the stream output stays 100% clean)
        if (!uiState.isProgramMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                // Status indicator: LIVE or PREVIEW + resolution
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isStreaming) StudioLiveRed else StudioElevatedBg)
                            .border(
                                width = 1.dp,
                                color = if (isStreaming) StudioLiveRed else StudioBorder,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isStreaming) Color.White else StudioSuccessGreen)
                        )
                        Text(
                            text = if (isStreaming) "LIVE" else "PREVIEW",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Text(
                        text = "${activeScene.canvasWidth}×${activeScene.canvasHeight} • 16:9",
                        color = StudioTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Selected layer quick pill
                if (selectedLayer != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(StudioAccentViolet.copy(alpha = 0.16f))
                            .border(1.dp, StudioAccentViolet.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${selectedLayer.name} (${selectedLayer.widthPercent}%×${selectedLayer.heightPercent}%)",
                            color = StudioTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = "Tap source to edit",
                        color = StudioTextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Main 16:9 Live Scene Preview Container
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("scene_canvas_container")
        ) {
            val containerShape = RoundedCornerShape(if (uiState.isProgramMode) 0.dp else 12.dp)
            val borderColor = when {
                uiState.isProgramMode -> Color.Transparent
                isStreaming -> StudioLiveRed.copy(alpha = 0.85f)
                selectedLayer != null -> StudioAccentViolet.copy(alpha = 0.65f)
                else -> StudioBorder
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect, matchHeightConstraintsFirst = maxWidth > maxHeight * aspect)
                    .clip(containerShape)
                    .background(Color.Black)
                    .border(
                        width = if (uiState.isProgramMode) 0.dp else 1.5.dp,
                        color = borderColor,
                        shape = containerShape
                    )
                    .pointerInput(
                        uiState.isProgramMode,
                        activeScene.id,
                        activeScene.layers,
                        uiState.selectedLayerId
                    ) {
                        if (uiState.isProgramMode) return@pointerInput

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val canvasW = size.width.toFloat().coerceAtLeast(1f)
                            val canvasH = size.height.toFloat().coerceAtLeast(1f)

                            val normDownX = down.position.x / canvasW
                            val normDownY = down.position.y / canvasH

                            val currentSelected = uiState.selectedLayer
                            var activeResizeHandle: ResizeHandle? = null
                            var targetLayerId: String? = null

                            if (currentSelected != null && currentSelected.visible && !currentSelected.locked) {
                                activeResizeHandle = hitTestResizeHandle(
                                    layer = currentSelected,
                                    touchPx = down.position,
                                    canvasW = canvasW,
                                    canvasH = canvasH
                                )
                                if (activeResizeHandle != null) {
                                    targetLayerId = currentSelected.id
                                }
                            }

                            if (activeResizeHandle == null) {
                                val hitLayer = activeScene.sortedLayers()
                                    .asReversed()
                                    .firstOrNull { layer ->
                                        layer.visible &&
                                            normDownX >= layer.x &&
                                            normDownX <= (layer.x + layer.width) &&
                                            normDownY >= layer.y &&
                                            normDownY <= (layer.y + layer.height)
                                    }
                                targetLayerId = hitLayer?.id
                                onSelectLayer(targetLayerId)
                            }

                            var dragged = false
                            do {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }

                                if (pressedCount >= 2 && targetLayerId != null) {
                                    // Pinch-to-scale & two-finger pan
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    if (zoom != 1f && zoom > 0.5f && zoom < 2.0f) {
                                        val deltaScale = (zoom - 1f) * 0.65f
                                        onResizeLayer(
                                            targetLayerId,
                                            ResizeHandle.BOTTOM_RIGHT,
                                            deltaScale,
                                            deltaScale
                                        )
                                        dragged = true
                                    }
                                    if (pan != Offset.Zero) {
                                        onMoveLayer(
                                            targetLayerId,
                                            pan.x / canvasW,
                                            pan.y / canvasH
                                        )
                                        dragged = true
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } else if (pressedCount == 1 && targetLayerId != null) {
                                    val change = event.changes.firstOrNull()
                                    if (change != null && change.positionChanged()) {
                                        val deltaPx = change.position - change.previousPosition
                                        val dNormX = deltaPx.x / canvasW
                                        val dNormY = deltaPx.y / canvasH

                                        if (activeResizeHandle != null) {
                                            onResizeLayer(targetLayerId, activeResizeHandle, dNormX, dNormY)
                                        } else {
                                            onMoveLayer(targetLayerId, dNormX, dNormY)
                                        }
                                        dragged = true
                                        change.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            if (dragged) {
                                onGestureFinished()
                            }
                        }
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("scene_live_canvas")
                ) {
                    // Read frameTick so Compose invalidates at 30fps for live CameraX + Screen frames
                    val tick = uiState.frameTick
                    if (tick >= 0L) {
                        compositor.renderProgramFrame(
                            canvas = drawContext.canvas.nativeCanvas,
                            activeScene = activeScene,
                            outgoingScene = uiState.outgoingScene,
                            transitionProgress = uiState.transitionProgress,
                            transitionType = uiState.transitionType,
                            targetWidth = size.width.toInt().coerceAtLeast(1),
                            targetHeight = size.height.toInt().coerceAtLeast(1)
                        )
                    }

                    // Editor-only selection handles & alignment guides (never included in final stream)
                    if (!uiState.isProgramMode) {
                        if (uiState.safeAreaGuidesEnabled) {
                            val marginX = size.width * 0.05f
                            val marginY = size.height * 0.05f
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.14f),
                                topLeft = Offset(marginX, marginY),
                                size = Size(size.width - marginX * 2f, size.height - marginY * 2f),
                                cornerRadius = CornerRadius(6.dp.toPx()),
                                style = Stroke(
                                    width = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                            )
                        }

                        uiState.activeSnapVerticalX?.let { snapX ->
                            val px = snapX * size.width
                            drawLine(
                                color = StudioAccentSky,
                                start = Offset(px, 0f),
                                end = Offset(px, size.height),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                            )
                        }

                        uiState.activeSnapHorizontalY?.let { snapY ->
                            val py = snapY * size.height
                            drawLine(
                                color = StudioAccentSky,
                                start = Offset(0f, py),
                                end = Offset(size.width, py),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                            )
                        }

                        selectedLayer?.takeIf { it.visible }?.let { sel ->
                            val rect = RectF(
                                sel.x * size.width,
                                sel.y * size.height,
                                (sel.x + sel.width) * size.width,
                                (sel.y + sel.height) * size.height
                            )
                            val selectionColor = if (sel.locked) Color(0xFFF59E0B) else StudioAccentViolet

                            drawRect(
                                color = selectionColor,
                                topLeft = Offset(rect.left, rect.top),
                                size = Size(rect.width(), rect.height()),
                                style = Stroke(width = 2.dp.toPx())
                            )

                            if (!sel.locked) {
                                val handleRadius = 5.5.dp.toPx()
                                val handlePoints = listOf(
                                    Offset(rect.left, rect.top),
                                    Offset(rect.right, rect.top),
                                    Offset(rect.left, rect.bottom),
                                    Offset(rect.right, rect.bottom),
                                    Offset(rect.centerX(), rect.top),
                                    Offset(rect.centerX(), rect.bottom),
                                    Offset(rect.left, rect.centerY()),
                                    Offset(rect.right, rect.centerY())
                                )
                                handlePoints.forEach { pt ->
                                    drawCircle(
                                        color = StudioMainBg,
                                        radius = handleRadius + 2.dp.toPx(),
                                        center = pt
                                    )
                                    drawCircle(
                                        color = StudioAccentSky,
                                        radius = handleRadius,
                                        center = pt
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Contextual Floating Toolbar (Section 5: only displayed when a source is selected in Edit Mode)
        AnimatedVisibility(
            visible = !uiState.isProgramMode && selectedLayer != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (selectedLayer != null) {
                ContextualSourceFloatingToolbar(
                    selectedLayer = selectedLayer,
                    canUndo = uiState.canUndo,
                    canRedo = uiState.canRedo,
                    onUndo = onUndo,
                    onRedo = onRedo,
                    onToggleScaleMode = { onToggleScaleMode(selectedLayer.id) },
                    onOpenCropOrSettings = { onOpenLayerInspector(selectedLayer.id) },
                    onToggleLock = { onToggleLockLayer(selectedLayer.id) },
                    onDelete = { onDeleteLayer(selectedLayer.id) },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ContextualSourceFloatingToolbar(
    selectedLayer: SceneLayer,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleScaleMode: () -> Unit,
    onOpenCropOrSettings: () -> Unit,
    onToggleLock: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = StudioCardBg,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("contextual_source_toolbar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            // Undo & Redo
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("canvas_undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) StudioTextPrimary else StudioTextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("canvas_redo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) StudioTextPrimary else StudioTextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(22.dp)
                    .background(StudioBorder)
            )

            // Fit / Fill / Stretch toggle button
            Surface(
                onClick = onToggleScaleMode,
                color = StudioElevatedBg,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("canvas_scale_mode_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = null,
                        tint = StudioAccentSky,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = selectedLayer.scaleMode.displayName,
                        color = StudioTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Crop & Inspector settings
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenCropOrSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("canvas_crop_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Crop,
                        contentDescription = "Crop Source",
                        tint = StudioTextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("canvas_lock_button")
                ) {
                    Icon(
                        imageVector = if (selectedLayer.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (selectedLayer.locked) "Unlock Source" else "Lock Source",
                        tint = if (selectedLayer.locked) Color(0xFFF59E0B) else StudioTextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = onOpenCropOrSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("canvas_inspector_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Source Properties",
                        tint = StudioAccentViolet,
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("canvas_delete_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Source",
                        tint = StudioLiveRed,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

private fun hitTestResizeHandle(
    layer: SceneLayer,
    touchPx: Offset,
    canvasW: Float,
    canvasH: Float
): ResizeHandle? {
    val left = layer.x * canvasW
    val top = layer.y * canvasH
    val right = (layer.x + layer.width) * canvasW
    val bottom = (layer.y + layer.height) * canvasH
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f

    val hitRadius = 34f
    val candidates = listOf(
        ResizeHandle.TOP_LEFT to Offset(left, top),
        ResizeHandle.TOP_RIGHT to Offset(right, top),
        ResizeHandle.BOTTOM_LEFT to Offset(left, bottom),
        ResizeHandle.BOTTOM_RIGHT to Offset(right, bottom),
        ResizeHandle.TOP to Offset(cx, top),
        ResizeHandle.BOTTOM to Offset(cx, bottom),
        ResizeHandle.LEFT to Offset(left, cy),
        ResizeHandle.RIGHT to Offset(right, cy)
    )

    return candidates.firstOrNull { (_, pt) ->
        hypot(touchPx.x - pt.x, touchPx.y - pt.y) <= hitRadius
    }?.first
}
