package com.termlinux.bridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Localhost-only authenticated IPC bridge (127.0.0.1:14714) for Linux CLI helpers:
 * tl-clip, tl-open, tl-notify.
 */
class AndroidBridgeServer(
    private val context: Context,
    private val port: Int = DEFAULT_PORT
) {
    val authToken: String = generateToken()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val notifyIdCounter = AtomicInteger(2000)

    private var scope: CoroutineScope? = null
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    fun start() {
        if (serverSocket != null) return
        ensureNotificationChannel()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope
        acceptJob = newScope.launch {
            runCatching {
                val server = ServerSocket(port, 8, InetAddress.getByName("127.0.0.1"))
                serverSocket = server
                while (isActive && !server.isClosed) {
                    val client = runCatching { server.accept() }.getOrNull() ?: break
                    launch { handleClient(client) }
                }
            }
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        scope?.cancel()
        scope = null
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = PrintWriter(socket.getOutputStream(), true, Charsets.UTF_8)

            val authLine = reader.readLine() ?: return
            if (authLine != "AUTH $authToken") {
                writer.println("ERR unauthorized")
                return
            }

            val cmdLine = reader.readLine() ?: return
            val parts = cmdLine.split(" ", limit = 3)
            val cmd = parts.getOrNull(0).orEmpty()

            when (cmd) {
                "CLIP_GET" -> {
                    val text = readClipboard()
                    val b64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    writer.println("OK $b64")
                }

                "CLIP_SET" -> {
                    val b64 = parts.getOrNull(1).orEmpty()
                    val text = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                    writeClipboard(text)
                    writer.println("OK")
                }

                "OPEN" -> {
                    val b64 = parts.getOrNull(1).orEmpty()
                    val target = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8).trim()
                    val ok = openTarget(target)
                    writer.println(if (ok) "OK" else "ERR cannot_open")
                }

                "NOTIFY" -> {
                    val titleB64 = parts.getOrNull(1).orEmpty()
                    val bodyB64 = parts.getOrNull(2).orEmpty()
                    val title = String(Base64.decode(titleB64, Base64.DEFAULT), Charsets.UTF_8)
                    val body = String(Base64.decode(bodyB64, Base64.DEFAULT), Charsets.UTF_8)
                    postNotification(title.ifBlank { "TermLinux" }, body)
                    writer.println("OK")
                }

                else -> writer.println("ERR unknown_command")
            }
        } catch (_: Throwable) {
            // Ignore malformed client
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun readClipboard(): String {
        var result = ""
        val latch = CountDownLatch(1)
        mainHandler.post {
            runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = cm?.primaryClip
                result = clip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            }
            latch.countDown()
        }
        latch.await(2, TimeUnit.SECONDS)
        return result
    }

    private fun writeClipboard(text: String) {
        mainHandler.post {
            runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("TermLinux", text))
            }
        }
    }

    private fun openTarget(target: String): Boolean {
        if (target.isBlank()) return false
        return runCatching {
            val uri = if (target.startsWith("http://") || target.startsWith("https://")) {
                Uri.parse(target)
            } else {
                Uri.parse(target)
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun postNotification(title: String, body: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        nm.notify(notifyIdCounter.incrementAndGet(), notif)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TermLinux Script Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val DEFAULT_PORT = 14714
        private const val CHANNEL_ID = "termlinux_cli_alerts"

        private fun generateToken(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
