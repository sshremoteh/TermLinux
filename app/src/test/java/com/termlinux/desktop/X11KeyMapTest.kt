package com.termlinux.desktop

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class X11KeyMapTest {
    @Test
    fun mapsSpecialAndAsciiKeysToX11KeySyms() {
        assertEquals(0xFF0D, X11KeyMap.toKeySym(KeyEvent.KEYCODE_ENTER, 0))
        assertEquals(0xFF1B, X11KeyMap.toKeySym(KeyEvent.KEYCODE_ESCAPE, 0))
        assertEquals(0xFF52, X11KeyMap.toKeySym(KeyEvent.KEYCODE_DPAD_UP, 0))
        assertEquals(0xFFBE, X11KeyMap.toKeySym(KeyEvent.KEYCODE_F1, 0))
        assertEquals('A'.code, X11KeyMap.toKeySym(KeyEvent.KEYCODE_A, 'A'.code))
    }
}
