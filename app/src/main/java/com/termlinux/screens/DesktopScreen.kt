package com.termlinux.screens

import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.termlinux.desktop.VncClient
import com.termlinux.desktop.VncState

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DesktopScreen(
    onBackToTerminal: () -> Unit
) {
    var frameTick by remember { mutableIntStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

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
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val title = when (val st = vncState) {
                    is VncState.Connected -> "GUI Desktop (${st.width}×${st.height})"
                    is VncState.Connecting -> "Connecting to 127.0.0.1:5901..."
                    is VncState.Error -> "Desktop disconnected"
                    VncState.Disconnected -> "Desktop offline"
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vncClient.connect("127.0.0.1", 5901) },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Reconnect")
                    }
                    Button(
                        onClick = onBackToTerminal,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Terminal")
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val st = vncState
            val bmp = currentBitmap
            if (st is VncState.Connected && bmp != null) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewSize = it }
                        .pointerInteropFilter { ev ->
                            if (viewSize.width <= 0 || viewSize.height <= 0) return@pointerInteropFilter false
                            val vx = ((ev.x / viewSize.width) * st.width).toInt()
                            val vy = ((ev.y / viewSize.height) * st.height).toInt()
                            val mask = when (ev.actionMasked) {
                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                    when {
                                        (ev.buttonState and MotionEvent.BUTTON_SECONDARY) != 0 -> 4
                                        (ev.buttonState and MotionEvent.BUTTON_TERTIARY) != 0 -> 2
                                        else -> 1
                                    }
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> 0
                                else -> 0
                            }
                            vncClient.sendPointerEvent(mask, vx, vy)
                            true
                        }
                ) {
                    @Suppress("UNUSED_EXPRESSION")
                    frameTick
                    drawImage(
                        image = bmp.asImageBitmap(),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt())
                    )
                }
            } else {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (st is VncState.Error) st.message else "Waiting for Linux GUI Desktop on 127.0.0.1:5901",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "In TermLinux terminal run:\n  tl-desktop setup\n  tl-desktop start",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
