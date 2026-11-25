package com.termlinux.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.termlinux.ui.theme.LocalTuiPalette

@Composable
fun TuiText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalTuiPalette.current.text,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontFamily = FontFamily.Monospace,
            fontWeight = fontWeight
        )
    )
}

@Composable
fun TuiDivider(modifier: Modifier = Modifier) {
    val p = LocalTuiPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(p.border)
    )
}

@Composable
fun TuiButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true
) {
    val p = LocalTuiPalette.current
    val bg = when {
        !enabled -> p.panelAlt
        danger && filled -> p.error
        filled -> p.accent
        else -> p.panel
    }
    val fg = when {
        !enabled -> p.textMuted
        danger && filled -> p.bg
        filled -> p.accentText
        danger -> p.error
        else -> p.text
    }
    val borderColor = when {
        !enabled -> p.border
        danger -> p.error
        filled -> p.accent
        else -> p.border
    }

    Box(
        modifier = modifier
            .height(34.dp)
            .background(bg)
            .border(1.dp, borderColor)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        TuiText(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = if (filled) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun TuiToggleRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    subtitle: String? = null
) {
    val p = LocalTuiPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onToggle(!checked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            TuiText(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                TuiText(text = subtitle, color = p.textMuted, fontSize = 10.sp)
            }
        }
        Box(
            modifier = Modifier
                .background(if (checked) p.accent else p.bg)
                .border(1.dp, if (checked) p.accent else p.border)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            TuiText(
                text = if (checked) "[ ON ]" else "[OFF ]",
                color = if (checked) p.accentText else p.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TuiSlider(
    label: String,
    valueText: String,
    fraction: Float,
    onFractionChange: (Float) -> Unit
) {
    val p = LocalTuiPalette.current
    val clamped = fraction.coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TuiText(text = label, fontSize = 12.sp)
            TuiText(text = valueText, color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .background(p.bg)
                .border(1.dp, p.border)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (size.width > 0) {
                            onFractionChange((offset.x / size.width).coerceIn(0f, 1f))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        if (size.width > 0) {
                            onFractionChange((change.position.x / size.width).coerceIn(0f, 1f))
                        }
                    }
                }
        ) {
            val fillW = size.width * clamped
            drawRect(
                color = p.accent.copy(alpha = 0.35f),
                topLeft = Offset.Zero,
                size = Size(fillW, size.height)
            )
            val cursorX = fillW.coerceIn(2f, (size.width - 4f).coerceAtLeast(2f))
            drawRect(
                color = p.accent,
                topLeft = Offset(cursorX - 2f, 0f),
                size = Size(4f, size.height)
            )
        }
    }
}

@Composable
fun TuiTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val p = LocalTuiPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(p.bg)
            .border(1.dp, p.border)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TuiText(text = "> ", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                TuiText(text = placeholder, color = p.textMuted, fontSize = 12.sp, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                cursorBrush = SolidColor(p.accent),
                textStyle = TextStyle(
                    color = p.text,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun TuiPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalTuiPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(p.panel)
            .border(1.dp, p.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(p.panelAlt)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TuiText(
                text = "[ $title ]",
                color = p.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        TuiDivider()
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
fun TuiProgressBar(
    fraction: Float?,
    modifier: Modifier = Modifier
) {
    val p = LocalTuiPalette.current
    val fill = (fraction ?: 0.35f).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(p.bg)
            .border(1.dp, p.border)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fill)
                .height(10.dp)
                .background(p.accent)
        )
    }
}

@Composable
fun TuiDialog(
    title: String,
    onDismiss: () -> Unit,
    footer: @Composable () -> Unit = {
        TuiButton(label = "CLOSE", onClick = onDismiss)
    },
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalTuiPalette.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(p.panel)
                .border(1.dp, p.accent)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(p.panelAlt)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TuiText(
                    text = "[ $title ]",
                    color = p.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                TuiText(
                    text = "[X]",
                    color = p.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                )
            }
            TuiDivider()
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
            TuiDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(p.panelAlt)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Spacer(Modifier.width(4.dp))
                footer()
            }
        }
    }
}
