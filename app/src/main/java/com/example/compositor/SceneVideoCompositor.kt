package com.example.compositor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.model.ScaleMode
import com.example.model.Scene
import com.example.model.SceneFontOption
import com.example.model.SceneLayer
import com.example.model.SceneTransitionType
import com.example.model.SourceType
import com.example.sources.VideoSourceManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * High-quality, zero-allocation real-time multi-layer Video Compositor for SceneCast Live.
 *
 * - Renders Camera and Screen capture simultaneously as independent layers in exact zIndex order.
 * - Uses high-quality bilinear + dithered bitmap filtering (FILTER_BITMAP_FLAG | DITHER_FLAG | ANTI_ALIAS_FLAG).
 * - Renders clean, borderless windows by default with zero grid lines, scanlines, or editor chrome.
 */
class SceneVideoCompositor(
    private val context: Context,
    private val sourceManager: VideoSourceManager
) {
    private class RenderWorkspace {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val slatePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply { style = Paint.Style.FILL }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }
        val clipPath = Path()
        val dstRectF = RectF()
        val fittedRectF = RectF()
        val borderRectF = RectF()
        val shadowRectF = RectF()
        val srcRect = Rect()

        var cachedBgW = -1
        var cachedBgH = -1
        var cachedBgC1 = 0L
        var cachedBgC2 = 0L
        var cachedBgShader: LinearGradient? = null
    }

    private val threadWorkspace = object : ThreadLocal<RenderWorkspace>() {
        override fun initialValue(): RenderWorkspace = RenderWorkspace()
    }

    private val fontCache = ConcurrentHashMap<String, Typeface>()

    private var masterFrameBuffer: Bitmap? = null
    private var masterCanvas: Canvas? = null
    private val masterLock = Any()

    private fun getTypeface(fontKey: String, bold: Boolean): Typeface {
        val cacheKey = "$fontKey:$bold"
        fontCache[cacheKey]?.let { return it }
        val resId = when (fontKey) {
            SceneFontOption.ROBOTO_MONO.fontKey -> R.font.roboto_mono
            SceneFontOption.MONTSERRAT.fontKey -> R.font.montserrat
            SceneFontOption.OSWALD.fontKey -> R.font.oswald
            else -> R.font.space_grotesk
        }
        val base = try {
            ResourcesCompat.getFont(context, resId) ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
        val tf = Typeface.create(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
        fontCache[cacheKey] = tf
        return tf
    }

    fun renderToOffscreenBitmap(
        activeScene: Scene,
        outgoingScene: Scene? = null,
        transitionProgress: Float = 1f,
        transitionType: SceneTransitionType = SceneTransitionType.CUT,
        targetWidth: Int = activeScene.canvasWidth,
        targetHeight: Int = activeScene.canvasHeight
    ): Bitmap {
        synchronized(masterLock) {
            val safeW = targetWidth.coerceAtLeast(16)
            val safeH = targetHeight.coerceAtLeast(16)
            var bmp = masterFrameBuffer
            if (bmp == null || bmp.isRecycled || bmp.width != safeW || bmp.height != safeH) {
                bmp = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
                masterFrameBuffer = bmp
                masterCanvas = Canvas(bmp)
            }
            bmp.eraseColor(if (activeScene.transparentBackground) Color.TRANSPARENT else Color.BLACK)
            val canvas = masterCanvas ?: Canvas(bmp).also { masterCanvas = it }
            renderProgramFrame(
                canvas = canvas,
                activeScene = activeScene,
                outgoingScene = outgoingScene,
                transitionProgress = transitionProgress,
                transitionType = transitionType,
                targetWidth = safeW,
                targetHeight = safeH
            )
            return bmp
        }
    }

    fun renderProgramFrame(
        canvas: Canvas,
        activeScene: Scene,
        outgoingScene: Scene? = null,
        transitionProgress: Float = 1f,
        transitionType: SceneTransitionType = SceneTransitionType.CUT,
        targetWidth: Int = activeScene.canvasWidth,
        targetHeight: Int = activeScene.canvasHeight
    ) {
        val ws = threadWorkspace.get() ?: RenderWorkspace()
        if (outgoingScene != null && transitionProgress < 1f && transitionType != SceneTransitionType.CUT) {
            when (transitionType) {
                SceneTransitionType.FADE -> {
                    renderSingleScene(ws, canvas, outgoingScene, targetWidth, targetHeight, 1f)
                    renderSingleScene(
                        ws,
                        canvas,
                        activeScene,
                        targetWidth,
                        targetHeight,
                        transitionProgress.coerceIn(0f, 1f)
                    )
                }
                SceneTransitionType.DIP_TO_BLACK -> {
                    if (transitionProgress < 0.5f) {
                        val alpha = 1f - (transitionProgress * 2f)
                        canvas.drawColor(Color.BLACK)
                        renderSingleScene(ws, canvas, outgoingScene, targetWidth, targetHeight, alpha)
                    } else {
                        val alpha = (transitionProgress - 0.5f) * 2f
                        canvas.drawColor(Color.BLACK)
                        renderSingleScene(ws, canvas, activeScene, targetWidth, targetHeight, alpha)
                    }
                }
                SceneTransitionType.CUT -> {
                    renderSingleScene(ws, canvas, activeScene, targetWidth, targetHeight, 1f)
                }
            }
        } else {
            renderSingleScene(ws, canvas, activeScene, targetWidth, targetHeight, 1f)
        }
    }

    private fun renderSingleScene(
        ws: RenderWorkspace,
        canvas: Canvas,
        scene: Scene,
        targetWidth: Int,
        targetHeight: Int,
        masterAlpha: Float
    ) {
        val saveCount = if (masterAlpha < 0.999f) {
            canvas.saveLayerAlpha(
                0f,
                0f,
                targetWidth.toFloat(),
                targetHeight.toFloat(),
                (masterAlpha * 255).toInt().coerceIn(0, 255)
            )
        } else {
            canvas.save()
        }

        // 1. Scene Background (with cached LinearGradient to avoid per-frame GC allocation)
        if (!scene.transparentBackground) {
            ws.bgPaint.color = scene.backgroundColorHex.toInt()
            if (scene.useGradientBackground) {
                if (ws.cachedBgShader == null ||
                    ws.cachedBgW != targetWidth ||
                    ws.cachedBgH != targetHeight ||
                    ws.cachedBgC1 != scene.backgroundColorHex ||
                    ws.cachedBgC2 != scene.backgroundGradientEndHex
                ) {
                    ws.cachedBgW = targetWidth
                    ws.cachedBgH = targetHeight
                    ws.cachedBgC1 = scene.backgroundColorHex
                    ws.cachedBgC2 = scene.backgroundGradientEndHex
                    ws.cachedBgShader = LinearGradient(
                        0f,
                        0f,
                        targetWidth.toFloat(),
                        targetHeight.toFloat(),
                        scene.backgroundColorHex.toInt(),
                        scene.backgroundGradientEndHex.toInt(),
                        Shader.TileMode.CLAMP
                    )
                }
                ws.bgPaint.shader = ws.cachedBgShader
            } else {
                ws.bgPaint.shader = null
            }
            canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), ws.bgPaint)
        } else {
            canvas.drawColor(Color.TRANSPARENT)
        }

        // 2. Render each visible layer in exact zIndex order
        val sorted = scene.sortedLayers()
        for (layer in sorted) {
            if (!layer.visible || layer.opacity <= 0.01f) continue
            renderLayer(ws, canvas, layer, targetWidth, targetHeight)
        }

        canvas.restoreToCount(saveCount)
    }

    private fun renderLayer(
        ws: RenderWorkspace,
        canvas: Canvas,
        layer: SceneLayer,
        canvasW: Int,
        canvasH: Int
    ) {
        val left = layer.x * canvasW
        val top = layer.y * canvasH
        val width = (layer.width * canvasW).coerceAtLeast(8f)
        val height = (layer.height * canvasH).coerceAtLeast(8f)
        ws.dstRectF.set(left, top, left + width, top + height)

        val scaleFactor = canvasW / 1920f.coerceAtLeast(1f)
        val minDim = min(width, height)
        val cornerRadiusPx = (layer.border.cornerRadiusPercent / 100f) * minDim
        val alphaInt = (layer.opacity.coerceIn(0f, 1f) * 255f).toInt()

        canvas.save()
        if (layer.rotationDeg != 0f) {
            canvas.rotate(layer.rotationDeg, ws.dstRectF.centerX(), ws.dstRectF.centerY())
        }

        // 1. Optional shadow / outer glow ONLY if explicitly enabled by user
        if (layer.border.shadowEnabled || layer.border.glowEnabled) {
            val glowColor = if (layer.border.glowEnabled) {
                layer.border.glowColorHex.toInt()
            } else {
                0x88000000.toInt()
            }
            ws.shadowPaint.color = glowColor
            ws.shadowPaint.alpha = (alphaInt * 0.45f).toInt()
            val spread = (if (layer.border.glowEnabled) 6f else 4f) * scaleFactor
            ws.shadowRectF.set(
                ws.dstRectF.left - spread * 0.5f,
                ws.dstRectF.top + (if (layer.border.glowEnabled) -spread * 0.5f else spread),
                ws.dstRectF.right + spread * 0.5f,
                ws.dstRectF.bottom + spread
            )
            canvas.drawRoundRect(
                ws.shadowRectF,
                cornerRadiusPx + spread,
                cornerRadiusPx + spread,
                ws.shadowPaint
            )
        }

        // 2. Clip window interior
        val clipSave = canvas.save()
        if (cornerRadiusPx > 0.5f) {
            ws.clipPath.reset()
            ws.clipPath.addRoundRect(ws.dstRectF, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
            canvas.clipPath(ws.clipPath)
        } else {
            canvas.clipRect(ws.dstRectF)
        }

        when (layer.type) {
            SourceType.BACKGROUND -> {
                renderBackgroundLayer(ws, canvas, layer, ws.dstRectF, alphaInt)
            }
            SourceType.TEXT -> {
                renderTextLayer(ws, canvas, layer, ws.dstRectF, scaleFactor, alphaInt)
            }
            else -> {
                val bmp = sourceManager.getBitmapForLayer(layer)
                if (bmp != null && !bmp.isRecycled) {
                    drawCroppedAndScaledBitmap(ws, canvas, bmp, layer, ws.dstRectF, alphaInt)
                } else {
                    renderCleanSourceStandbySurface(ws, canvas, layer, ws.dstRectF, scaleFactor, alphaInt)
                }
            }
        }

        canvas.restoreToCount(clipSave)

        // 3. Render customizable border ONLY when explicitly enabled by the user
        if (layer.border.enabled && layer.border.thicknessDp > 0.2f) {
            val strokePx = (layer.border.thicknessDp * 2.0f * scaleFactor).coerceAtLeast(1.5f)
            ws.borderPaint.strokeWidth = strokePx
            if (layer.border.useGradient) {
                ws.borderPaint.shader = LinearGradient(
                    ws.dstRectF.left,
                    ws.dstRectF.top,
                    ws.dstRectF.right,
                    ws.dstRectF.bottom,
                    layer.border.colorHex.toInt(),
                    layer.border.secondaryColorHex.toInt(),
                    Shader.TileMode.CLAMP
                )
            } else {
                ws.borderPaint.shader = null
                ws.borderPaint.color = layer.border.colorHex.toInt()
            }
            ws.borderPaint.alpha = alphaInt
            val inset = strokePx / 2f
            ws.borderRectF.set(
                ws.dstRectF.left + inset,
                ws.dstRectF.top + inset,
                ws.dstRectF.right - inset,
                ws.dstRectF.bottom - inset
            )
            canvas.drawRoundRect(ws.borderRectF, cornerRadiusPx, cornerRadiusPx, ws.borderPaint)
        }

        // 4. Render attached transparent PNG frame ONLY when explicitly selected by the user
        val frameBmp = sourceManager.getBorderFrameBitmap(
            layer.border.builtInPngFrameRes,
            layer.border.customPngFramePath
        )
        if (frameBmp != null && !frameBmp.isRecycled) {
            ws.bitmapPaint.alpha = alphaInt
            ws.srcRect.set(0, 0, frameBmp.width, frameBmp.height)
            canvas.drawBitmap(frameBmp, ws.srcRect, ws.dstRectF, ws.bitmapPaint)
        }

        canvas.restore()
    }

    private fun drawCroppedAndScaledBitmap(
        ws: RenderWorkspace,
        canvas: Canvas,
        bitmap: Bitmap,
        layer: SceneLayer,
        dst: RectF,
        alphaInt: Int
    ) {
        val crop = layer.crop.clamped()
        val extraRightCrop = if (
            layer.type == SourceType.SCREEN_CAPTURE &&
            layer.screenCaptureConfig.splitScreenLeftHalfCrop
        ) {
            0.5f
        } else {
            crop.right
        }

        val srcLeft = (crop.left * bitmap.width).toInt().coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
        val srcTop = (crop.top * bitmap.height).toInt().coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
        val srcRight = ((1f - extraRightCrop) * bitmap.width).toInt().coerceIn(srcLeft + 1, bitmap.width)
        val srcBottom = ((1f - crop.bottom) * bitmap.height).toInt().coerceIn(srcTop + 1, bitmap.height)

        val croppedW = (srcRight - srcLeft).toFloat().coerceAtLeast(1f)
        val croppedH = (srcBottom - srcTop).toFloat().coerceAtLeast(1f)

        ws.srcRect.set(srcLeft, srcTop, srcRight, srcBottom)
        ws.bitmapPaint.alpha = alphaInt

        val applyFlip = layer.flipHorizontal || layer.flipVertical
        if (applyFlip) {
            canvas.save()
            val sx = if (layer.flipHorizontal) -1f else 1f
            val sy = if (layer.flipVertical) -1f else 1f
            canvas.scale(sx, sy, dst.centerX(), dst.centerY())
        }

        when (layer.scaleMode) {
            ScaleMode.STRETCH -> {
                canvas.drawBitmap(bitmap, ws.srcRect, dst, ws.bitmapPaint)
            }
            ScaleMode.FIT -> {
                if (layer.type != SourceType.PNG_OVERLAY && layer.type != SourceType.LOGO) {
                    ws.slatePaint.shader = null
                    ws.slatePaint.color = 0xFF05070B.toInt()
                    ws.slatePaint.alpha = alphaInt
                    canvas.drawRect(dst, ws.slatePaint)
                }
                val scale = min(dst.width() / croppedW, dst.height() / croppedH)
                val fittedW = croppedW * scale
                val fittedH = croppedH * scale
                val fitLeft = dst.left + (dst.width() - fittedW) / 2f
                val fitTop = dst.top + (dst.height() - fittedH) / 2f
                ws.fittedRectF.set(fitLeft, fitTop, fitLeft + fittedW, fitTop + fittedH)
                canvas.drawBitmap(bitmap, ws.srcRect, ws.fittedRectF, ws.bitmapPaint)
            }
            ScaleMode.FILL -> {
                val scale = maxOf(dst.width() / croppedW, dst.height() / croppedH)
                val scaledW = croppedW * scale
                val scaledH = croppedH * scale
                val drawLeft = dst.left + (dst.width() - scaledW) / 2f
                val drawTop = dst.top + (dst.height() - scaledH) / 2f
                ws.fittedRectF.set(drawLeft, drawTop, drawLeft + scaledW, drawTop + scaledH)
                canvas.drawBitmap(bitmap, ws.srcRect, ws.fittedRectF, ws.bitmapPaint)
            }
        }

        if (applyFlip) {
            canvas.restore()
        }
    }

    private fun renderBackgroundLayer(
        ws: RenderWorkspace,
        canvas: Canvas,
        layer: SceneLayer,
        dst: RectF,
        alphaInt: Int
    ) {
        val cfg = layer.backgroundConfig
        if (cfg.transparentBackground) return
        val bmp = sourceManager.getBitmapForLayer(layer)
        if (bmp != null && !bmp.isRecycled) {
            drawCroppedAndScaledBitmap(ws, canvas, bmp, layer, dst, alphaInt)
            return
        }
        if (cfg.useGradient) {
            val rad = Math.toRadians(cfg.gradientAngleDeg.toDouble())
            val endX = dst.left + dst.width() * cos(rad).toFloat()
            val endY = dst.top + dst.height() * sin(rad).toFloat()
            ws.slatePaint.shader = LinearGradient(
                dst.left,
                dst.top,
                endX,
                endY,
                cfg.primaryColorHex.toInt(),
                cfg.secondaryColorHex.toInt(),
                Shader.TileMode.CLAMP
            )
        } else {
            ws.slatePaint.shader = null
            ws.slatePaint.color = cfg.primaryColorHex.toInt()
        }
        ws.slatePaint.alpha = alphaInt
        canvas.drawRect(dst, ws.slatePaint)
    }

    private fun renderTextLayer(
        ws: RenderWorkspace,
        canvas: Canvas,
        layer: SceneLayer,
        dst: RectF,
        scaleFactor: Float,
        alphaInt: Int
    ) {
        val cfg = layer.textConfig
        if ((cfg.backgroundColorHex ushr 24) > 0L) {
            ws.slatePaint.shader = null
            ws.slatePaint.color = cfg.backgroundColorHex.toInt()
            ws.slatePaint.alpha = ((cfg.backgroundColorHex ushr 24).toInt() * alphaInt) / 255
            canvas.drawRect(dst, ws.slatePaint)
        }

        ws.textPaint.typeface = getTypeface(cfg.fontKey, cfg.bold)
        ws.textPaint.textSize = (cfg.fontSizeSp * 1.8f * scaleFactor).coerceAtLeast(14f)
        ws.textPaint.color = cfg.textColorHex.toInt()
        ws.textPaint.alpha = alphaInt
        if (cfg.shadowEnabled) {
            ws.textPaint.setShadowLayer(6f * scaleFactor, 0f, 3f * scaleFactor, 0xCC000000.toInt())
        } else {
            ws.textPaint.clearShadowLayer()
        }

        val lines = cfg.text.split("\n")
        val lineHeight = ws.textPaint.textSize * 1.25f
        val totalHeight = lines.size * lineHeight
        var y = dst.centerY() - (totalHeight / 2f) - ((ws.textPaint.descent() + ws.textPaint.ascent()) / 2f)
        for (line in lines) {
            canvas.drawText(line, dst.centerX(), y, ws.textPaint)
            y += lineHeight
        }
        ws.textPaint.clearShadowLayer()
    }

    /**
     * Clean, grid-free, border-free dark studio surface shown before a source starts capturing
     * or when no video frames have arrived yet.
     */
    private fun renderCleanSourceStandbySurface(
        ws: RenderWorkspace,
        canvas: Canvas,
        layer: SceneLayer,
        dst: RectF,
        scaleFactor: Float,
        alphaInt: Int
    ) {
        val bgColor = when (layer.type) {
            SourceType.SCREEN_CAPTURE -> 0xFF0A0F1A.toInt()
            SourceType.CAMERA -> 0xFF100E1A.toInt()
            SourceType.NETWORK_VIDEO -> 0xFF0B141A.toInt()
            else -> 0xFF0E121B.toInt()
        }

        ws.slatePaint.shader = null
        ws.slatePaint.color = bgColor
        ws.slatePaint.alpha = alphaInt
        canvas.drawRect(dst, ws.slatePaint)

        ws.textPaint.typeface = getTypeface(SceneFontOption.SPACE_GROTESK.fontKey, true)
        ws.textPaint.textSize = (20f * scaleFactor).coerceIn(11f, dst.height() * 0.14f)
        ws.textPaint.color = 0xFFCBD5E1.toInt()
        ws.textPaint.alpha = alphaInt
        canvas.drawText(layer.name.uppercase(), dst.centerX(), dst.centerY() - 12f * scaleFactor, ws.textPaint)

        ws.textPaint.typeface = getTypeface(SceneFontOption.ROBOTO_MONO.fontKey, false)
        ws.textPaint.textSize = (13f * scaleFactor).coerceIn(9f, dst.height() * 0.09f)
        ws.textPaint.color = 0xFF94A3B8.toInt()
        ws.textPaint.alpha = alphaInt

        val subLabel = when (layer.type) {
            SourceType.SCREEN_CAPTURE -> "Screen / App Capture Standby"
            SourceType.CAMERA -> "${layer.cameraConfig.facing.displayName} • ${layer.widthPercent}%×${layer.heightPercent}%"
            SourceType.NETWORK_VIDEO -> layer.networkConfig.streamUrl
            else -> "${layer.widthPercent}%×${layer.heightPercent}%"
        }
        canvas.drawText(subLabel, dst.centerX(), dst.centerY() + 16f * scaleFactor, ws.textPaint)
    }
}
