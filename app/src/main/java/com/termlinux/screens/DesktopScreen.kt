package com.termlinux.screens

import android.content.Context
import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.termlinux.desktop.VncClient
import com.termlinux.desktop.VncState
import com.termlinux.desktop.X11KeyMap
import com.termlinux.ui.components.TuiButton
import com.termlinux.ui.components.TuiDivider
import com.termlinux.ui.components.TuiText
import com.termlinux.ui.theme.LocalTuiPalette

private class VncSurfaceView(
    context: Context,
    private val vncClient: VncClient
) : View(context) {

    var bitmap: Bitmap? = null
    var fbWidth: Int = 0
    var fbHeight: Int = 0

    private var zoomScale = 1f
    private var panX = 0f
    private var panY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isPanning = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomScale = (zoomScale * detector.scaleFactor).coerceIn(1f, 4f)
                clampPan()
                invalidate()
                return true
            }
        }
    )

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun resetZoom() {
        zoomScale = 1f
        panX = 0f
        panY = 0f
        invalidate()
    }

    fun showKeyboard() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                text?.forEach { ch ->
                    val sym = ch.code
                    vncClient.sendKeyEvent(true, sym)
                    vncClient.sendKeyEvent(false, sym)
                }
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength.coerceAtLeast(1)) {
                    vncClient.sendKeyEvent(true, 0xFF08)
                    vncClient.sendKeyEvent(false, 0xFF08)
                }
                return true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val sym = X11KeyMap.toKeySym(event)
        if (sym != null) {
            vncClient.sendKeyEvent(true, sym)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val sym = X11KeyMap.toKeySym(event)
        if (sym != null) {
            vncClient.sendKeyEvent(false, sym)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            val (vx, vy) = toFramebufferCoords(event.x, event.y)
            val btnMask = if (vScroll > 0) 8 else if (vScroll < 0) 16 else 0
            if (btnMask != 0) {
                vncClient.sendPointerEvent(btnMask, vx, vy)
                vncClient.sendPointerEvent(0, vx, vy)
                return true
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            val (vx, vy) = toFramebufferCoords(event.x, event.y)
            vncClient.sendPointerEvent(0, vx, vy)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        requestFocus()
        scaleDetector.onTouchEvent(event)

        if (event.pointerCount >= 2) {
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    isPanning = true
                    lastTouchX = avgX(event)
                    lastTouchY = avgY(event)
                }
                MotionEvent.ACTION_MOVE -> if (isPanning && !scaleDetector.isInProgress) {
                    val ax = avgX(event)
                    val ay = avgY(event)
                    panX += ax - lastTouchX
                    panY += ay - lastTouchY
                    lastTouchX = ax
                    lastTouchY = ay
                    clampPan()
                    invalidate()
                }
                MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                    if (event.pointerCount <= 2) isPanning = false
                }
            }
            return true
        }

        if (isPanning) {
            if (event.actionMasked == MotionEvent.ACTION_UP) isPanning = false
            return true
        }

        val (vx, vy) = toFramebufferCoords(event.x, event.y)
        val mask = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                when {
                    (event.buttonState and MotionEvent.BUTTON_SECONDARY) != 0 -> 4
                    (event.buttonState and MotionEvent.BUTTON_TERTIARY) != 0 -> 2
                    else -> 1
                }
            }
            else -> 0
        }
        vncClient.sendPointerEvent(mask, vx, vy)
        return true
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(android.graphics.Color.BLACK)
        val bmp = bitmap ?: return
        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(zoomScale, zoomScale)
        val dest = android.graphics.Rect(0, 0, width, height)
        canvas.drawBitmap(bmp, null, dest, null)
        canvas.restore()
    }

    private fun toFramebufferCoords(x: Float, y: Float): Pair<Int, Int> {
        if (width <= 0 || height <= 0 || fbWidth <= 0 || fbHeight <= 0) return 0 to 0
        val unpannedX = (x - panX) / zoomScale
        val unpannedY = (y - panY) / zoomScale
        val vx = ((unpannedX / width) * fbWidth).toInt().coerceIn(0, fbWidth - 1)
        val vy = ((unpannedY / height) * fbHeight).toInt().coerceIn(0, fbHeight - 1)
        return vx to vy
    }

    private fun clampPan() {
        val maxPanX = 0f
        val minPanX = -(width * (zoomScale - 1f))
        val maxPanY = 0f
        val minPanY = -(height * (zoomScale - 1f))
        panX = panX.coerceIn(minPanX, maxPanX)
        panY = panY.coerceIn(minPanY, maxPanY)
    }

    private fun avgX(e: MotionEvent): Float {
        var s = 0f
        for (i in 0 until e.pointerCount) s += e.getX(i)
        return s / e.pointerCount.coerceAtLeast(1)
    }

    private fun avgY(e: MotionEvent): Float {
        var s = 0f
        for (i in 0 until e.pointerCount) s += e.getY(i)
        return s / e.pointerCount.coerceAtLeast(1)
    }
}

@Composable
fun DesktopScreen(
    onBackToTerminal: () -> Unit
) {
    val p = LocalTuiPalette.current
    val context = LocalContext.current
    var frameTick by remember { mutableIntStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var surfaceRef by remember { mutableStateOf<VncSurfaceView?>(null) }

    val vncClient = remember {
        VncClient(
            onFrameUpdated = { bmp ->
                currentBitmap = bmp
                frameTick++
            }
        )
    }
    val vncState by vncClient.state.collectAsState()

    LaunchedEffect(Unit) {
        vncClient.connect("127.0.0.1", 5901)
    }

    DisposableEffect(Unit) {
        onDispose {
            vncClient.disconnect()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(p.panelAlt)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val title = when (val st = vncState) {
                is VncState.Connected -> "X11 ${st.width}x${st.height}"
                is VncState.Connecting -> "CONNECTING..."
                is VncState.Error -> "DISCONNECTED"
                VncState.Disconnected -> "OFFLINE"
            }
            TuiText(text = title, color = p.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TuiButton("KEYS", onClick = { surfaceRef?.showKeyboard() })
                TuiButton("1:1", onClick = { surfaceRef?.resetZoom() })
                TuiButton("RETRY", onClick = { vncClient.connect("127.0.0.1", 5901) })
                TuiButton("TERM", onClick = onBackToTerminal, filled = true)
            }
        }
        TuiDivider()

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val st = vncState
            if (st is VncState.Connected) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        VncSurfaceView(context, vncClient).also { view ->
                            surfaceRef = view
                            view.fbWidth = st.width
                            view.fbHeight = st.height
                            view.bitmap = currentBitmap
                        }
                    },
                    update = { view ->
                        @Suppress("UNUSED_EXPRESSION")
                        frameTick
                        surfaceRef = view
                        view.fbWidth = st.width
                        view.fbHeight = st.height
                        view.bitmap = currentBitmap
                        view.invalidate()
                    }
                )
            } else {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TuiText(
                        text = if (st is VncState.Error) st.message else "Waiting for Linux GUI Desktop on 127.0.0.1:5901",
                        fontSize = 12.sp
                    )
                    TuiText(
                        text = "In TermLinux terminal run:\n  tl-desktop setup\n  tl-desktop start",
                        color = p.accent,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
