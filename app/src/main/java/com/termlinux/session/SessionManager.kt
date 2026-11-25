package com.termlinux.session

import android.content.Context
import android.content.Intent
import android.os.Build
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termlinux.bridge.AndroidBridgeServer
import com.termlinux.core.AppPaths
import com.termlinux.distro.DistroEntry
import com.termlinux.distro.DistroManager
import com.termlinux.runtime.NativeBridge
import com.termlinux.runtime.PRootCommandBuilder
import com.termlinux.service.LinuxSessionService
import com.termlinux.settings.AppSettings
import com.termlinux.storage.StorageBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

sealed interface SessionState {
    data object Idle : SessionState
    data class Starting(val distroId: String, val distroName: String) : SessionState
    data class Running(
        val pid: Int,
        val distroId: String,
        val distroName: String,
        val startedAtMs: Long
    ) : SessionState

    data class Paused(
        val pid: Int,
        val distroId: String,
        val distroName: String
    ) : SessionState

    data class Exited(val exitCode: Int, val distroId: String) : SessionState
    data class ProcessLost(val distroId: String, val reason: String) : SessionState
    data class Failed(val reason: String) : SessionState
}

data class ManagedSessionInfo(
    val id: String,
    val title: String,
    val distroId: String,
    val distroName: String,
    val pid: Int,
    val isRunning: Boolean,
    val exitCode: Int? = null
)

class SessionManager(
    private val context: Context,
    private val paths: AppPaths,
    private val distroManager: DistroManager,
    private val storageBridge: StorageBridge,
    private val androidBridgeServer: AndroidBridgeServer
) {
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _sessions = MutableStateFlow<List<ManagedSessionInfo>>(emptyList())
    val sessions: StateFlow<List<ManagedSessionInfo>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val sessionEntries = CopyOnWriteArrayList<Entry>()

    private data class Entry(
        val id: String,
        var title: String,
        val distro: DistroEntry,
        val terminalSession: TerminalSession,
        var state: SessionState
    )

    val activeTerminalSession: TerminalSession?
        get() = sessionEntries.firstOrNull { it.id == _activeSessionId.value }?.terminalSession

    private var uiClient: TerminalSessionClient? = null

    fun attachUiClient(client: TerminalSessionClient?) {
        uiClient = client
        sessionEntries.forEach { it.terminalSession.updateTerminalSessionClient(serviceAwareClient) }
        verifyProcessLiveness()
    }

    fun switchSession(sessionId: String) {
        val target = sessionEntries.firstOrNull { it.id == sessionId } ?: return
        _activeSessionId.value = target.id
        _sessionState.value = target.state
        verifyProcessLiveness()
    }

    fun verifyProcessLiveness() {
        var changed = false
        for (entry in sessionEntries) {
            val st = entry.state
            if (st is SessionState.Running || st is SessionState.Paused) {
                val pid = entry.terminalSession.pid
                val alive = pid > 0 && entry.terminalSession.isRunning && NativeBridge.nativeIsProcessAlive(pid)
                if (!alive) {
                    entry.state = SessionState.ProcessLost(
                        distroId = entry.distro.id,
                        reason = "Linux session process (pid=$pid) is no longer running."
                    )
                    changed = true
                }
            }
        }
        if (changed) {
            publishSessions()
            sessionEntries.firstOrNull { it.id == _activeSessionId.value }?.let {
                _sessionState.value = it.state
            }
        }
    }

    fun onActivityBackgrounded() {
        verifyProcessLiveness()
        var changed = false
        for (entry in sessionEntries) {
            val st = entry.state
            if (st is SessionState.Running) {
                entry.state = SessionState.Paused(st.pid, st.distroId, st.distroName)
                changed = true
            }
        }
        if (changed) {
            publishSessions()
            sessionEntries.firstOrNull { it.id == _activeSessionId.value }?.let {
                _sessionState.value = it.state
            }
        }
    }

    fun onActivityForegrounded() {
        verifyProcessLiveness()
        var changed = false
        for (entry in sessionEntries) {
            val st = entry.state
            if (st is SessionState.Paused) {
                entry.state = SessionState.Running(
                    st.pid,
                    st.distroId,
                    st.distroName,
                    System.currentTimeMillis()
                )
                changed = true
            }
        }
        if (changed) {
            publishSessions()
            sessionEntries.firstOrNull { it.id == _activeSessionId.value }?.let {
                _sessionState.value = it.state
            }
        }
    }

    suspend fun startSession(
        distro: DistroEntry,
        settings: AppSettings,
        forceNewTab: Boolean = false
    ): Result<TerminalSession> {
        if (!forceNewTab) {
            val existing = sessionEntries.firstOrNull {
                it.distro.id == distro.id && it.terminalSession.isRunning
            }
            if (existing != null) {
                verifyProcessLiveness()
                if (existing.state is SessionState.Running || existing.state is SessionState.Paused) {
                    switchSession(existing.id)
                    return Result.success(existing.terminalSession)
                }
            }
        }

        return runCatching {
            _sessionState.value = SessionState.Starting(distro.id, distro.name)

            if (!distroManager.isInstalled(distro.id)) {
                throw IllegalStateException("${distro.name} is not installed yet")
            }

            distroManager.ensureRuntimePatches(distro)
            val rootfsDir = paths.rootfsDir(distro.id)
            val shellPath = distroManager.resolveShell(distro)
            val storageBinds = storageBridge.preparePRootBindings(settings.safTreeUri)

            val extraEnv = mutableMapOf(
                "TL_BRIDGE_PORT" to AndroidBridgeServer.DEFAULT_PORT.toString(),
                "TL_BRIDGE_TOKEN" to androidBridgeServer.authToken,
                "TL_BOOTSTRAP_CREATE_USER" to if (settings.bootstrapCreateUser) "1" else "0",
                "TL_BOOTSTRAP_USER" to settings.bootstrapUsername,
                "TL_BOOTSTRAP_ZSH" to if (settings.bootstrapZshPreset) "1" else "0"
            )

            val launchSpec = PRootCommandBuilder.build(
                prootBinary = paths.prootBinary(),
                loaderBinary = paths.prootLoaderBinary(),
                rootfsDir = rootfsDir,
                tmpDir = paths.tmpDir(),
                shellPath = shellPath,
                bindMounts = storageBinds,
                extraEnv = extraEnv
            )

            val session = TerminalSession(
                launchSpec.executable,
                launchSpec.workingDirectory,
                launchSpec.argv.toTypedArray(),
                launchSpec.envp.toTypedArray(),
                settings.scrollbackLines,
                serviceAwareClient
            )
            val index = sessionEntries.size + 1
            val title = "${distro.id} #$index"
            session.mSessionName = title

            val entry = Entry(
                id = session.mHandle,
                title = title,
                distro = distro,
                terminalSession = session,
                state = SessionState.Starting(distro.id, distro.name)
            )
            sessionEntries.add(entry)
            _activeSessionId.value = entry.id
            publishSessions()
            startForegroundService()
            session
        }.onFailure { err ->
            _sessionState.value = SessionState.Failed(err.message ?: "Failed to start PRoot session")
        }
    }

    fun closeSession(sessionId: String) {
        val entry = sessionEntries.firstOrNull { it.id == sessionId } ?: return
        val pid = entry.terminalSession.pid
        runCatching { entry.terminalSession.finishIfRunning() }
        if (pid > 0) {
            NativeBridge.nativeKillProcess(pid, 15)
        }
        sessionEntries.remove(entry)
        if (_activeSessionId.value == sessionId) {
            val next = sessionEntries.lastOrNull()
            _activeSessionId.value = next?.id
            _sessionState.value = next?.state ?: SessionState.Idle
        }
        publishSessions()
        if (sessionEntries.none { it.terminalSession.isRunning }) {
            stopForegroundService()
        }
    }

    fun stopSession() {
        val activeId = _activeSessionId.value
        if (activeId != null) {
            closeSession(activeId)
        } else {
            stopAllSessions()
        }
    }

    fun stopAllSessions() {
        sessionEntries.toList().forEach { entry ->
            val pid = entry.terminalSession.pid
            runCatching { entry.terminalSession.finishIfRunning() }
            if (pid > 0) NativeBridge.nativeKillProcess(pid, 15)
        }
        sessionEntries.clear()
        _activeSessionId.value = null
        _sessionState.value = SessionState.Idle
        publishSessions()
        stopForegroundService()
    }

    private fun publishSessions() {
        _sessions.value = sessionEntries.map { entry ->
            val running = entry.state is SessionState.Running ||
                entry.state is SessionState.Paused ||
                entry.state is SessionState.Starting
            val exitCode = (entry.state as? SessionState.Exited)?.exitCode
            ManagedSessionInfo(
                id = entry.id,
                title = entry.title,
                distroId = entry.distro.id,
                distroName = entry.distro.name,
                pid = entry.terminalSession.pid,
                isRunning = running,
                exitCode = exitCode
            )
        }
    }

    private fun startForegroundService() {
        val intent = Intent(context, LinuxSessionService::class.java).apply {
            action = LinuxSessionService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopForegroundService() {
        val intent = Intent(context, LinuxSessionService::class.java).apply {
            action = LinuxSessionService.ACTION_STOP
        }
        context.startService(intent)
    }

    private val serviceAwareClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            if (changedSession.mHandle == _activeSessionId.value) {
                uiClient?.onTextChanged(changedSession)
            }
        }

        override fun onTitleChanged(changedSession: TerminalSession) {
            val entry = sessionEntries.firstOrNull { it.id == changedSession.mHandle }
            val newTitle = changedSession.title
            if (entry != null && !newTitle.isNullOrBlank()) {
                entry.title = newTitle.take(32)
                publishSessions()
            }
            if (changedSession.mHandle == _activeSessionId.value) {
                uiClient?.onTitleChanged(changedSession)
            }
        }

        override fun onSessionFinished(finishedSession: TerminalSession) {
            val entry = sessionEntries.firstOrNull { it.id == finishedSession.mHandle }
            val distroId = entry?.distro?.id ?: "linux"
            val exitCode = finishedSession.exitStatus
            val newState = SessionState.Exited(exitCode, distroId)
            if (entry != null) {
                entry.state = newState
            }
            if (finishedSession.mHandle == _activeSessionId.value) {
                _sessionState.value = newState
                uiClient?.onSessionFinished(finishedSession)
            }
            publishSessions()
            if (sessionEntries.none { it.terminalSession.isRunning }) {
                stopForegroundService()
            }
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
            uiClient?.onCopyTextToClipboard(session, text)
        }

        override fun onPasteTextFromClipboard(session: TerminalSession?) {
            uiClient?.onPasteTextFromClipboard(session)
        }

        override fun onBell(session: TerminalSession) {
            uiClient?.onBell(session)
        }

        override fun onColorsChanged(session: TerminalSession) {
            if (session.mHandle == _activeSessionId.value) {
                uiClient?.onColorsChanged(session)
            }
        }

        override fun onTerminalCursorStateChange(state: Boolean) {
            uiClient?.onTerminalCursorStateChange(state)
        }

        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
            val entry = sessionEntries.firstOrNull { it.id == session.mHandle }
            val distro = entry?.distro
            val newState = SessionState.Running(
                pid = pid,
                distroId = distro?.id ?: "linux",
                distroName = distro?.name ?: "Linux",
                startedAtMs = System.currentTimeMillis()
            )
            if (entry != null) {
                entry.state = newState
            }
            if (session.mHandle == _activeSessionId.value) {
                _sessionState.value = newState
                uiClient?.setTerminalShellPid(session, pid)
            }
            publishSessions()
        }

        override fun getTerminalCursorStyle(): Int = uiClient?.getTerminalCursorStyle() ?: 0
        override fun logError(tag: String?, message: String?) {}
        override fun logWarn(tag: String?, message: String?) {}
        override fun logInfo(tag: String?, message: String?) {}
        override fun logDebug(tag: String?, message: String?) {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
        override fun logStackTrace(tag: String?, e: Exception?) {}
    }
}
