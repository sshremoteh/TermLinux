package com.termlinux.desktop

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

sealed interface VncState {
    data object Disconnected : VncState
    data object Connecting : VncState
    data class Connected(val width: Int, val height: Int, val name: String) : VncState
    data class Error(val message: String) : VncState
}

/**
 * Lightweight RFB 3.8 (VNC) client for localhost PRoot Linux GUI desktops (127.0.0.1:5901).
 * Supports SecurityType=None (1), Raw (0) and CopyRect (1) encodings, pointer and key events.
 */
class VncClient(
    private val onFrameUpdated: (Bitmap) -> Unit
) {
    private val _state = MutableStateFlow<VncState>(VncState.Disconnected)
    val state: StateFlow<VncState> = _state.asStateFlow()

    private var scope: CoroutineScope? = null
    private var socket: Socket? = null
    private var out: DataOutputStream? = null
    private var framebuffer: Bitmap? = null
    private var fbWidth = 0
    private var fbHeight = 0
    private val writeLock = Any()

    fun connect(host: String = "127.0.0.1", port: Int = 5901) {
        disconnect()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope
        _state.value = VncState.Connecting

        newScope.launch {
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(host, port), 4000)
                sock.tcpNoDelay = true
                socket = sock

                val input = DataInputStream(BufferedInputStream(sock.getInputStream(), 64 * 1024))
                val output = DataOutputStream(BufferedOutputStream(sock.getOutputStream(), 8192))
                out = output

                // 1. ProtocolVersion handshake
                val serverVersion = ByteArray(12)
                input.readFully(serverVersion)
                val verStr = String(serverVersion, Charsets.US_ASCII)
                if (!verStr.startsWith("RFB ")) {
                    throw IOException("Invalid RFB server banner: $verStr")
                }
                output.write("RFB 003.008\n".toByteArray(Charsets.US_ASCII))
                output.flush()

                // 2. Security handshake
                val numSecTypes = input.readUnsignedByte()
                if (numSecTypes == 0) {
                    val reasonLen = input.readInt().coerceIn(0, 4096)
                    val reasonBytes = ByteArray(reasonLen)
                    input.readFully(reasonBytes)
                    throw IOException("VNC auth error: ${String(reasonBytes)}")
                }
                val secTypes = ByteArray(numSecTypes)
                input.readFully(secTypes)
                if (!secTypes.contains(1.toByte())) {
                    throw IOException("VNC server requires authentication; start tl-desktop with -SecurityTypes None on localhost")
                }
                output.writeByte(1) // SecurityType = None
                output.flush()

                val secResult = input.readInt()
                if (secResult != 0) {
                    throw IOException("VNC security handshake failed: code=$secResult")
                }

                // 3. ClientInit (shared = 1)
                output.writeByte(1)
                output.flush()

                // 4. ServerInit
                fbWidth = input.readUnsignedShort()
                fbHeight = input.readUnsignedShort()
                // Skip 16-byte PixelFormat from server, then set our 32-bit BGRA PixelFormat
                input.skipBytes(16)
                val nameLen = input.readInt().coerceIn(0, 4096)
                val nameBytes = ByteArray(nameLen)
                input.readFully(nameBytes)
                val desktopName = String(nameBytes, Charsets.UTF_8)

                // SetPixelFormat: 32bpp, depth 24, little-endian, true-color, RGB888
                synchronized(writeLock) {
                    output.writeByte(0) // SetPixelFormat
                    output.write(ByteArray(3)) // padding
                    output.writeByte(32) // bits-per-pixel
                    output.writeByte(24) // depth
                    output.writeByte(0) // big-endian = false
                    output.writeByte(1) // true-colour = true
                    output.writeShort(255) // red-max
                    output.writeShort(255) // green-max
                    output.writeShort(255) // blue-max
                    output.writeByte(16) // red-shift
                    output.writeByte(8) // green-shift
                    output.writeByte(0) // blue-shift
                    output.write(ByteArray(3)) // padding

                    // SetEncodings: CopyRect (1), Raw (0)
                    output.writeByte(2)
                    output.writeByte(0)
                    output.writeShort(2)
                    output.writeInt(1) // CopyRect
                    output.writeInt(0) // Raw
                    output.flush()
                }

                val bmp = Bitmap.createBitmap(fbWidth, fbHeight, Bitmap.Config.ARGB_8888)
                framebuffer = bmp
                _state.value = VncState.Connected(fbWidth, fbHeight, desktopName)

                requestUpdate(output, incremental = false)

                while (isActive && !sock.isClosed) {
                    val msgType = input.readUnsignedByte()
                    when (msgType) {
                        0 -> { // FramebufferUpdate
                            input.readUnsignedByte() // padding
                            val numRects = input.readUnsignedShort()
                            for (i in 0 until numRects) {
                                val x = input.readUnsignedShort()
                                val y = input.readUnsignedShort()
                                val w = input.readUnsignedShort()
                                val h = input.readUnsignedShort()
                                val encoding = input.readInt()
                                when (encoding) {
                                    0 -> readRawRect(input, bmp, x, y, w, h)
                                    1 -> readCopyRect(input, bmp, x, y, w, h)
                                    else -> throw IOException("Unsupported VNC encoding: $encoding")
                                }
                            }
                            onFrameUpdated(bmp)
                            requestUpdate(output, incremental = true)
                        }

                        1 -> { // SetColourMapEntries
                            input.readUnsignedByte()
                            input.readUnsignedShort()
                            val count = input.readUnsignedShort()
                            input.skipBytes(count * 6)
                        }

                        2 -> Unit // Bell

                        3 -> { // ServerCutText
                            input.skipBytes(3)
                            val len = input.readInt().coerceIn(0, 1024 * 1024)
                            input.skipBytes(len)
                        }

                        else -> throw IOException("Unknown VNC message type: $msgType")
                    }
                }
            } catch (_: EOFException) {
                if (_state.value !is VncState.Disconnected) {
                    _state.value = VncState.Error("Desktop VNC server closed connection.")
                }
            } catch (e: Throwable) {
                if (_state.value !is VncState.Disconnected) {
                    _state.value = VncState.Error(e.message ?: "Unable to connect to 127.0.0.1:$port")
                }
            }
        }
    }

    fun sendPointerEvent(buttonMask: Int, x: Int, y: Int) {
        val output = out ?: return
        val clampedX = x.coerceIn(0, (fbWidth - 1).coerceAtLeast(0))
        val clampedY = y.coerceIn(0, (fbHeight - 1).coerceAtLeast(0))
        scope?.launch {
            runCatching {
                synchronized(writeLock) {
                    output.writeByte(5) // PointerEvent
                    output.writeByte(buttonMask)
                    output.writeShort(clampedX)
                    output.writeShort(clampedY)
                    output.flush()
                }
            }
        }
    }

    fun sendKeyEvent(down: Boolean, keySym: Int) {
        val output = out ?: return
        scope?.launch {
            runCatching {
                synchronized(writeLock) {
                    output.writeByte(4) // KeyEvent
                    output.writeByte(if (down) 1 else 0)
                    output.writeShort(0) // padding
                    output.writeInt(keySym)
                    output.flush()
                }
            }
        }
    }

    fun disconnect() {
        _state.value = VncState.Disconnected
        runCatching { socket?.close() }
        socket = null
        out = null
        scope?.cancel()
        scope = null
    }

    private fun requestUpdate(output: DataOutputStream, incremental: Boolean) {
        synchronized(writeLock) {
            output.writeByte(3) // FramebufferUpdateRequest
            output.writeByte(if (incremental) 1 else 0)
            output.writeShort(0)
            output.writeShort(0)
            output.writeShort(fbWidth)
            output.writeShort(fbHeight)
            output.flush()
        }
    }

    private fun readRawRect(
        input: DataInputStream,
        bmp: Bitmap,
        x: Int,
        y: Int,
        w: Int,
        h: Int
    ) {
        if (w <= 0 || h <= 0) return
        val pixelCount = w * h
        val raw = ByteArray(pixelCount * 4)
        input.readFully(raw)
        val pixels = IntArray(pixelCount)
        var i = 0
        var p = 0
        while (p < pixelCount) {
            val b = raw[i].toInt() and 0xFF
            val g = raw[i + 1].toInt() and 0xFF
            val r = raw[i + 2].toInt() and 0xFF
            pixels[p++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            i += 4
        }
        bmp.setPixels(pixels, 0, w, x, y, w, h)
    }

    private fun readCopyRect(
        input: DataInputStream,
        bmp: Bitmap,
        x: Int,
        y: Int,
        w: Int,
        h: Int
    ) {
        val srcX = input.readUnsignedShort()
        val srcY = input.readUnsignedShort()
        if (w <= 0 || h <= 0) return
        val copy = Bitmap.createBitmap(bmp, srcX, srcY, w, h)
        val canvas = Canvas(bmp)
        canvas.drawBitmap(copy, null, Rect(x, y, x + w, y + h), null)
        copy.recycle()
    }
}
