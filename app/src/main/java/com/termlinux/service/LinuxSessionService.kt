package com.termlinux.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.termlinux.MainActivity
import com.termlinux.R
import com.termlinux.TermLinuxApp
import com.termlinux.audio.AudioBridgeServer
import com.termlinux.session.SessionState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LinuxSessionService : LifecycleService() {

    private var isForegroundStarted = false
    private var wakeLock: PowerManager.WakeLock? = null
    private val audioBridge = AudioBridgeServer()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val app = application as TermLinuxApp
        lifecycleScope.launch {
            app.container.sessionManager.sessionState.collectLatest { state ->
                if (isForegroundStarted) {
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(NOTIFICATION_ID, buildNotification(state))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                val app = application as TermLinuxApp
                app.container.androidBridgeServer.stop()
                audioBridge.stop()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForegroundStarted = false
                stopSelf()
            }

            ACTION_TERMINATE_SESSION -> {
                val app = application as TermLinuxApp
                app.container.androidBridgeServer.stop()
                audioBridge.stop()
                releaseWakeLock()
                app.container.sessionManager.stopAllSessions()
            }

            ACTION_ACQUIRE_WAKELOCK -> acquireWakeLock()
            ACTION_RELEASE_WAKELOCK -> releaseWakeLock()
            ACTION_START_AUDIO -> audioBridge.start()
            ACTION_STOP_AUDIO -> audioBridge.stop()

            else -> {
                val app = application as TermLinuxApp
                app.container.androidBridgeServer.start()
                val notification = buildNotification(app.container.sessionManager.sessionState.value)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isForegroundStarted = true
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        audioBridge.stop()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TermLinux::SessionWakeLock").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }
        if (isForegroundStarted) {
            val app = application as TermLinuxApp
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(app.container.sessionManager.sessionState.value))
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
        if (isForegroundStarted) {
            val app = application as TermLinuxApp
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(app.container.sessionManager.sessionState.value))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: SessionState): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LinuxSessionService::class.java).apply {
                action = ACTION_TERMINATE_SESSION
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wlSuffix = if (wakeLock?.isHeld == true) " • WakeLock" else ""
        val (title, text) = when (state) {
            is SessionState.Running -> "TermLinux: ${state.distroName}" to "Session running (PID ${state.pid})$wlSuffix"
            is SessionState.Paused -> "TermLinux: ${state.distroName}" to "Session active in background (PID ${state.pid})$wlSuffix"
            is SessionState.Starting -> "TermLinux" to "Starting ${state.distroName}..."
            is SessionState.Exited -> "TermLinux" to "Session exited (${state.exitCode})"
            is SessionState.ProcessLost -> "TermLinux" to state.reason
            is SessionState.Failed -> "TermLinux" to state.reason
            SessionState.Idle -> "TermLinux" to "No active session"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(state is SessionState.Running || state is SessionState.Paused || state is SessionState.Starting)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.action_exit_session), stopIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "termlinux_session_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.termlinux.action.START_SESSION"
        const val ACTION_STOP = "com.termlinux.action.STOP_SESSION"
        const val ACTION_TERMINATE_SESSION = "com.termlinux.action.TERMINATE_SESSION"
        const val ACTION_ACQUIRE_WAKELOCK = "com.termlinux.action.ACQUIRE_WAKELOCK"
        const val ACTION_RELEASE_WAKELOCK = "com.termlinux.action.RELEASE_WAKELOCK"
        const val ACTION_START_AUDIO = "com.termlinux.action.START_AUDIO"
        const val ACTION_STOP_AUDIO = "com.termlinux.action.STOP_AUDIO"
    }
}
