package com.termlinux.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
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
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Localhost-only TCP PCM audio server (127.0.0.1:14713) backed by Android AudioTrack.
 * Accepts raw S16LE 44100Hz stereo PCM streams from PulseAudio module-simple-protocol-tcp or pipe/nc.
 */
class AudioBridgeServer(
    private val port: Int = DEFAULT_PORT
) {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var scope: CoroutineScope? = null
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    fun start() {
        if (_isRunning.value) return
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope
        acceptJob = newScope.launch {
            runCatching {
                val loopback = InetAddress.getByName("127.0.0.1")
                val server = ServerSocket(port, 4, loopback)
                serverSocket = server
                _isRunning.value = true

                while (isActive && !server.isClosed) {
                    val client = runCatching { server.accept() }.getOrNull() ?: break
                    launch { handleClient(client) }
                }
            }
            _isRunning.value = false
        }
    }

    fun stop() {
        _isRunning.value = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        scope?.cancel()
        scope = null
    }

    private fun handleClient(socket: Socket) {
        val sampleRate = 44100
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(8192)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        try {
            track.play()
            socket.getInputStream().use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    track.write(buffer, 0, read)
                }
            }
        } catch (_: Throwable) {
            // Client disconnected or audio stopped
        } finally {
            runCatching { track.stop() }
            runCatching { track.release() }
            runCatching { socket.close() }
        }
    }

    companion object {
        const val DEFAULT_PORT = 14713
    }
}
