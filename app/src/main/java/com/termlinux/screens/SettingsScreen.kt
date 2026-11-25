package com.termlinux.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termlinux.distro.DistroEntry
import com.termlinux.session.SessionState
import com.termlinux.settings.AppSettings
import com.termlinux.storage.SafEntry
import com.termlinux.storage.StorageStatus
import com.termlinux.ui.components.TuiButton
import com.termlinux.ui.components.TuiDialog
import com.termlinux.ui.components.TuiDivider
import com.termlinux.ui.components.TuiPanel
import com.termlinux.ui.components.TuiSlider
import com.termlinux.ui.components.TuiText
import com.termlinux.ui.components.TuiTextInput
import com.termlinux.ui.components.TuiToggleRow
import com.termlinux.ui.theme.AppUiThemes
import com.termlinux.ui.theme.LocalTuiPalette
import com.termlinux.ui.theme.TerminalThemes

@Composable
fun SettingsScreen(
    settings: AppSettings,
    distros: List<DistroEntry>,
    isDistroInstalled: (String) -> Boolean,
    sessionState: SessionState,
    storageStatus: StorageStatus,
    safEntries: List<SafEntry>,
    diagnosticsText: String,
    onBackToTerminal: () -> Unit,
    onSelectDistro: (String) -> Unit,
    onReinstallDistro: (DistroEntry) -> Unit,
    onRestartSession: () -> Unit,
    onStopSession: () -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onThemeChange: (String) -> Unit,
    onAppThemeChange: (String) -> Unit,
    onFontFamilyChange: (String) -> Unit,
    onScrollbackChange: (Int) -> Unit,
    onExtraKeysToggle: (Boolean) -> Unit,
    onExtraKeysPresetChange: (String) -> Unit,
    onImmersiveToggle: (Boolean) -> Unit,
    onWakeLockToggle: (Boolean) -> Unit,
    onAudioBridgeToggle: (Boolean) -> Unit,
    onBootstrapCreateUserToggle: (Boolean) -> Unit,
    onBootstrapUsernameChange: (String) -> Unit,
    onBootstrapZshToggle: (Boolean) -> Unit,
    onOpenDesktop: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onPickSafFolder: () -> Unit,
    onRunSafTest: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onExportLogs: () -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    safTestResult: String?
) {
    val p = LocalTuiPalette.current
    var confirmReinstallDistro by remember { mutableStateOf<DistroEntry?>(null) }
    var distroSearch by remember { mutableStateOf("") }

    val filteredDistros = remember(distros, distroSearch) {
        if (distroSearch.isBlank()) distros
        else distros.filter {
            it.name.contains(distroSearch, ignoreCase = true) ||
                it.packageManager.contains(distroSearch, ignoreCase = true) ||
                it.id.contains(distroSearch, ignoreCase = true)
        }
    }

    LaunchedEffect(Unit) {
        onRefreshDiagnostics()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Pure TUI top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(p.panelAlt)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TuiButton(label = "<- TERMINAL", onClick = onBackToTerminal, filled = true)
            TuiText(
                text = "TERMLINUX :: CONFIG",
                color = p.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        TuiDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TuiPanel("SESSION & POWER") {
                val statusText = when (sessionState) {
                    is SessionState.Running -> "RUNNING :: ${sessionState.distroName} (pid ${sessionState.pid})"
                    is SessionState.Paused -> "PAUSED :: ${sessionState.distroName} (pid ${sessionState.pid})"
                    is SessionState.Starting -> "STARTING :: ${sessionState.distroName}..."
                    is SessionState.Exited -> "EXITED :: code ${sessionState.exitCode}"
                    is SessionState.ProcessLost -> "LOST :: ${sessionState.reason}"
                    is SessionState.Failed -> "FAILED :: ${sessionState.reason}"
                    SessionState.Idle -> "IDLE :: no active session"
                }
                TuiText(text = statusText, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TuiButton("RESTART", onClick = onRestartSession, filled = true)
                    TuiButton("STOP", onClick = onStopSession, danger = true)
                    TuiButton("GUI (VNC)", onClick = onOpenDesktop)
                }
                TuiToggleRow(
                    label = "CPU WakeLock",
                    subtitle = "Prevent CPU sleep during builds/upgrades",
                    checked = settings.wakeLockEnabled,
                    onToggle = onWakeLockToggle
                )
                TuiToggleRow(
                    label = "Audio Bridge (127.0.0.1:14713)",
                    subtitle = "Stream PulseAudio TCP PCM to Android AudioTrack",
                    checked = settings.audioBridgeEnabled,
                    onToggle = onAudioBridgeToggle
                )
            }

            TuiPanel("APP & TERMINAL THEMES") {
                TuiText(text = "App UI Palette:", color = p.textMuted, fontSize = 11.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppUiThemes.all.forEach { t ->
                        TuiButton(
                            label = t.label,
                            onClick = { onAppThemeChange(t.id) },
                            filled = (t.id == settings.appThemeId)
                        )
                    }
                }

                TuiText(text = "Terminal Color Scheme:", color = p.textMuted, fontSize = 11.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TerminalThemes.all.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(t.background))
                                    .border(1.dp, Color(t.cursor))
                            )
                            Spacer(Modifier.width(4.dp))
                            TuiButton(
                                label = t.label,
                                onClick = { onThemeChange(t.id) },
                                filled = (t.id == settings.themeId)
                            )
                        }
                    }
                }
            }

            TuiPanel("TERMINAL & INPUT") {
                TuiText(text = "Font Family:", color = p.textMuted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TuiButton(
                        label = "JetBrainsMono Nerd",
                        onClick = { onFontFamilyChange("nerd") },
                        filled = (settings.fontFamilyId == "nerd")
                    )
                    TuiButton(
                        label = "System Mono",
                        onClick = { onFontFamilyChange("monospace") },
                        filled = (settings.fontFamilyId != "nerd")
                    )
                }

                TuiSlider(
                    label = "Font Size (or pinch-to-zoom)",
                    valueText = "${settings.fontSizeSp} sp",
                    fraction = (settings.fontSizeSp - 8) / 20f,
                    onFractionChange = { f -> onFontSizeChange((8 + f * 20).toInt()) }
                )

                TuiSlider(
                    label = "Scrollback Lines",
                    valueText = "${settings.scrollbackLines}",
                    fraction = (settings.scrollbackLines - 2000) / 48000f,
                    onFractionChange = { f ->
                        val v = ((2000 + f * 48000).toInt() / 1000) * 1000
                        onScrollbackChange(v.coerceIn(2000, 50000))
                    }
                )

                TuiToggleRow(
                    label = "Extra Keys Bar",
                    checked = settings.showExtraKeys,
                    onToggle = onExtraKeysToggle
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TuiButton(
                        label = "PRESET: STANDARD",
                        onClick = { onExtraKeysPresetChange("standard") },
                        filled = (settings.extraKeysPreset == "standard")
                    )
                    TuiButton(
                        label = "PRESET: DEV",
                        onClick = { onExtraKeysPresetChange("dev") },
                        filled = (settings.extraKeysPreset == "dev")
                    )
                }

                TuiToggleRow(
                    label = "Immersive Fullscreen",
                    checked = settings.immersiveMode,
                    onToggle = onImmersiveToggle
                )
            }

            TuiPanel("TL-BOOTSTRAP PRESETS (OPTIONAL)") {
                TuiText(
                    text = "Applied when running 'tl-bootstrap' in Linux (restart session after change).",
                    color = p.textMuted,
                    fontSize = 11.sp
                )
                TuiToggleRow(
                    label = "Create non-root sudo user",
                    checked = settings.bootstrapCreateUser,
                    onToggle = onBootstrapCreateUserToggle
                )
                if (settings.bootstrapCreateUser) {
                    TuiTextInput(
                        value = settings.bootstrapUsername,
                        onValueChange = onBootstrapUsernameChange,
                        placeholder = "username (default: linux)"
                    )
                }
                TuiToggleRow(
                    label = "Zsh + Git Prompt preset",
                    checked = settings.bootstrapZshPreset,
                    onToggle = onBootstrapZshToggle
                )
            }

            TuiPanel("DISTRIBUTIONS (${distros.size})") {
                TuiTextInput(
                    value = distroSearch,
                    onValueChange = { distroSearch = it },
                    placeholder = "filter distros (debian, arch, kali, alpine)..."
                )
                filteredDistros.forEach { distro ->
                    val installed = isDistroInstalled(distro.id)
                    val selected = distro.id == settings.selectedDistroId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(p.bg)
                            .border(1.dp, if (selected) p.accent else p.border)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                            TuiText(
                                text = "${distro.name}${if (selected) " *" else ""}",
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                            TuiText(
                                text = "${if (installed) "installed" else "${distro.approxSizeBytes / (1024 * 1024)} MB"} :: ${distro.packageManager}",
                                color = p.textMuted,
                                fontSize = 10.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (installed) {
                                TuiButton("LAUNCH", onClick = { onSelectDistro(distro.id) }, filled = true)
                            }
                            TuiButton(
                                label = if (installed) "REINSTALL" else "INSTALL",
                                onClick = {
                                    if (installed) confirmReinstallDistro = distro else onReinstallDistro(distro)
                                }
                            )
                        }
                    }
                }
            }

            TuiPanel("STORAGE ACCESS (/SDCARD)") {
                TuiText(text = storageStatus.summary, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TuiButton("ALL FILES ACCESS", onClick = onRequestAllFilesAccess, modifier = Modifier.weight(1f))
                    TuiButton("PICK SAF FOLDER", onClick = onPickSafFolder, modifier = Modifier.weight(1f))
                }
                if (storageStatus.safPermissionValid) {
                    TuiButton("TEST SAF READ/WRITE", onClick = onRunSafTest)
                    if (!safTestResult.isNullOrBlank()) {
                        TuiText(text = safTestResult, color = p.accent, fontSize = 11.sp)
                    }
                    safEntries.take(8).forEach { item ->
                        TuiText(
                            text = "${if (item.isDirectory) "[DIR] " else "[FILE]"} ${item.name}",
                            color = p.textMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            TuiPanel("DIAGNOSTICS & CONFIG") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TuiButton("REFRESH", onClick = onRefreshDiagnostics)
                    TuiButton("EXPORT LOGS", onClick = onExportLogs)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TuiButton("EXPORT CFG", onClick = onExportConfig)
                    TuiButton("IMPORT CFG", onClick = onImportConfig)
                }
                TuiText(text = diagnosticsText, color = p.textMuted, fontSize = 10.sp)
            }

            TuiPanel("LIMITATIONS & LICENSES") {
                TuiText(
                    text = "- PRoot runs in userspace via ptrace; systemd, Docker, raw sockets, and kernel modules are unsupported.\n" +
                        "- Scoped storage restricts /sdcard unless MANAGE_EXTERNAL_STORAGE is granted.\n" +
                        "- Licenses: termux/proot (GPL-2.0+), terminal-emulator/view (Apache-2.0), libtalloc (LGPL-3.0+).",
                    color = p.textMuted,
                    fontSize = 11.sp
                )
            }
        }
    }

    confirmReinstallDistro?.let { distro ->
        TuiDialog(
            title = "REINSTALL ${distro.name.uppercase()}?",
            onDismiss = { confirmReinstallDistro = null },
            footer = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TuiButton("CANCEL", onClick = { confirmReinstallDistro = null })
                    TuiButton(
                        label = "REINSTALL",
                        filled = true,
                        danger = true,
                        onClick = {
                            val target = distro
                            confirmReinstallDistro = null
                            onReinstallDistro(target)
                        }
                    )
                }
            }
        ) {
            TuiText(
                text = "Re-downloads and extracts ${distro.name} while preserving /root and /home.",
                fontSize = 12.sp
            )
        }
    }
}
