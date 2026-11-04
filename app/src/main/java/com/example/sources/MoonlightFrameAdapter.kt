package com.example.sources

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.SurfaceHolder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Receives MediaCodec output through SurfaceTexture (opaque/private decoder output is supported),
 * renders it into an RGBA framebuffer, and publishes CPU bitmaps to SceneCast.
 *
 * MoonlightSessionController can keep using surfaceHolder with setRenderTarget().
 */
class MoonlightFrameAdapter(
    private val onFrame: (Bitmap) -> Unit,
    width: Int = 1920,
    height: Int = 1080
) : SurfaceHolder.Callback, AutoCloseable {
    private val frameWidth = width.coerceAtLeast(16)
    private val frameHeight = height.coerceAtLeast(16)
    private val closed = AtomicBoolean(false)
    private val thread = HandlerThread("SceneCast-Moonlight-GL").apply { start() }
    private val handler = Handler(thread.looper)
    private val ready = CountDownLatch(1)
    @Volatile private var initError: Throwable? = null
    @Volatile private var decoderSurface: Surface? = null
    val surfaceHolder: SurfaceHolder

    private var eglDisplay: android.opengl.EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: android.opengl.EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: android.opengl.EGLSurface = EGL14.EGL_NO_SURFACE
    private var oesTexture = 0
    private var rgbaTexture = 0
    private var framebuffer = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var program = 0
    private val transform = FloatArray(16)
    private var vertexBuffer: java.nio.FloatBuffer? = null
    private var pixelBuffer: ByteBuffer? = null
    private val flipMatrix = android.graphics.Matrix().apply {
        preScale(1f, -1f)
        postTranslate(0f, frameHeight.toFloat())
    }

    init {
        handler.post {
            try {
                initializeGl()
                val st = SurfaceTexture(oesTexture)
                st.setDefaultBufferSize(frameWidth, frameHeight)
                st.setOnFrameAvailableListener({
                    if (!closed.get()) renderFrame()
                }, handler)
                surfaceTexture = st
                decoderSurface = Surface(st)
            } catch (t: Throwable) {
                initError = t
            } finally {
                ready.countDown()
            }
        }
        check(ready.await(8, TimeUnit.SECONDS)) { "Timed out creating Moonlight GL output" }
        initError?.let { throw IllegalStateException("Unable to initialize Moonlight GL output", it) }
        surfaceHolder = ReaderSurfaceHolder(checkNotNull(decoderSurface), frameWidth, frameHeight)
    }

    fun getSurface(): Surface = checkNotNull(decoderSurface)

    private fun initializeGl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }
        val versions = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, versions, 0, versions, 1)) { "eglInitialize failed" }
        val attrs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(eglDisplay, attrs, 0, configs, 0, 1, count, 0) && count[0] > 0) { "No EGL config" }
        val contextAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttrs, 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
        val pbufferAttrs = intArrayOf(EGL14.EGL_WIDTH, frameWidth, EGL14.EGL_HEIGHT, frameHeight, EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], pbufferAttrs, 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed" }
        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) { "eglMakeCurrent failed" }

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0); oesTexture = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLES20.glGenTextures(1, ids, 0); rgbaTexture = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, rgbaTexture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, frameWidth, frameHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glGenFramebuffers(1, ids, 0); framebuffer = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, rgbaTexture, 0)
        check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) { "RGBA framebuffer incomplete" }
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)

        val vertices = floatArrayOf(
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
        )
        val fb = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(vertices).position(0)
        vertexBuffer = fb
        pixelBuffer = ByteBuffer.allocateDirect(frameWidth * frameHeight * 4).order(ByteOrder.nativeOrder())
    }

    private fun renderFrame() {
        try {
            val st = surfaceTexture ?: return
            st.updateTexImage()
            st.getTransformMatrix(transform)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
            GLES20.glViewport(0, 0, frameWidth, frameHeight)
            GLES20.glUseProgram(program)
            val pos = GLES20.glGetAttribLocation(program, "aPosition")
            val tex = GLES20.glGetAttribLocation(program, "aTexCoord")
            val fb = vertexBuffer ?: return
            fb.position(0)
            GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, fb)
            GLES20.glEnableVertexAttribArray(pos)
            fb.position(2)
            GLES20.glVertexAttribPointer(tex, 2, GLES20.GL_FLOAT, false, 16, fb)
            GLES20.glEnableVertexAttribArray(tex)

            val matrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix")
            GLES20.glUniformMatrix4fv(matrixLoc, 1, false, transform, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sTexture"), 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            val pixels = pixelBuffer ?: return
            pixels.clear()
            GLES20.glReadPixels(0, 0, frameWidth, frameHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
            pixels.rewind()

            val rawBitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
            rawBitmap.copyPixelsFromBuffer(pixels)
            // glReadPixels reads bottom-up into Bitmap top-down; flip Y to achieve upright unmirrored display
            val upright = Bitmap.createBitmap(rawBitmap, 0, 0, frameWidth, frameHeight, flipMatrix, false)
            rawBitmap.recycle()
            if (closed.get()) upright.recycle() else onFrame(upright)
        } catch (_: Exception) {
            // Drop a bad frame; never crash the Moonlight connection thread.
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) = Unit
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        handler.post {
            try { surfaceTexture?.setOnFrameAvailableListener(null) } catch (_: Exception) { }
            try { decoderSurface?.release() } catch (_: Exception) { }
            try { surfaceTexture?.release() } catch (_: Exception) { }
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                if (program != 0) GLES20.glDeleteProgram(program)
                if (framebuffer != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
                if (rgbaTexture != 0) GLES20.glDeleteTextures(1, intArrayOf(rgbaTexture), 0)
                if (oesTexture != 0) GLES20.glDeleteTextures(1, intArrayOf(oesTexture), 0)
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
            thread.quitSafely()
        }
    }

    private class ReaderSurfaceHolder(private val output: Surface, w: Int, h: Int) : SurfaceHolder {
        private val frame = Rect(0, 0, w, h)
        override fun getSurface(): Surface = output
        override fun getSurfaceFrame(): Rect = Rect(frame)
        override fun addCallback(callback: SurfaceHolder.Callback) = Unit
        override fun removeCallback(callback: SurfaceHolder.Callback) = Unit
        override fun isCreating() = false
        override fun setType(type: Int) = Unit
        override fun setFixedSize(width: Int, height: Int) { frame.set(0, 0, width, height) }
        override fun setSizeFromLayout() = Unit
        override fun setFormat(format: Int) = Unit
        override fun setKeepScreenOn(screenOn: Boolean) = Unit
        override fun lockCanvas(): android.graphics.Canvas? = null
        override fun lockCanvas(dirty: Rect?): android.graphics.Canvas? = null
        override fun unlockCanvasAndPost(canvas: android.graphics.Canvas) = Unit
    }

    private fun createProgram(vertex: String, fragment: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader)
            val status = IntArray(1); GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(shader) }
            return shader
        }
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        val p = GLES20.glCreateProgram(); GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs); GLES20.glLinkProgram(p)
        val status = IntArray(1); GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(p) }
        return p
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() { gl_Position = aPosition; vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy; }
        """
        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            void main() { gl_FragColor = texture2D(sTexture, vTexCoord); }
        """
    }
}
