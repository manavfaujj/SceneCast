package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.model.CanvasPreset
import com.example.model.CropRect
import com.example.model.LayoutPreset
import com.example.model.SourceType
import com.example.streaming.RtmpProtocolClient
import com.example.viewmodel.ResizeHandle
import com.example.viewmodel.SceneCastViewModel
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExampleRobolectricTest {

    @Test
    fun `verify app name is SceneCast Live`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SceneCast Live", appName)
    }

    @Test
    fun `critical acceptance test - screen capture and camera scene composition, layout presets, resizing, PNG borders, audio mixer, and YouTube RTMPS`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = SceneCastViewModel(app)

        // 1. Open SceneCast Live & create a clean 16:9 1080p scene
        viewModel.createNewScene("Live Studio Broadcast", CanvasPreset.LANDSCAPE_16_9, 1920, 1080)
        var scene = viewModel.uiState.value.activeScene
        assertEquals("Live Studio Broadcast", scene.name)
        assertEquals(1920, scene.canvasWidth)
        assertEquals(1080, scene.canvasHeight)

        // 2. Verify SCREEN_CAPTURE and CAMERA are the default sources
        val screenLayerInitial = scene.layers.firstOrNull { it.type == SourceType.SCREEN_CAPTURE }
        val camLayerInitial = scene.layers.firstOrNull { it.type == SourceType.CAMERA }
        assertNotNull(screenLayerInitial)
        assertNotNull(camLayerInitial)
        assertEquals("Screen / App Capture", SourceType.SCREEN_CAPTURE.displayName)

        // 3. Render offscreen composition
        val compFrame1: Bitmap = viewModel.compositor.renderToOffscreenBitmap(
            activeScene = viewModel.uiState.value.activeScene,
            targetWidth = 1920,
            targetHeight = 1080
        )
        assertNotNull(compFrame1)
        assertEquals(1920, compFrame1.width)
        assertEquals(1080, compFrame1.height)

        // 4. Apply layout preset (65% Screen left, 35% Camera right)
        viewModel.applyLayoutPreset(LayoutPreset.SCREEN_65_CAM_35)
        scene = viewModel.uiState.value.activeScene
        val screenLayer = scene.layers.first { it.type == SourceType.SCREEN_CAPTURE }
        val camLayer = scene.layers.first { it.type == SourceType.CAMERA }
        assertEquals(65, screenLayer.widthPercent)
        assertEquals(0, screenLayer.xPercent)
        assertEquals(35, camLayer.widthPercent)
        assertEquals(65, camLayer.xPercent)

        // 5. Resize screen layer via handle and percentage
        viewModel.resizeLayerFromHandle(screenLayer.id, ResizeHandle.RIGHT, -0.05f, 0f)
        assertEquals(60, viewModel.uiState.value.activeScene.layers.first { it.id == screenLayer.id }.widthPercent)
        viewModel.setLayerExactPercentageWidth(screenLayer.id, 65)
        assertEquals(65, viewModel.uiState.value.activeScene.layers.first { it.id == screenLayer.id }.widthPercent)

        // 6. Add custom border styling to Camera window
        viewModel.updateLayer(camLayer.id) { layer ->
            layer.copy(
                crop = CropRect(left = 0.04f, top = 0.02f, right = 0.04f, bottom = 0.02f),
                border = layer.border.copy(
                    enabled = true,
                    colorHex = 0xFF00E5FFL,
                    thicknessDp = 4f,
                    cornerRadiusPercent = 8f,
                    builtInPngFrameRes = R.drawable.img_frame_cyber_cyan
                )
            )
        }

        // 7. Verify AudioMixerEngine channel volume & mute controls
        val micChannel = viewModel.audioState.value.micChannel
        assertNotNull(micChannel)
        viewModel.audioMixer.setChannelVolume("mic", 0.85f)
        assertEquals(0.85f, viewModel.audioState.value.micChannel.volume, 0.01f)
        viewModel.audioMixer.toggleChannelMute("mic")
        assertTrue(viewModel.audioState.value.micChannel.muted)
        viewModel.audioMixer.toggleChannelMute("mic")
        assertFalse(viewModel.audioState.value.micChannel.muted)

        // 8. Switch between Edit Mode and Program Mode
        viewModel.setProgramMode(true)
        assertTrue(viewModel.uiState.value.isProgramMode)
        viewModel.setProgramMode(false)
        assertFalse(viewModel.uiState.value.isProgramMode)

        // 9. Verify RTMP/RTMPS publishing pipeline
        val rtmpClient = RtmpProtocolClient()
        val combinedAnnexB = byteArrayOf(
            0, 0, 0, 1, 0x67, 0x42, 0x00, 0x1F,
            0, 0, 0, 1, 0x68, 0xCE.toByte(), 0x3C, 0x80.toByte(),
            0, 0, 1, 0x65, 0x88.toByte(), 0x84.toByte(), 0x21
        )
        ServerSocket(0).use { server ->
            val port = server.localPort
            val serverThread = thread {
                server.accept().use { clientSock ->
                    val inp = DataInputStream(clientSock.getInputStream())
                    val out = DataOutputStream(clientSock.getOutputStream())
                    val c0 = inp.readUnsignedByte()
                    assertEquals(0x03, c0)
                    val c1 = ByteArray(1536)
                    inp.readFully(c1)
                    out.writeByte(0x03)
                    out.write(ByteArray(1536))
                    out.write(c1)
                    out.flush()
                    val c2 = ByteArray(1536)
                    inp.readFully(c2)
                    val publishBuf = ByteArray(4096)
                    var totalRead = 0
                    while (true) {
                        val n = inp.read(publishBuf)
                        if (n <= 0) break
                        totalRead += n
                    }
                    assertTrue(totalRead > 0)
                }
            }

            rtmpClient.connectAndPublish(
                serverUrl = "rtmp://127.0.0.1:$port/live2",
                streamKey = "test-stream-key",
                width = 1920,
                height = 1080,
                fps = 30,
                videoBitrateKbps = 6500,
                audioBitrateKbps = 128
            )
            assertTrue(rtmpClient.isConnected)
            rtmpClient.sendAvcVideoFrame(combinedAnnexB, isKeyframe = true, timestampMs = 33)
            rtmpClient.sendAacSequenceHeader(byteArrayOf(0x12, 0x08), timestampMs = 33)
            rtmpClient.sendAacAudioFrame(byteArrayOf(0x01, 0x02, 0x03, 0x04), timestampMs = 50)
            assertTrue(rtmpClient.totalBytesSent > 3200L)
            rtmpClient.disconnect()
            serverThread.join(3000)
        }
    }

    @Test
    fun `test delete source from scene and rename source layer`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = SceneCastViewModel(app)

        viewModel.createNewScene("Custom Scene", CanvasPreset.PORTRAIT_9_16, 1080, 1920)
        val initialLayers = viewModel.uiState.value.activeScene.layers
        assertTrue(initialLayers.isNotEmpty())

        val firstLayer = initialLayers.first()
        viewModel.renameLayer(firstLayer.id, "Main Gameplay Screen")
        val renamed = viewModel.uiState.value.activeScene.layers.first { it.id == firstLayer.id }
        assertEquals("Main Gameplay Screen", renamed.name)

        // Delete source from scene
        val countBefore = viewModel.uiState.value.activeScene.layers.size
        viewModel.deleteSourceFromScene(viewModel.uiState.value.activeSceneId, firstLayer.id)
        val countAfter = viewModel.uiState.value.activeScene.layers.size
        assertEquals(countBefore - 1, countAfter)
        assertFalse(viewModel.uiState.value.activeScene.layers.any { it.id == firstLayer.id })
    }

    @Test
    fun `test add text label and PNG overlay sources`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = SceneCastViewModel(app)

        val beforeCount = viewModel.uiState.value.activeScene.layers.size
        viewModel.addSourceLayer(SourceType.TEXT)
        assertEquals(beforeCount + 1, viewModel.uiState.value.activeScene.layers.size)

        val textLayer = viewModel.uiState.value.activeScene.layers.first { it.type == SourceType.TEXT }
        assertEquals("Stream Label", textLayer.name)

        viewModel.addSourceLayer(SourceType.PNG_OVERLAY, builtInRes = R.drawable.img_frame_broadcast_gold)
        assertEquals(beforeCount + 2, viewModel.uiState.value.activeScene.layers.size)
    }
}
