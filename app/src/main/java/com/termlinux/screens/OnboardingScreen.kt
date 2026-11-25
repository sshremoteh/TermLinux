package com.termlinux.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.termlinux.distro.DistroEntry
import com.termlinux.distro.InstallState
import com.termlinux.storage.StorageAccessMode
import com.termlinux.storage.StorageStatus

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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "TermLinux",
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Standalone ARM64 Linux environment via PRoot. No root, Termux, or SSH required.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Step 1: Distro selection
            Text(
                text = "1. Choose Linux Distribution (${distros.size} available)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search distro or package manager (apt, pacman, dnf, apk)...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            filteredDistros.forEach { distro ->
                val selected = distro.id == selectedDistro.id
                val installed = isDistroInstalled(distro.id)
                val interrupted = hasInterruptedInstall(distro.id)
                Card(
                    onClick = { if (!isInstalling) onSelectDistro(distro.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = distro.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            val badge = when {
                                installed -> "Installed"
                                interrupted -> "Incomplete"
                                else -> "${distro.approxSizeBytes / (1024 * 1024)} MB"
                            }
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (installed) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = distro.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Step 2: Storage access (/sdcard)
            Text(
                text = "2. Android Storage Access (/sdcard)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = storageStatus.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (storageStatus.mode == StorageAccessMode.DIRECT_SHARED_STORAGE) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = "Without All Files Access, Linux cannot freely read or modify /sdcard. " +
                            "If you prefer scoped access, pick a single folder via SAF (exposed as a limited bridge, not full POSIX /sdcard). You can also skip this for now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                }
            }

            // Step 3: Install progress & action
            when (installState) {
                is InstallState.Downloading -> {
                    val total = installState.progress.totalBytes
                    val read = installState.progress.bytesRead
                    val fraction = if (total > 0) (read.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
                    Text(
                        text = "Downloading ${installState.distro.name}: ${read / (1024 * 1024)} MB" +
                            if (total > 0) " / ${total / (1024 * 1024)} MB" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (fraction != null) {
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                is InstallState.Verifying -> {
                    Text("Verifying SHA-256 checksum...", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is InstallState.Extracting -> {
                    Text(
                        "Extracting rootfs (${installState.entriesProcessed} entries)...",
                        style = MaterialTheme.typography.bodySmall
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is InstallState.Configuring -> {
                    Text("Configuring DNS, shell, and Storage Bridge...", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is InstallState.Failed -> {
                    Text(
                        text = "Error: ${installState.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                is InstallState.Interrupted -> {
                    Text(
                        text = "Installation paused/interrupted. You can safely resume.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                else -> Unit
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isInstalling) {
                    OutlinedButton(
                        onClick = onCancelInstall,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                } else if (isDistroInstalled(selectedDistro.id)) {
                    Button(
                        onClick = onOpenTerminal,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open Terminal")
                    }
                } else {
                    val label = if (hasInterruptedInstall(selectedDistro.id)) {
                        "Resume ${selectedDistro.name} Install"
                    } else {
                        "Install ${selectedDistro.name}"
                    }
                    Button(
                        onClick = { onInstallDistro(selectedDistro) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label)
                    }
                }
            }

            Text(
                text = "Note: PRoot runs Linux userspace without root. Kernel-only features (systemd, Docker, kernel modules) are not supported.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
