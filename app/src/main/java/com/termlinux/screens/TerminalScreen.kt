package com.termlinux.screens

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termlinux.distro.DistroEntry
import com.termlinux.session.ManagedSessionInfo
import com.termlinux.session.SessionManager
import com.termlinux.session.SessionState
import com.termlinux.settings.AppSettings
import com.termlinux.terminal.TerminalHostView
import com.termlinux.ui.theme.TerminalThemes
import kotlinx.coroutines.launch

@Composable
fun TerminalScreen(
    settings: AppSettings,
    distros: List<DistroEntry>,
    isDistroInstalled: (String) -> Boolean,
    sessionState: SessionState,
    sessions: List<ManagedSessionInfo>,
    activeSessionId: String?,
    sessionManager: SessionManager,
    activeSession: TerminalSession?,
    onOpenSettings: () -> Unit,
    onOpenDesktop: () -> Unit,
    onNewSession: () -> Unit,
    onLaunchDistroSession: (DistroEntry) -> Unit,
    onInstallDistro: (DistroEntry) -> Unit,
    onSwitchSession: (String) -> Unit,
    onCloseSession: (String) -> Unit,
    onRestartSession: () -> Unit,
    onToggleWakeLock: (Boolean) -> Unit,
    onFontSizeScaled: (Int) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showDistroPicker by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showUrlsDialog by remember { mutableStateOf(false) }
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    var hostViewRef by remember { mutableStateOf<TerminalHostView?>(null) }

    val theme = remember(settings.themeId) { TerminalThemes.byId(settings.themeId) }

    DisposableEffect(sessionManager) {
        onDispose {
            sessionManager.attachUiClient(null)
        }
    }

    LaunchedEffect(hostViewRef, activeSession, activeSessionId) {
        val host = hostViewRef ?: return@LaunchedEffect
        sessionManager.attachUiClient(host.sessionClient)
        host.attachSession(activeSession)
        host.applyAppearance(settings.fontSizeSp, theme, settings.fontFamilyId)
    }

    LaunchedEffect(hostViewRef, settings.fontSizeSp, theme, settings.fontFamilyId) {
        hostViewRef?.applyAppearance(settings.fontSizeSp, theme, settings.fontFamilyId)
    }

    LaunchedEffect(hostViewRef, ctrlActive, altActive) {
        hostViewRef?.setModifiers(ctrlActive, altActive)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            TermuxDrawerSheet(
                sessions = sessions,
                activeSessionId = activeSessionId,
                wakeLockEnabled = settings.wakeLockEnabled,
                onSelectSession = { id ->
                    onSwitchSession(id)
                    scope.launch { drawerState.close() }
                },
                onCloseSession = onCloseSession,
                onNewSession = {
                    onNewSession()
                    scope.launch { drawerState.close() }
                },
                onOpenDistroPicker = {
                    scope.launch { drawerState.close() }
                    showDistroPicker = true
                },
                onToggleKeyboard = {
                    scope.launch { drawerState.close() }
                    hostViewRef?.toggleSoftKeyboard()
                },
                onPaste = {
                    scope.launch { drawerState.close() }
                    hostViewRef?.pasteFromClipboard()
                },
                onOpenSearch = {
                    scope.launch { drawerState.close() }
                    showSearchDialog = true
                },
                onOpenUrls = {
                    scope.launch { drawerState.close() }
                    showUrlsDialog = true
                },
                onToggleWakeLock = { onToggleWakeLock(!settings.wakeLockEnabled) },
                onRestartSession = {
                    onRestartSession()
                    scope.launch { drawerState.close() }
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                },
                onOpenDesktop = {
                    scope.launch { drawerState.close() }
                    onOpenDesktop()
                }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(theme.background))
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                when (sessionState) {
                    is SessionState.ProcessLost -> {
                        SessionStatusBanner(
                            message = sessionState.reason,
                            actionLabel = "Restart",
                            onAction = onRestartSession,
                            onSettings = onOpenSettings
                        )
                    }

                    is SessionState.Exited -> {
                        SessionStatusBanner(
                            message = "[Process completed (code ${sessionState.exitCode})]",
                            actionLabel = "Restart",
                            onAction = onRestartSession,
                            onSettings = onOpenSettings
                        )
                    }

                    is SessionState.Failed -> {
                        SessionStatusBanner(
                            message = "Failed: ${sessionState.reason}",
                            actionLabel = "Retry",
                            onAction = onRestartSession,
                            onSettings = onOpenSettings
                        )
                    }

                    else -> Unit
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            TerminalHostView(
                                context = ctx,
                                onThreeFingerTripleTap = {
                                    scope.launch {
                                        if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                    }
                                },
                                onFontSizeScaled = onFontSizeScaled,
                                onModifierConsumed = {
                                    ctrlActive = false
                                    altActive = false
                                }
                            ).also { host ->
                                hostViewRef = host
                                host.applyAppearance(settings.fontSizeSp, theme, settings.fontFamilyId)
                                sessionManager.attachUiClient(host.sessionClient)
                                host.attachSession(activeSession)
                            }
                        },
                        update = { host ->
                            hostViewRef = host
                            host.applyAppearance(settings.fontSizeSp, theme, settings.fontFamilyId)
                            host.attachSession(activeSession)
                        }
                    )
                }

                if (settings.showExtraKeys) {
                    TermuxExtraKeysGrid(
                        ctrlActive = ctrlActive,
                        altActive = altActive,
                        preset = settings.extraKeysPreset,
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        onToggleCtrl = { ctrlActive = !ctrlActive },
                        onToggleAlt = { altActive = !altActive },
                        onKeyCode = { code ->
                            hostViewRef?.sendSpecialKey(code)
                            if (ctrlActive) ctrlActive = false
                            if (altActive) altActive = false
                        },
                        onText = { text ->
                            hostViewRef?.sendText(text)
                            if (ctrlActive) ctrlActive = false
                            if (altActive) altActive = false
                        }
                    )
                }
            }
        }
    }

    if (showDistroPicker) {
        DistroSessionPickerDialog(
            distros = distros,
            isDistroInstalled = isDistroInstalled,
            onDismiss = { showDistroPicker = false },
            onLaunchDistro = { distro ->
                showDistroPicker = false
                onLaunchDistroSession(distro)
            },
            onInstallDistro = { distro ->
                showDistroPicker = false
                onInstallDistro(distro)
            }
        )
    }

    if (showSearchDialog) {
        var query by remember { mutableStateOf("") }
        val matches = remember(query) { hostViewRef?.searchTranscript(query).orEmpty() }
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search Terminal History", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Type text to search...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(modifier = Modifier.height(220.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(matches) { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) { Text("Close") }
            }
        )
    }

    if (showUrlsDialog) {
        val urls = remember { hostViewRef?.extractUrls().orEmpty() }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { showUrlsDialog = false },
            title = { Text("Detected URLs", style = MaterialTheme.typography.titleMedium) },
            text = {
                if (urls.isEmpty()) {
                    Text("No URLs found in terminal buffer.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(modifier = Modifier.height(220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(urls) { url ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        runCatching {
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                        showUrlsDialog = false
                                    }
                            ) {
                                Text(
                                    text = url,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUrlsDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun TermuxDrawerSheet(
    sessions: List<ManagedSessionInfo>,
    activeSessionId: String?,
    wakeLockEnabled: Boolean,
    onSelectSession: (String) -> Unit,
    onCloseSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onOpenDistroPicker: () -> Unit,
    onToggleKeyboard: () -> Unit,
    onPaste: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenUrls: () -> Unit,
    onToggleWakeLock: () -> Unit,
    onRestartSession: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDesktop: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TermLinux",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${sessions.size} session(s) • 3-finger 3x tap",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(2.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("CONFIG", style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, maxLines = 1)
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))

            Text(
                text = "[ SESSIONS ]",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions, key = { it.id }) { info ->
                    val isActive = info.id == activeSessionId
                    Surface(
                        color = if (isActive) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(2.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSession(info.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (info.isRunning) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error
                                        )
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = info.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (info.isRunning && info.pid > 0) {
                                            "${info.distroName} • PID ${info.pid}"
                                        } else {
                                            "${info.distroName} • exited"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = "×",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onCloseSession(info.id) }
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DrawerActionButton("+ Tab", onClick = onNewSession, filled = true, modifier = Modifier.weight(1f))
                DrawerActionButton("Distros", onClick = onOpenDistroPicker, filled = true, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DrawerActionButton("Keyboard", onClick = onToggleKeyboard, modifier = Modifier.weight(1f))
                DrawerActionButton("Paste", onClick = onPaste, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DrawerActionButton("Find", onClick = onOpenSearch, modifier = Modifier.weight(1f))
                DrawerActionButton("URLs", onClick = onOpenUrls, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DrawerActionButton(
                    label = if (wakeLockEnabled) "WakeLock: ON" else "WakeLock: OFF",
                    onClick = onToggleWakeLock,
                    filled = wakeLockEnabled,
                    modifier = Modifier.weight(1f)
                )
                DrawerActionButton("Desktop (GUI)", onClick = onOpenDesktop, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            DrawerActionButton("Restart Session", onClick = onRestartSession, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DrawerActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    val shape = RoundedCornerShape(2.dp)
    if (filled) {
        Button(
            onClick = onClick,
            shape = shape,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = modifier.height(36.dp)
        ) {
            Text(label, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = shape,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = modifier.height(36.dp)
        ) {
            Text(label, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SessionStatusBanner(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onSettings) { Text("Settings") }
                Button(onClick = onAction, shape = RoundedCornerShape(8.dp)) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun TermuxExtraKeysGrid(
    ctrlActive: Boolean,
    altActive: Boolean,
    preset: String,
    onOpenMenu: () -> Unit,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onKeyCode: (Int) -> Unit,
    onText: (String) -> Unit
) {
    val row1 = listOf("ESC", "/", "-", "HOME", "↑", "END", "PGUP", "☰")
    val row2 = if (preset == "dev") {
        listOf("TAB", "CTRL", "ALT", "←", "↓", "→", "|", "~")
    } else {
        listOf("TAB", "CTRL", "ALT", "←", "↓", "→", "PGDN", "~")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(horizontal = 3.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        ExtraKeyRow(row1, ctrlActive, altActive, onOpenMenu, onToggleCtrl, onToggleAlt, onKeyCode, onText)
        ExtraKeyRow(row2, ctrlActive, altActive, onOpenMenu, onToggleCtrl, onToggleAlt, onKeyCode, onText)
    }
}

@Composable
private fun ExtraKeyRow(
    labels: List<String>,
    ctrlActive: Boolean,
    altActive: Boolean,
    onOpenMenu: () -> Unit,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onKeyCode: (Int) -> Unit,
    onText: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        labels.forEach { label ->
            val isActive = (label == "CTRL" && ctrlActive) || (label == "ALT" && altActive)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        1.dp,
                        if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(2.dp)
                    )
                    .clickable {
                        when (label) {
                            "☰" -> onOpenMenu()
                            "ESC" -> onKeyCode(KeyEvent.KEYCODE_ESCAPE)
                            "TAB" -> onKeyCode(KeyEvent.KEYCODE_TAB)
                            "CTRL" -> onToggleCtrl()
                            "ALT" -> onToggleAlt()
                            "←" -> onKeyCode(KeyEvent.KEYCODE_DPAD_LEFT)
                            "↓" -> onKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
                            "↑" -> onKeyCode(KeyEvent.KEYCODE_DPAD_UP)
                            "→" -> onKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
                            "HOME" -> onKeyCode(KeyEvent.KEYCODE_MOVE_HOME)
                            "END" -> onKeyCode(KeyEvent.KEYCODE_MOVE_END)
                            "PGUP" -> onKeyCode(KeyEvent.KEYCODE_PAGE_UP)
                            "PGDN" -> onKeyCode(KeyEvent.KEYCODE_PAGE_DOWN)
                            else -> onText(label)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DistroSessionPickerDialog(
    distros: List<DistroEntry>,
    isDistroInstalled: (String) -> Boolean,
    onDismiss: () -> Unit,
    onLaunchDistro: (DistroEntry) -> Unit,
    onInstallDistro: (DistroEntry) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val sorted = remember(distros, query) {
        distros.filter {
            query.isBlank() ||
                it.name.contains(query, ignoreCase = true) ||
                it.packageManager.contains(query, ignoreCase = true) ||
                it.id.contains(query, ignoreCase = true)
        }.sortedByDescending { isDistroInstalled(it.id) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Launch Distro Session",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search distro or pkg manager...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(sorted, key = { it.id }) { distro ->
                        val installed = isDistroInstalled(distro.id)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (installed) onLaunchDistro(distro) else onInstallDistro(distro)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = distro.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (installed) {
                                            "Installed • ${distro.packageManager}"
                                        } else {
                                            "${distro.approxSizeBytes / (1024 * 1024)} MB • ${distro.packageManager}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = if (installed) "Launch" else "Install",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (installed) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
