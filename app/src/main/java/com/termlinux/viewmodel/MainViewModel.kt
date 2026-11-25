package com.termlinux.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.termlinux.TermLinuxApp
import com.termlinux.distro.DistroEntry
import com.termlinux.distro.DistroManifest
import com.termlinux.distro.InstallState
import com.termlinux.service.LinuxSessionService
import com.termlinux.session.SessionState
import com.termlinux.settings.AppSettings
import com.termlinux.storage.SafEntry
import com.termlinux.storage.StorageStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as TermLinuxApp).container
    val distroManager = container.distroManager
    val storageBridge = container.storageBridge
    val sessionManager = container.sessionManager
    val settingsRepository = container.settingsRepository
    val paths = container.paths

    val manifest: DistroManifest = distroManager.loadManifest()

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings()
    )

    val installState: StateFlow<InstallState> = distroManager.installState
    val sessionState: StateFlow<SessionState> = sessionManager.sessionState
    val sessions = sessionManager.sessions
    val activeSessionId = sessionManager.activeSessionId

    private val _storageStatus = MutableStateFlow(storageBridge.inspectStatus(null))
    val storageStatus: StateFlow<StorageStatus> = _storageStatus.asStateFlow()

    private val _safEntries = MutableStateFlow<List<SafEntry>>(emptyList())
    val safEntries: StateFlow<List<SafEntry>> = _safEntries.asStateFlow()

    private val _diagnosticsText = MutableStateFlow("")
    val diagnosticsText: StateFlow<String> = _diagnosticsText.asStateFlow()

    private var installJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { s ->
                refreshStorageStatus(s.safTreeUri)
            }
        }
    }

    fun refreshStorageStatus(safUri: String? = settings.value.safTreeUri) {
        val status = storageBridge.inspectStatus(safUri)
        _storageStatus.value = status
        if (status.safPermissionValid && safUri != null) {
            viewModelScope.launch {
                _safEntries.value = storageBridge.listSafTree(safUri)
            }
        } else {
            _safEntries.value = emptyList()
        }
    }

    fun onSafTreeSelected(uri: Uri) {
        viewModelScope.launch {
            storageBridge.persistSafPermission(uri)
            settingsRepository.setSafTreeUri(uri.toString())
            refreshStorageStatus(uri.toString())
        }
    }

    fun selectDistro(distroId: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedDistro(distroId)
        }
    }

    fun startInstall(distro: DistroEntry, forceReinstall: Boolean = false) {
        installJob?.cancel()
        installJob = viewModelScope.launch {
            settingsRepository.setSelectedDistro(distro.id)
            val result = distroManager.installOrResume(distro, forceReinstall)
            if (result.isSuccess) {
                val currentSettings = settingsRepository.settingsFlow.first()
                sessionManager.startSession(distro, currentSettings)
            }
        }
    }

    fun cancelInstall() {
        installJob?.cancel()
        installJob = null
    }

    fun launchSelectedDistro(forceNewTab: Boolean = false) {
        viewModelScope.launch {
            val currentSettings = settingsRepository.settingsFlow.first()
            val distro = manifest.distros.firstOrNull { it.id == currentSettings.selectedDistroId }
                ?: manifest.distros.first()
            sessionManager.startSession(distro, currentSettings, forceNewTab = forceNewTab)
        }
    }

    fun launchDistroSession(distro: DistroEntry, forceNewTab: Boolean = true) {
        viewModelScope.launch {
            val currentSettings = settingsRepository.settingsFlow.first()
            sessionManager.startSession(distro, currentSettings, forceNewTab = forceNewTab)
        }
    }

    fun switchSession(sessionId: String) {
        sessionManager.switchSession(sessionId)
    }

    fun closeSession(sessionId: String) {
        sessionManager.closeSession(sessionId)
    }

    fun setWakeLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWakeLockEnabled(enabled)
            val app = getApplication<Application>()
            val intent = Intent(app, LinuxSessionService::class.java).apply {
                action = if (enabled) LinuxSessionService.ACTION_ACQUIRE_WAKELOCK
                else LinuxSessionService.ACTION_RELEASE_WAKELOCK
            }
            runCatching { app.startService(intent) }
        }
    }

    fun restartSession() {
        viewModelScope.launch {
            sessionManager.stopSession()
            val currentSettings = settingsRepository.settingsFlow.first()
            val distro = manifest.distros.firstOrNull { it.id == currentSettings.selectedDistroId }
                ?: manifest.distros.first()
            sessionManager.startSession(distro, currentSettings)
        }
    }

    fun isSelectedDistroInstalled(distroId: String): Boolean = distroManager.isInstalled(distroId)

    fun hasAnyDistroInstalled(): Boolean = distroManager.getInstalledDistros().isNotEmpty()

    fun runSafWriteTest(onResult: (String) -> Unit) {
        val uri = settings.value.safTreeUri
        if (uri == null) {
            onResult("No SAF folder selected.")
            return
        }
        viewModelScope.launch {
            val testName = "termlinux-saf-test.txt"
            val payload = "TermLinux SAF write test (${System.currentTimeMillis()})\n".toByteArray()
            val writeRes = storageBridge.writeSafFile(uri, testName, "text/plain", payload)
            writeRes.onSuccess { createdUri ->
                val readRes = storageBridge.readSafFile(createdUri)
                val deleted = storageBridge.deleteSafEntry(createdUri)
                _safEntries.value = storageBridge.listSafTree(uri)
                onResult("SAF test OK: wrote ${payload.size}B, readBack=${readRes.isSuccess}, cleanedUp=$deleted")
            }.onFailure { err ->
                onResult("SAF test failed: ${err.message}")
            }
        }
    }

    fun refreshDiagnostics() {
        val s = settings.value
        val proot = paths.prootBinary()
        val loader = paths.prootLoaderBinary()
        val status = storageBridge.inspectStatus(s.safTreeUri)
        val installed = distroManager.getInstalledDistros()
        val sb = StringBuilder()
        sb.appendLine("=== TermLinux Runtime Diagnostics ===")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.SUPPORTED_ABIS.joinToString()})")
        sb.appendLine("Android SDK: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        sb.appendLine("PRoot binary: ${proot.absolutePath} (exists=${proot.exists()}, exec=${proot.canExecute()}, size=${proot.length()})")
        sb.appendLine("PRoot loader: ${loader.absolutePath} (exists=${loader.exists()}, exec=${loader.canExecute()}, size=${loader.length()})")
        sb.appendLine("Installed distros: ${if (installed.isEmpty()) "none" else installed.joinToString { "${it.distroId}@${it.version}" }}")
        sb.appendLine("Storage mode: ${status.mode}")
        sb.appendLine("All files access: ${status.hasAllFilesAccess}")
        sb.appendLine("Direct path: ${status.directPath} (r=${status.directPathReadable}, w=${status.directPathWritable})")
        sb.appendLine("SAF URI: ${status.safTreeUri ?: "none"} (valid=${status.safPermissionValid})")
        sb.appendLine("Session state: ${sessionState.value}")
        _diagnosticsText.value = sb.toString()
    }

    fun buildExportLogsFile(): File {
        refreshDiagnostics()
        val logFile = File(paths.logsDir(), "termlinux-diagnostics.txt")
        logFile.writeText(_diagnosticsText.value)
        return logFile
    }
}
