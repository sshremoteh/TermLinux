package com.termlinux.input

import android.view.MotionEvent
import kotlin.math.abs

/**
 * Detects three consecutive simultaneous three-finger taps without interfering with normal
 * single-finger or mouse interactions and without terminating the underlying Linux session.
 */
class ThreeFingerTripleTapDetector(
    private val maxTapDurationMs: Long = 450L,
    private val maxInterTapGapMs: Long = 900L,
    private val maxMoveSlopPx: Float = 64f,
    private val onTripleTap: () -> Unit
) {
    private var tapCount = 0
    private var currentTapSawThreePointers = false
    private var currentTapStartMs = 0L
    private var lastTapEndMs = 0L
    private var ambiguous = false
    private var startX = 0f
    private var startY = 0f

    fun onTouchEvent(event: MotionEvent): Boolean {
        return handleEvent(
            actionMasked = event.actionMasked,
            pointerCount = event.pointerCount,
            eventTimeMs = event.eventTime,
            avgX = averageX(event),
            avgY = averageY(event)
        )
    }

    internal fun handleEvent(
        actionMasked: Int,
        pointerCount: Int,
        eventTimeMs: Long,
        avgX: Float = 0f,
        avgY: Float = 0f
    ): Boolean {
        when (actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (tapCount > 0 && (eventTimeMs - lastTapEndMs) > maxInterTapGapMs) {
                    tapCount = 0
                }
                currentTapSawThreePointers = false
                currentTapStartMs = eventTimeMs
                ambiguous = false
                startX = avgX
                startY = avgY
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointerCount == 3 && !ambiguous) {
                    currentTapSawThreePointers = true
                    startX = avgX
                    startY = avgY
                } else if (pointerCount > 3) {
                    resetAll()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (currentTapSawThreePointers && !ambiguous) {
                    if (abs(avgX - startX) > maxMoveSlopPx || abs(avgY - startY) > maxMoveSlopPx) {
                        resetAll()
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Remaining pointers will be pointerCount - 1; wait for all fingers to lift
            }

            MotionEvent.ACTION_UP -> {
                val duration = eventTimeMs - currentTapStartMs
                if (currentTapSawThreePointers && !ambiguous && duration <= maxTapDurationMs) {
                    tapCount++
                    lastTapEndMs = eventTimeMs
                    currentTapSawThreePointers = false
                    if (tapCount >= 3) {
                        tapCount = 0
                        onTripleTap()
                        return true
                    }
                } else {
                    resetAll()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                resetAll()
            }
        }
        return false
    }

    fun resetAll() {
        tapCount = 0
        currentTapSawThreePointers = false
        ambiguous = true
        lastTapEndMs = 0L
    }

    private fun averageX(event: MotionEvent): Float {
        if (event.pointerCount <= 0) return 0f
        var sum = 0f
        for (i in 0 until event.pointerCount) sum += event.getX(i)
        return sum / event.pointerCount
    }

    private fun averageY(event: MotionEvent): Float {
        if (event.pointerCount <= 0) return 0f
        var sum = 0f
        for (i in 0 until event.pointerCount) sum += event.getY(i)
        return sum / event.pointerCount
    }
}
