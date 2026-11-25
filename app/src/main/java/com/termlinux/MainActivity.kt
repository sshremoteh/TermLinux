package com.termlinux

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.termlinux.screens.DesktopScreen
import com.termlinux.screens.OnboardingScreen
import com.termlinux.screens.SettingsScreen
import com.termlinux.screens.TerminalScreen
import com.termlinux.ui.theme.AppUiThemes
import com.termlinux.ui.theme.TermLinuxTheme
import com.termlinux.viewmodel.MainViewModel
import kotlinx.coroutines.launch

private enum class AppRoute {
    ONBOARDING,
    TERMINAL,
    SETTINGS,
    DESKTOP
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val notificationLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* Foreground notification works best when granted, optional if denied */ }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val settings by viewModel.settings.collectAsState()
            TermLinuxTheme(appThemeId = settings.appThemeId) {
                val installState by viewModel.installState.collectAsState()
                val sessionState by viewModel.sessionState.collectAsState()
                val sessions by viewModel.sessions.collectAsState()
                val activeSessionId by viewModel.activeSessionId.collectAsState()
                val storageStatus by viewModel.storageStatus.collectAsState()
                val safEntries by viewModel.safEntries.collectAsState()
                val diagnosticsText by viewModel.diagnosticsText.collectAsState()
                val scope = rememberCoroutineScope()

                var safTestResult by remember { mutableStateOf<String?>(null) }
                var route by remember {
                    mutableStateOf(
                        if (viewModel.hasAnyDistroInstalled()) AppRoute.TERMINAL else AppRoute.ONBOARDING
                    )
                }

                val allFilesLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) {
                    viewModel.refreshStorageStatus()
                }

                val safTreeLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree()
                ) { uri ->
                    if (uri != null) {
                        viewModel.onSafTreeSelected(uri)
                    }
                }

                val exportLogsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("text/plain")
                ) { uri ->
                    if (uri != null) {
                        val file = viewModel.buildExportLogsFile()
                        contentResolver.openOutputStream(uri)?.use { out ->
                            file.inputStream().use { it.copyTo(out) }
                        }
                    }
                }

                val exportConfigLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    if (uri != null) {
                        val payload = viewModel.settingsRepository.exportToJson(settings)
                        contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(payload.toByteArray())
                        }
                    }
                }

                val importConfigLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        if (raw != null) {
                            scope.launch {
                                viewModel.settingsRepository.importFromJson(raw)
                            }
                        }
                    }
                }

                LaunchedEffect(route, settings.immersiveMode, settings.appThemeId) {
                    val isLight = AppUiThemes.byId(settings.appThemeId).isLight
                    applyImmersiveMode(
                        enabled = (route == AppRoute.TERMINAL && settings.immersiveMode),
                        lightSystemBars = (route != AppRoute.TERMINAL && isLight)
                    )
                }

                LaunchedEffect(route) {
                    if (route == AppRoute.TERMINAL && viewModel.sessionManager.activeTerminalSession == null) {
                        viewModel.launchSelectedDistro()
                    }
                }

                LaunchedEffect(installState) {
                    if (route == AppRoute.ONBOARDING && installState is com.termlinux.distro.InstallState.Ready) {
                        route = AppRoute.TERMINAL
                    }
                }

                when (route) {
                    AppRoute.ONBOARDING -> {
                        OnboardingScreen(
                            distros = viewModel.manifest.distros,
                            selectedDistroId = settings.selectedDistroId,
                            installState = installState,
                            storageStatus = storageStatus,
                            hasInterruptedInstall = { viewModel.distroManager.hasInterruptedInstall(it) },
                            isDistroInstalled = { viewModel.isSelectedDistroInstalled(it) },
                            onSelectDistro = { viewModel.selectDistro(it) },
                            onRequestAllFilesAccess = {
                                viewModel.storageBridge.buildManageAllFilesIntent()?.let {
                                    allFilesLauncher.launch(it)
                                }
                            },
                            onPickSafFolder = { safTreeLauncher.launch(null) },
                            onInstallDistro = { distro ->
                                viewModel.startInstall(distro)
                            },
                            onCancelInstall = { viewModel.cancelInstall() },
                            onOpenTerminal = {
                                viewModel.launchSelectedDistro()
                                route = AppRoute.TERMINAL
                            }
                        )
                    }

                    AppRoute.TERMINAL -> {
                        TerminalScreen(
                            settings = settings,
                            distros = viewModel.manifest.distros,
                            isDistroInstalled = { viewModel.isSelectedDistroInstalled(it) },
                            sessionState = sessionState,
                            sessions = sessions,
                            activeSessionId = activeSessionId,
                            sessionManager = viewModel.sessionManager,
                            activeSession = viewModel.sessionManager.activeTerminalSession,
                            onOpenSettings = { route = AppRoute.SETTINGS },
                            onOpenDesktop = { route = AppRoute.DESKTOP },
                            onNewSession = { viewModel.launchSelectedDistro(forceNewTab = true) },
                            onLaunchDistroSession = { distro ->
                                viewModel.launchDistroSession(distro, forceNewTab = true)
                            },
                            onInstallDistro = { distro ->
                                viewModel.startInstall(distro)
                                route = AppRoute.ONBOARDING
                            },
                            onSwitchSession = { id -> viewModel.switchSession(id) },
                            onCloseSession = { id -> viewModel.closeSession(id) },
                            onRestartSession = { viewModel.restartSession() },
                            onToggleWakeLock = { enabled -> viewModel.setWakeLockEnabled(enabled) },
                            onFontSizeScaled = { newSize ->
                                scope.launch { viewModel.settingsRepository.setFontSizeSp(newSize) }
                            }
                        )
                    }

                    AppRoute.SETTINGS -> {
                        SettingsScreen(
                            settings = settings,
                            distros = viewModel.manifest.distros,
                            isDistroInstalled = { viewModel.isSelectedDistroInstalled(it) },
                            sessionState = sessionState,
                            storageStatus = storageStatus,
                            safEntries = safEntries,
                            diagnosticsText = diagnosticsText,
                            onBackToTerminal = { route = AppRoute.TERMINAL },
                            onSelectDistro = { id ->
                                val distro = viewModel.manifest.distros.firstOrNull { it.id == id }
                                if (distro != null) {
                                    viewModel.selectDistro(id)
                                    viewModel.launchDistroSession(distro, forceNewTab = true)
                                    route = AppRoute.TERMINAL
                                }
                            },
                            onReinstallDistro = { distro ->
                                viewModel.startInstall(distro, forceReinstall = true)
                                route = AppRoute.ONBOARDING
                            },
                            onRestartSession = { viewModel.restartSession() },
                            onStopSession = { viewModel.sessionManager.stopSession() },
                            onFontSizeChange = { size -> scope.launch { viewModel.settingsRepository.setFontSizeSp(size) } },
                            onThemeChange = { theme -> scope.launch { viewModel.settingsRepository.setThemeId(theme) } },
                            onAppThemeChange = { appTheme -> scope.launch { viewModel.settingsRepository.setAppThemeId(appTheme) } },
                            onFontFamilyChange = { font -> scope.launch { viewModel.settingsRepository.setFontFamilyId(font) } },
                            onScrollbackChange = { lines -> scope.launch { viewModel.settingsRepository.setScrollbackLines(lines) } },
                            onExtraKeysToggle = { show -> scope.launch { viewModel.settingsRepository.setShowExtraKeys(show) } },
                            onExtraKeysPresetChange = { preset -> scope.launch { viewModel.settingsRepository.setExtraKeysPreset(preset) } },
                            onImmersiveToggle = { imm -> scope.launch { viewModel.settingsRepository.setImmersiveMode(imm) } },
                            onWakeLockToggle = { wl -> viewModel.setWakeLockEnabled(wl) },
                            onAudioBridgeToggle = { ab -> viewModel.setAudioBridgeEnabled(ab) },
                            onBootstrapCreateUserToggle = { v -> scope.launch { viewModel.settingsRepository.setBootstrapCreateUser(v) } },
                            onBootstrapUsernameChange = { u -> scope.launch { viewModel.settingsRepository.setBootstrapUsername(u) } },
                            onBootstrapZshToggle = { z -> scope.launch { viewModel.settingsRepository.setBootstrapZshPreset(z) } },
                            onOpenDesktop = { route = AppRoute.DESKTOP },
                            onRequestAllFilesAccess = {
                                viewModel.storageBridge.buildManageAllFilesIntent()?.let {
                                    allFilesLauncher.launch(it)
                                }
                            },
                            onPickSafFolder = { safTreeLauncher.launch(null) },
                            onRunSafTest = { viewModel.runSafWriteTest { safTestResult = it } },
                            onRefreshDiagnostics = { viewModel.refreshDiagnostics() },
                            onExportLogs = { exportLogsLauncher.launch("termlinux-diagnostics.txt") },
                            onExportConfig = { exportConfigLauncher.launch("termlinux-config.json") },
                            onImportConfig = { importConfigLauncher.launch(arrayOf("application/json")) },
                            safTestResult = safTestResult
                        )
                    }

                    AppRoute.DESKTOP -> {
                        DesktopScreen(
                            onBackToTerminal = { route = AppRoute.TERMINAL }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.sessionManager.onActivityForegrounded()
        viewModel.refreshStorageStatus()
    }

    override fun onStop() {
        super.onStop()
        viewModel.sessionManager.onActivityBackgrounded()
    }

    private fun applyImmersiveMode(enabled: Boolean, lightSystemBars: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, !enabled)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = lightSystemBars
        controller.isAppearanceLightNavigationBars = lightSystemBars
        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
