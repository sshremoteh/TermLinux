package com.termlinux.screens

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.termlinux.ui.components.TuiButton
import com.termlinux.ui.components.TuiDialog
import com.termlinux.ui.components.TuiDivider
import com.termlinux.ui.components.TuiText
import com.termlinux.ui.components.TuiTextInput
import com.termlinux.ui.theme.LocalTuiPalette
import com.termlinux.ui.theme.TerminalThemes

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
    val p = LocalTuiPalette.current
    var drawerOpen by remember { mutableStateOf(false) }
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
                        actionLabel = "RESTART",
                        onAction = onRestartSession,
                        onSettings = onOpenSettings
                    )
                }

                is SessionState.Exited -> {
                    SessionStatusBanner(
                        message = "[process exited: ${sessionState.exitCode}]",
                        actionLabel = "RESTART",
                        onAction = onRestartSession,
                        onSettings = onOpenSettings
                    )
                }

                is SessionState.Failed -> {
                    SessionStatusBanner(
                        message = "ERR: ${sessionState.reason}",
                        actionLabel = "RETRY",
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
                            onThreeFingerTripleTap = { drawerOpen = !drawerOpen },
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
                TuiExtraKeysGrid(
                    ctrlActive = ctrlActive,
                    altActive = altActive,
                    preset = settings.extraKeysPreset,
                    onOpenMenu = { drawerOpen = true },
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

        // Scrim when TUI drawer is open
        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { drawerOpen = false }
            )
        }

        // Sharp TUI Drawer (0dp edges, 1dp border)
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it }
        ) {
            TuiDrawerPanel(
                sessions = sessions,
                activeSessionId = activeSessionId,
                wakeLockEnabled = settings.wakeLockEnabled,
                onSelectSession = { id ->
                    onSwitchSession(id)
                    drawerOpen = false
                },
                onCloseSession = onCloseSession,
                onNewSession = {
                    onNewSession()
                    drawerOpen = false
                },
                onOpenDistroPicker = {
                    drawerOpen = false
                    showDistroPicker = true
                },
                onToggleKeyboard = {
                    drawerOpen = false
                    hostViewRef?.toggleSoftKeyboard()
                },
                onPaste = {
                    drawerOpen = false
                    hostViewRef?.pasteFromClipboard()
                },
                onOpenSearch = {
                    drawerOpen = false
                    showSearchDialog = true
                },
                onOpenUrls = {
                    drawerOpen = false
                    showUrlsDialog = true
                },
                onToggleWakeLock = { onToggleWakeLock(!settings.wakeLockEnabled) },
                onRestartSession = {
                    onRestartSession()
                    drawerOpen = false
                },
                onOpenSettings = {
                    drawerOpen = false
                    onOpenSettings()
                },
                onOpenDesktop = {
                    drawerOpen = false
                    onOpenDesktop()
                }
            )
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
        TuiDialog(
            title = "SEARCH SCROLLBACK",
            onDismiss = { showSearchDialog = false }
        ) {
            TuiTextInput(
                value = query,
                onValueChange = { query = it },
                placeholder = "grep pattern..."
            )
            LazyColumn(
                modifier = Modifier.height(220.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(matches) { line ->
                    TuiText(text = line, fontSize = 11.sp)
                }
            }
        }
    }

    if (showUrlsDialog) {
        val urls = remember { hostViewRef?.extractUrls().orEmpty() }
        val ctx = LocalContext.current
        TuiDialog(
            title = "DETECTED URLS",
            onDismiss = { showUrlsDialog = false }
        ) {
            if (urls.isEmpty()) {
                TuiText(text = "No URLs found in buffer.", color = p.textMuted, fontSize = 12.sp)
            } else {
                LazyColumn(
                    modifier = Modifier.height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(urls) { url ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(p.bg)
                                .border(1.dp, p.border)
                                .clickable {
                                    runCatching {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                    showUrlsDialog = false
                                }
                                .padding(8.dp)
                        ) {
                            TuiText(text = url, color = p.accent, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TuiDrawerPanel(
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
    val p = LocalTuiPalette.current
    Column(
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(p.panel)
            .border(1.dp, p.border)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                TuiText(text = "TERMLINUX", color = p.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                TuiText(
                    text = "${sessions.size} tty • 3-finger 3x tap",
                    color = p.textMuted,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(8.dp))
            TuiButton(label = "CONFIG", onClick = onOpenSettings, filled = true)
        }

        Spacer(Modifier.height(10.dp))
        TuiDivider()
        Spacer(Modifier.height(8.dp))

        TuiText(text = "[ SESSIONS ]", color = p.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(sessions, key = { it.id }) { info ->
                val isActive = info.id == activeSessionId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isActive) p.panelAlt else p.bg)
                        .border(1.dp, if (isActive) p.accent else p.border)
                        .clickable { onSelectSession(info.id) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        TuiText(
                            text = "${if (info.isRunning) "*" else "!"} ${info.title}",
                            color = if (isActive) p.accent else p.text,
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                        TuiText(
                            text = if (info.isRunning && info.pid > 0) {
                                "${info.distroName} :: pid ${info.pid}"
                            } else {
                                "${info.distroName} :: exited"
                            },
                            color = p.textMuted,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                    TuiText(
                        text = "[X]",
                        color = p.textMuted,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clickable { onCloseSession(info.id) }
                            .padding(start = 8.dp)
                    )
                }
            }
        }

        TuiDivider()
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TuiButton("+ TAB", onClick = onNewSession, filled = true, modifier = Modifier.weight(1f))
            TuiButton("DISTROS", onClick = onOpenDistroPicker, filled = true, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TuiButton("KEYS", onClick = onToggleKeyboard, modifier = Modifier.weight(1f))
            TuiButton("PASTE", onClick = onPaste, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TuiButton("FIND", onClick = onOpenSearch, modifier = Modifier.weight(1f))
            TuiButton("URLS", onClick = onOpenUrls, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TuiButton(
                label = if (wakeLockEnabled) "WAKELOCK:ON" else "WAKELOCK:OFF",
                onClick = onToggleWakeLock,
                filled = wakeLockEnabled,
                modifier = Modifier.weight(1f)
            )
            TuiButton("GUI (VNC)", onClick = onOpenDesktop, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        TuiButton("RESTART SESSION", onClick = onRestartSession, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SessionStatusBanner(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onSettings: () -> Unit
) {
    val p = LocalTuiPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.panelAlt)
            .border(1.dp, p.error)
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TuiText(
            text = message,
            color = p.error,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TuiButton("CONFIG", onClick = onSettings)
            TuiButton(actionLabel, onClick = onAction, filled = true)
        }
    }
}

@Composable
private fun TuiExtraKeysGrid(
    ctrlActive: Boolean,
    altActive: Boolean,
    preset: String,
    onOpenMenu: () -> Unit,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onKeyCode: (Int) -> Unit,
    onText: (String) -> Unit
) {
    val p = LocalTuiPalette.current
    val row1 = listOf("ESC", "/", "-", "HOME", "↑", "END", "PGUP", "☰")
    val row2 = if (preset == "dev") {
        listOf("TAB", "CTRL", "ALT", "←", "↓", "→", "|", "~")
    } else {
        listOf("TAB", "CTRL", "ALT", "←", "↓", "→", "PGDN", "~")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.bg)
            .navigationBarsPadding()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
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
    val p = LocalTuiPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        labels.forEach { label ->
            val isActive = (label == "CTRL" && ctrlActive) || (label == "ALT" && altActive)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .background(if (isActive) p.accent else p.panel)
                    .border(1.dp, if (isActive) p.accent else p.border)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
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
                TuiText(
                    text = label,
                    color = if (isActive) p.accentText else p.text,
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
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
    val p = LocalTuiPalette.current
    var query by remember { mutableStateOf("") }
    val sorted = remember(distros, query) {
        distros.filter {
            query.isBlank() ||
                it.name.contains(query, ignoreCase = true) ||
                it.packageManager.contains(query, ignoreCase = true) ||
                it.id.contains(query, ignoreCase = true)
        }.sortedByDescending { isDistroInstalled(it.id) }
    }

    TuiDialog(
        title = "LAUNCH DISTRO SESSION",
        onDismiss = onDismiss
    ) {
        TuiTextInput(
            value = query,
            onValueChange = { query = it },
            placeholder = "filter distro or pkg mgr..."
        )
        LazyColumn(
            modifier = Modifier.height(300.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(sorted, key = { it.id }) { distro ->
                val installed = isDistroInstalled(distro.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(p.bg)
                        .border(1.dp, if (installed) p.accent else p.border)
                        .clickable {
                            if (installed) onLaunchDistro(distro) else onInstallDistro(distro)
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        TuiText(text = distro.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        TuiText(
                            text = if (installed) "installed :: ${distro.packageManager}"
                            else "${distro.approxSizeBytes / (1024 * 1024)} MB :: ${distro.packageManager}",
                            color = p.textMuted,
                            fontSize = 10.sp
                        )
                    }
                    TuiText(
                        text = if (installed) "[RUN]" else "[GET]",
                        color = if (installed) p.accent else p.info,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
