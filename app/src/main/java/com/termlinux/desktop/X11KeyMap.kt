package com.termlinux.desktop

import android.view.KeyEvent

/**
 * Maps Android KeyEvent codes and Unicode characters to X11 KeySyms for RFB (VNC) KeyEvent messages.
 */
object X11KeyMap {
    fun toKeySym(event: KeyEvent): Int? = toKeySym(event.keyCode, event.unicodeChar)

    fun toKeySym(keyCode: Int, unicodeChar: Int): Int? {
        return when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> 0xFF0D
            KeyEvent.KEYCODE_TAB -> 0xFF09
            KeyEvent.KEYCODE_DEL -> 0xFF08 // BackSpace
            KeyEvent.KEYCODE_FORWARD_DEL -> 0xFFFF // Delete
            KeyEvent.KEYCODE_ESCAPE -> 0xFF1B
            KeyEvent.KEYCODE_INSERT -> 0xFF63
            KeyEvent.KEYCODE_MOVE_HOME -> 0xFF50
            KeyEvent.KEYCODE_MOVE_END -> 0xFF57
            KeyEvent.KEYCODE_PAGE_UP -> 0xFF55
            KeyEvent.KEYCODE_PAGE_DOWN -> 0xFF56
            KeyEvent.KEYCODE_DPAD_LEFT -> 0xFF51
            KeyEvent.KEYCODE_DPAD_UP -> 0xFF52
            KeyEvent.KEYCODE_DPAD_RIGHT -> 0xFF53
            KeyEvent.KEYCODE_DPAD_DOWN -> 0xFF54
            KeyEvent.KEYCODE_SHIFT_LEFT -> 0xFFE1
            KeyEvent.KEYCODE_SHIFT_RIGHT -> 0xFFE2
            KeyEvent.KEYCODE_CTRL_LEFT -> 0xFFE3
            KeyEvent.KEYCODE_CTRL_RIGHT -> 0xFFE4
            KeyEvent.KEYCODE_ALT_LEFT -> 0xFFE9
            KeyEvent.KEYCODE_ALT_RIGHT -> 0xFFEA
            KeyEvent.KEYCODE_META_LEFT -> 0xFFEB
            KeyEvent.KEYCODE_META_RIGHT -> 0xFFEC
            KeyEvent.KEYCODE_CAPS_LOCK -> 0xFFE5
            KeyEvent.KEYCODE_F1 -> 0xFFBE
            KeyEvent.KEYCODE_F2 -> 0xFFBF
            KeyEvent.KEYCODE_F3 -> 0xFFC0
            KeyEvent.KEYCODE_F4 -> 0xFFC1
            KeyEvent.KEYCODE_F5 -> 0xFFC2
            KeyEvent.KEYCODE_F6 -> 0xFFC3
            KeyEvent.KEYCODE_F7 -> 0xFFC4
            KeyEvent.KEYCODE_F8 -> 0xFFC5
            KeyEvent.KEYCODE_F9 -> 0xFFC6
            KeyEvent.KEYCODE_F10 -> 0xFFC7
            KeyEvent.KEYCODE_F11 -> 0xFFC8
            KeyEvent.KEYCODE_F12 -> 0xFFC9
            else -> {
                when {
                    unicodeChar in 0x20..0x7E -> unicodeChar
                    unicodeChar > 0x7E -> 0x01000000 or unicodeChar
                    else -> null
                }
            }
        }
    }
}
