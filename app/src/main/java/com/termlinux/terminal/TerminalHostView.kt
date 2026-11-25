package com.termlinux.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import com.termlinux.input.ThreeFingerTripleTapDetector
import com.termlinux.ui.theme.TerminalColorTheme
import kotlin.math.roundToInt

class TerminalHostView(
    context: Context,
    private val onThreeFingerTripleTap: () -> Unit,
    private val onFontSizeScaled: (Int) -> Unit = {},
    private val onModifierConsumed: () -> Unit = {}
) : FrameLayout(context) {

    val terminalView: TerminalView = TerminalView(context, null)
    private var ctrlHeld = false
    private var altHeld = false
    private var currentFontSizeSp = 13f
    private var scaleAccumulator = 1f

    private val tripleTapDetector = ThreeFingerTripleTapDetector(
        onTripleTap = onThreeFingerTripleTap
    )

    private val viewClient = object : TerminalViewClient {
        override fun onScale(scale: Float): Float {
            if (scale < 0.9f || scale > 1.1f) {
                scaleAccumulator *= scale
                val target = (currentFontSizeSp * scale).coerceIn(8f, 28f)
                val rounded = target.roundToInt()
                if (rounded != currentFontSizeSp.roundToInt()) {
                    currentFontSizeSp = rounded.toFloat()
                    applyFontSizePx(currentFontSizeSp)
                    onFontSizeScaled(rounded)
                }
                return 1f
            }
            return scale
        }

        override fun onSingleTapUp(e: MotionEvent?) {
            terminalView.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
        }

        override fun shouldBackButtonBeMappedToEscape(): Boolean = false

        override fun shouldEnforceCharBasedInput(): Boolean = true

        override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

        override fun isTerminalViewSelected(): Boolean = true

        override fun copyModeChanged(copyMode: Boolean) {}

        override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean {
            if (e != null && e.isCtrlPressed && e.isShiftPressed && keyCode == KeyEvent.KEYCODE_V) {
                pasteFromClipboard()
                return true
            }
            return false
        }

        override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean {
            if (ctrlHeld || altHeld) {
                val isModifierOnly = keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
                    keyCode == KeyEvent.KEYCODE_CTRL_RIGHT ||
                    keyCode == KeyEvent.KEYCODE_ALT_LEFT ||
                    keyCode == KeyEvent.KEYCODE_ALT_RIGHT ||
                    keyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
                    keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT
                if (!isModifierOnly) {
                    onModifierConsumed()
                }
            }
            return false
        }

        override fun onLongPress(event: MotionEvent?): Boolean = false

        override fun readControlKey(): Boolean = ctrlHeld

        override fun readAltKey(): Boolean = altHeld

        override fun readShiftKey(): Boolean = false

        override fun readFnKey(): Boolean = false

        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
            return false
        }

        override fun onEmulatorSet() {}

        override fun logError(tag: String?, message: String?) {}
        override fun logWarn(tag: String?, message: String?) {}
        override fun logInfo(tag: String?, message: String?) {}
        override fun logDebug(tag: String?, message: String?) {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
        override fun logStackTrace(tag: String?, e: Exception?) {}
    }

    val sessionClient: TerminalSessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            post { terminalView.onScreenUpdated() }
        }

        override fun onTitleChanged(changedSession: TerminalSession) {}

        override fun onSessionFinished(finishedSession: TerminalSession) {
            post { terminalView.onScreenUpdated() }
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
            runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("TermLinux", text))
            }
        }

        override fun onPasteTextFromClipboard(session: TerminalSession?) {
            runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = cm?.primaryClip
                val item = clip?.getItemAt(0)?.coerceToText(context)?.toString()
                if (!item.isNullOrEmpty()) {
                    session?.emulator?.paste(item)
                }
            }
        }

        override fun onBell(session: TerminalSession) {}
        override fun onColorsChanged(session: TerminalSession) {
            post { terminalView.onScreenUpdated() }
        }
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
        override fun getTerminalCursorStyle(): Int = 0
        override fun logError(tag: String?, message: String?) {}
        override fun logWarn(tag: String?, message: String?) {}
        override fun logInfo(tag: String?, message: String?) {}
        override fun logDebug(tag: String?, message: String?) {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
        override fun logStackTrace(tag: String?, e: Exception?) {}
    }

    init {
        terminalView.setTerminalViewClient(viewClient)
        applyFontSizePx(currentFontSizeSp)
        terminalView.setTypeface(Typeface.MONOSPACE)
        terminalView.isFocusable = true
        terminalView.isFocusableInTouchMode = true
        addView(
            terminalView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (tripleTapDetector.onTouchEvent(ev)) {
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    fun attachSession(session: TerminalSession?) {
        if (session != null && terminalView.currentSession !== session) {
            terminalView.attachSession(session)
        }
        terminalView.requestFocus()
    }

    fun applyAppearance(fontSizeSp: Int, theme: TerminalColorTheme) {
        currentFontSizeSp = fontSizeSp.toFloat()
        applyFontSizePx(currentFontSizeSp)

        TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_FOREGROUND] = theme.foreground
        TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND] = theme.background
        TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_CURSOR] = theme.cursor

        terminalView.currentSession?.emulator?.mColors?.let { colors ->
            colors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND] = theme.foreground
            colors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND] = theme.background
            colors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] = theme.cursor
        }

        setBackgroundColor(theme.background)
        terminalView.setBackgroundColor(theme.background)
        terminalView.onScreenUpdated()
    }

    private fun applyFontSizePx(sp: Float) {
        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            resources.displayMetrics
        ).toInt().coerceAtLeast(10)
        terminalView.setTextSize(px)
    }

    fun setModifiers(ctrl: Boolean, alt: Boolean) {
        ctrlHeld = ctrl
        altHeld = alt
    }

    fun sendSpecialKey(keyCode: Int) {
        val session = terminalView.currentSession ?: return
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            session.write(byteArrayOf(0x1b), 0, 1)
            return
        }
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            session.write(byteArrayOf(0x09), 0, 1)
            return
        }
        terminalView.onKeyDown(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    }

    fun sendText(text: String) {
        val session = terminalView.currentSession ?: return
        if (ctrlHeld && text.length == 1) {
            val ch = text[0].lowercaseChar()
            if (ch in 'a'..'z') {
                val ctrlByte = (ch.code - 'a'.code + 1).toByte()
                session.write(byteArrayOf(ctrlByte), 0, 1)
                return
            }
        }
        session.write(text)
    }

    fun toggleSoftKeyboard() {
        terminalView.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
    }

    fun pasteFromClipboard() {
        sessionClient.onPasteTextFromClipboard(terminalView.currentSession)
    }
}
