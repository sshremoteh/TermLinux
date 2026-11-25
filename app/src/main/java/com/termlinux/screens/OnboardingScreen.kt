package com.termlinux.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termlinux.distro.DistroEntry
import com.termlinux.distro.InstallState
import com.termlinux.storage.StorageAccessMode
import com.termlinux.storage.StorageStatus
import com.termlinux.ui.components.TuiButton
import com.termlinux.ui.components.TuiDivider
import com.termlinux.ui.components.TuiPanel
import com.termlinux.ui.components.TuiProgressBar
import com.termlinux.ui.components.TuiText
import com.termlinux.ui.components.TuiTextInput
import com.termlinux.ui.theme.LocalTuiPalette

@Composable
fun OnboardingScreen(
    distros: List<DistroEntry>,
    selectedDistroId: String,
    installState: InstallState,
    storageStatus: StorageStatus,
    hasInterruptedInstall: (String) -> Boolean,
    isDistroInstalled: (String) -> Boolean,
    onSelectDistro: (String) -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onPickSafFolder: () -> Unit,
    onInstallDistro: (DistroEntry) -> Unit,
    onCancelInstall: () -> Unit,
    onOpenTerminal: () -> Unit
) {
    val p = LocalTuiPalette.current
    val selectedDistro = distros.firstOrNull { it.id == selectedDistroId } ?: distros.first()
    var searchQuery by remember { mutableStateOf("") }
    val filteredDistros = remember(distros, searchQuery) {
        if (searchQuery.isBlank()) distros
        else distros.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.packageManager.contains(searchQuery, ignoreCase = true) ||
                it.id.contains(searchQuery, ignoreCase = true)
        }
    }
    val isInstalling = installState is InstallState.Downloading ||
        installState is InstallState.Verifying ||
        installState is InstallState.Extracting ||
        installState is InstallState.Configuring

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(p.panelAlt)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TuiText(text = "TERMLINUX :: SETUP", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            TuiText(text = "ARM64 PROOT", color = p.textMuted, fontSize = 11.sp)
        }
        TuiDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TuiText(
                text = "Standalone ARM64 Linux userspace via PRoot. No root, Termux, or SSH required.",
                color = p.textMuted,
                fontSize = 12.sp
            )

            TuiPanel("1. DISTRIBUTION (${distros.size})") {
                TuiTextInput(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "filter distro or pkg mgr (apt, pacman, dnf, apk)..."
                )
                filteredDistros.forEach { distro ->
                    val selected = distro.id == selectedDistro.id
                    val installed = isDistroInstalled(distro.id)
                    val interrupted = hasInterruptedInstall(distro.id)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected) p.panelAlt else p.bg)
                            .border(1.dp, if (selected) p.accent else p.border)
                            .clickable { if (!isInstalling) onSelectDistro(distro.id) }
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TuiText(
                                text = "${if (selected) ">" else " "} ${distro.name}",
                                color = if (selected) p.accent else p.text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val badge = when {
                                installed -> "[INSTALLED]"
                                interrupted -> "[PARTIAL]"
                                else -> "${distro.approxSizeBytes / (1024 * 1024)} MB"
                            }
                            TuiText(
                                text = badge,
                                color = if (installed) p.accent else p.textMuted,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        TuiText(text = distro.description, color = p.textMuted, fontSize = 11.sp)
                    }
                }
            }

            TuiPanel("2. ANDROID STORAGE (/SDCARD)") {
                TuiText(
                    text = storageStatus.summary,
                    color = if (storageStatus.mode == StorageAccessMode.DIRECT_SHARED_STORAGE) p.accent else p.textMuted,
                    fontSize = 11.sp
                )
                TuiText(
                    text = "Without All Files Access, Linux cannot freely read/write /sdcard. SAF exposes only a selected folder via tlfiles.",
                    color = p.textMuted,
                    fontSize = 11.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TuiButton("ALL FILES ACCESS", onClick = onRequestAllFilesAccess, modifier = Modifier.weight(1f))
                    TuiButton("PICK SAF FOLDER", onClick = onPickSafFolder, modifier = Modifier.weight(1f))
                }
            }

            when (installState) {
                is InstallState.Downloading -> {
                    val total = installState.progress.totalBytes
                    val read = installState.progress.bytesRead
                    val fraction = if (total > 0) (read.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
                    TuiText(
                        text = "Downloading ${installState.distro.name}: ${read / (1024 * 1024)} MB" +
                            if (total > 0) " / ${total / (1024 * 1024)} MB" else "",
                        fontSize = 11.sp
                    )
                    TuiProgressBar(fraction = fraction)
                }

                is InstallState.Verifying -> {
                    TuiText("Verifying SHA-256 checksum...", fontSize = 11.sp)
                    TuiProgressBar(fraction = null)
                }

                is InstallState.Extracting -> {
                    TuiText("Extracting rootfs (${installState.entriesProcessed} entries)...", fontSize = 11.sp)
                    TuiProgressBar(fraction = null)
                }

                is InstallState.Configuring -> {
                    TuiText("Configuring DNS, shell, and bridges...", fontSize = 11.sp)
                    TuiProgressBar(fraction = null)
                }

                is InstallState.Failed -> {
                    TuiText("ERR: ${installState.error}", color = p.error, fontSize = 11.sp)
                }

                is InstallState.Interrupted -> {
                    TuiText("Install interrupted. Safe to resume.", color = p.info, fontSize = 11.sp)
                }

                else -> Unit
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isInstalling) {
                    TuiButton("CANCEL", onClick = onCancelInstall, danger = true, modifier = Modifier.weight(1f))
                } else if (isDistroInstalled(selectedDistro.id)) {
                    TuiButton("OPEN TERMINAL", onClick = onOpenTerminal, filled = true, modifier = Modifier.weight(1f))
                } else {
                    val label = if (hasInterruptedInstall(selectedDistro.id)) {
                        "RESUME ${selectedDistro.id.uppercase()}"
                    } else {
                        "INSTALL ${selectedDistro.id.uppercase()}"
                    }
                    TuiButton(label, onClick = { onInstallDistro(selectedDistro) }, filled = true, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
