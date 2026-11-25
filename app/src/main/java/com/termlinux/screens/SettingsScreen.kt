package com.termlinux.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.termlinux.distro.DistroEntry
import com.termlinux.session.SessionState
import com.termlinux.settings.AppSettings
import com.termlinux.storage.SafEntry
import com.termlinux.storage.StorageStatus
import com.termlinux.ui.theme.AppUiThemes
import com.termlinux.ui.theme.TerminalThemes

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "TermLinux Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    navigationIcon = {
                        TextButton(onClick = onBackToTerminal) {
                            Text("← Terminal")
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Session section
            SectionCard("Linux Session & Power") {
                val statusText = when (sessionState) {
                    is SessionState.Running -> "Running: ${sessionState.distroName} (PID ${sessionState.pid})"
                    is SessionState.Paused -> "Paused in background: ${sessionState.distroName} (PID ${sessionState.pid})"
                    is SessionState.Starting -> "Starting ${sessionState.distroName}..."
                    is SessionState.Exited -> "Exited with code ${sessionState.exitCode}"
                    is SessionState.ProcessLost -> "Lost: ${sessionState.reason}"
                    is SessionState.Failed -> "Failed: ${sessionState.reason}"
                    SessionState.Idle -> "Idle (no active session)"
                }
                Text(statusText, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRestartSession) { Text("Restart Session") }
                    OutlinedButton(onClick = onStopSession) { Text("Stop") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CPU WakeLock", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Keep CPU active when screen is off during long builds/upgrades",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = settings.wakeLockEnabled, onCheckedChange = onWakeLockToggle)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Audio Bridge (127.0.0.1:14713)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Stream Linux PulseAudio TCP PCM to Android speakers",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = settings.audioBridgeEnabled, onCheckedChange = onAudioBridgeToggle)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenDesktop, shape = RoundedCornerShape(8.dp)) {
                        Text("Open GUI Desktop (VNC)")
                    }
                }
            }

            // App & Terminal Themes
            SectionCard("App & Environment Themes") {
                Text("App UI Theme", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppUiThemes.all.forEach { t ->
                        if (t.id == settings.appThemeId) {
                            Button(onClick = { onAppThemeChange(t.id) }) { Text(t.label) }
                        } else {
                            OutlinedButton(onClick = { onAppThemeChange(t.id) }) { Text(t.label) }
                        }
                    }
                }

                Text("Terminal Color Scheme", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TerminalThemes.all.forEach { t ->
                        val selected = t.id == settings.themeId
                        if (selected) {
                            Button(onClick = { onThemeChange(t.id) }) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(t.cursor))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(t.label)
                            }
                        } else {
                            OutlinedButton(onClick = { onThemeChange(t.id) }) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(t.background))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(t.label)
                            }
                        }
                    }
                }
            }

            // Terminal appearance & input
            SectionCard("Terminal & Input") {
                Text("Terminal Font", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isNerd = settings.fontFamilyId == "nerd"
                    if (isNerd) {
                        Button(onClick = { onFontFamilyChange("nerd") }, shape = RoundedCornerShape(8.dp)) {
                            Text("JetBrainsMono Nerd")
                        }
                        OutlinedButton(onClick = { onFontFamilyChange("monospace") }, shape = RoundedCornerShape(8.dp)) {
                            Text("System Mono")
                        }
                    } else {
                        OutlinedButton(onClick = { onFontFamilyChange("nerd") }, shape = RoundedCornerShape(8.dp)) {
                            Text("JetBrainsMono Nerd")
                        }
                        Button(onClick = { onFontFamilyChange("monospace") }, shape = RoundedCornerShape(8.dp)) {
                            Text("System Mono")
                        }
                    }
                }

                Text("Font Size: ${settings.fontSizeSp} sp (or pinch-to-zoom)", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = settings.fontSizeSp.toFloat(),
                    onValueChange = { onFontSizeChange(it.toInt()) },
                    valueRange = 8f..28f,
                    steps = 19
                )

                Text("Scrollback: ${settings.scrollbackLines} lines", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = settings.scrollbackLines.toFloat(),
                    onValueChange = { onScrollbackChange((it.toInt() / 1000) * 1000) },
                    valueRange = 2000f..50000f,
                    steps = 11
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Extra Keys Bar", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = settings.showExtraKeys, onCheckedChange = onExtraKeysToggle)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val std = settings.extraKeysPreset == "standard"
                    if (std) {
                        Button(onClick = { onExtraKeysPresetChange("standard") }) { Text("Standard Keys") }
                        OutlinedButton(onClick = { onExtraKeysPresetChange("dev") }) { Text("Dev Keys") }
                    } else {
                        OutlinedButton(onClick = { onExtraKeysPresetChange("standard") }) { Text("Standard Keys") }
                        Button(onClick = { onExtraKeysPresetChange("dev") }) { Text("Dev Keys") }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Immersive Fullscreen", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = settings.immersiveMode, onCheckedChange = onImmersiveToggle)
                }
            }

            // Optional tl-bootstrap presets
            SectionCard("Bootstrap Presets (Optional)") {
                Text(
                    "Applied when you run 'tl-bootstrap' inside Linux (restart session after changing).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Create non-root sudo user", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = settings.bootstrapCreateUser, onCheckedChange = onBootstrapCreateUserToggle)
                }
                if (settings.bootstrapCreateUser) {
                    OutlinedTextField(
                        value = settings.bootstrapUsername,
                        onValueChange = onBootstrapUsernameChange,
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Zsh + Git Prompt preset", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = settings.bootstrapZshPreset, onCheckedChange = onBootstrapZshToggle)
                }
            }

            // Distribution catalog
            SectionCard("Distributions (${distros.size})") {
                OutlinedTextField(
                    value = distroSearch,
                    onValueChange = { distroSearch = it },
                    placeholder = { Text("Filter distros (Debian, Arch, Kali, Alpine...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                filteredDistros.forEach { distro ->
                    val installed = isDistroInstalled(distro.id)
                    val selected = distro.id == settings.selectedDistroId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${distro.name}${if (selected) " (Active)" else ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = "${if (installed) "Installed" else "${distro.approxSizeBytes / (1024 * 1024)} MB"} • ${distro.packageManager}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (installed) {
                                TextButton(onClick = { onSelectDistro(distro.id) }) { Text("Launch") }
                            }
                            OutlinedButton(onClick = {
                                if (installed) confirmReinstallDistro = distro else onReinstallDistro(distro)
                            }) {
                                Text(if (installed) "Reinstall" else "Install")
                            }
                        }
                    }
                }
            }

            // Storage (/sdcard) section
            SectionCard("Storage Access (/sdcard)") {
                Text(storageStatus.summary, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onRequestAllFilesAccess,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("All Files Access")
                    }
                    OutlinedButton(
                        onClick = onPickSafFolder,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pick SAF Folder")
                    }
                }
                if (storageStatus.safPermissionValid) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onRunSafTest) {
                            Text("Test SAF Read/Write")
                        }
                    }
                    if (!safTestResult.isNullOrBlank()) {
                        Text(safTestResult, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (safEntries.isNotEmpty()) {
                        Text(
                            "SAF Folder Preview (${safEntries.size} items):",
                            style = MaterialTheme.typography.labelMedium
                        )
                        safEntries.take(8).forEach { item ->
                            Text(
                                text = "${if (item.isDirectory) "[DIR]" else "[FILE]"} ${item.name}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Config export/import & diagnostics
            SectionCard("Diagnostics & Config") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRefreshDiagnostics) { Text("Refresh") }
                    OutlinedButton(onClick = onExportLogs) { Text("Export Logs") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExportConfig) { Text("Export Config (SAF)") }
                    OutlinedButton(onClick = onImportConfig) { Text("Import Config (SAF)") }
                }
                Text(
                    text = diagnosticsText,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Limitations & Licenses
            SectionCard("Android/PRoot Limitations & Licenses") {
                Text(
                    text = "- PRoot emulates chroot/bind-mount in userspace via ptrace; systemd, Docker, raw sockets, and kernel modules are not available.\n" +
                        "- Android 10–16 scoped storage restricts /sdcard unless All Files Access (MANAGE_EXTERNAL_STORAGE) is granted.\n" +
                        "- SAF folder access uses ContentResolver and cannot be POSIX bind-mounted.\n" +
                        "- Components: termux/proot (GPL-2.0-or-later), termux/terminal-emulator & terminal-view (Apache-2.0), libtalloc (LGPL-3.0-or-later).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    confirmReinstallDistro?.let { distro ->
        AlertDialog(
            onDismissRequest = { confirmReinstallDistro = null },
            title = { Text("Reinstall ${distro.name}?") },
            text = {
                Text("This will re-download and extract ${distro.name} while preserving /root and /home. Active sessions should be stopped first.")
            },
            confirmButton = {
                Button(onClick = {
                    val target = distro
                    confirmReinstallDistro = null
                    onReinstallDistro(target)
                }) {
                    Text("Reinstall")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReinstallDistro = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}
