package com.termlinux.input

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreeFingerTripleTapDetectorTest {

    private fun performThreeFingerTap(detector: ThreeFingerTripleTapDetector, startMs: Long) {
        detector.handleEvent(MotionEvent.ACTION_DOWN, 1, startMs, 100f, 100f)
        detector.handleEvent(MotionEvent.ACTION_POINTER_DOWN, 2, startMs + 10, 110f, 100f)
        detector.handleEvent(MotionEvent.ACTION_POINTER_DOWN, 3, startMs + 20, 120f, 100f)
        detector.handleEvent(MotionEvent.ACTION_POINTER_UP, 3, startMs + 60, 120f, 100f)
        detector.handleEvent(MotionEvent.ACTION_POINTER_UP, 2, startMs + 70, 110f, 100f)
        detector.handleEvent(MotionEvent.ACTION_UP, 1, startMs + 80, 100f, 100f)
    }

    @Test
    fun threeConsecutiveThreeFingerTapsTriggerCallback() {
        var triggered = 0
        val detector = ThreeFingerTripleTapDetector { triggered++ }

        performThreeFingerTap(detector, 1000L)
        performThreeFingerTap(detector, 1250L)
        assertEquals(0, triggered)
        performThreeFingerTap(detector, 1500L)
        assertEquals(1, triggered)
    }

    @Test
    fun twoFingerTapResetsCounter() {
        var triggered = 0
        val detector = ThreeFingerTripleTapDetector { triggered++ }

        performThreeFingerTap(detector, 1000L)
        performThreeFingerTap(detector, 1250L)

        // Two-finger tap in between
        detector.handleEvent(MotionEvent.ACTION_DOWN, 1, 1450L, 100f, 100f)
        detector.handleEvent(MotionEvent.ACTION_POINTER_DOWN, 2, 1460L, 110f, 100f)
        detector.handleEvent(MotionEvent.ACTION_POINTER_UP, 2, 1490L, 110f, 100f)
        detector.handleEvent(MotionEvent.ACTION_UP, 1, 1500L, 100f, 100f)

        performThreeFingerTap(detector, 1650L)
        assertEquals(0, triggered)
    }

    @Test
    fun interTapTimeoutResetsCounter() {
        var triggered = 0
        val detector = ThreeFingerTripleTapDetector(maxInterTapGapMs = 500L) { triggered++ }

        performThreeFingerTap(detector, 1000L)
        performThreeFingerTap(detector, 1250L)
        // Gap > 500ms
        performThreeFingerTap(detector, 2200L)
        assertEquals(0, triggered)
    }
}
