package com.termlinux.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class TerminalColorTheme(
    val id: String,
    val label: String,
    val background: Int,
    val foreground: Int,
    val cursor: Int
)

object TerminalThemes {
    val Obsidian = TerminalColorTheme("obsidian", "TTY Dark", 0xFF07090C.toInt(), 0xFFE2E8F0.toInt(), 0xFF00E676.toInt())
    val Amoled = TerminalColorTheme("amoled", "AMOLED", 0xFF000000.toInt(), 0xFFF3F4F6.toInt(), 0xFF22D3EE.toInt())
    val NordicSlate = TerminalColorTheme("nordic", "Nord", 0xFF2E3440.toInt(), 0xFFD8DEE9.toInt(), 0xFF88C0D0.toInt())
    val GruvboxDark = TerminalColorTheme("gruvbox", "Gruvbox", 0xFF1D2021.toInt(), 0xFFEBDBB2.toInt(), 0xFFFABD2F.toInt())
    val SolarizedDark = TerminalColorTheme("solarized", "Solarized", 0xFF002B36.toInt(), 0xFF93A1A1.toInt(), 0xFF2AA198.toInt())
    val Dracula = TerminalColorTheme("dracula", "Dracula", 0xFF282A36.toInt(), 0xFFF8F8F2.toInt(), 0xFFFF79C6.toInt())
    val TokyoNight = TerminalColorTheme("tokyo", "Tokyo Night", 0xFF1A1B26.toInt(), 0xFFC0CAF5.toInt(), 0xFF7AA2F7.toInt())
    val Catppuccin = TerminalColorTheme("catppuccin", "Catppuccin", 0xFF1E1E2E.toInt(), 0xFFCDD6F4.toInt(), 0xFFA6E3A1.toInt())
    val OneDark = TerminalColorTheme("onedark", "One Dark", 0xFF282C34.toInt(), 0xFFABB2BF.toInt(), 0xFF61AFEF.toInt())
    val SolarizedLight = TerminalColorTheme("solarized-light", "Paper Light", 0xFFFDF6E3.toInt(), 0xFF586E75.toInt(), 0xFF268BD2.toInt())

    val all = listOf(
        Obsidian,
        Amoled,
        NordicSlate,
        GruvboxDark,
        SolarizedDark,
        Dracula,
        TokyoNight,
        Catppuccin,
        OneDark,
        SolarizedLight
    )

    fun byId(id: String): TerminalColorTheme =
        all.firstOrNull { it.id == id } ?: Obsidian
}

@Immutable
data class TuiPalette(
    val bg: Color,
    val panel: Color,
    val panelAlt: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val accentText: Color,
    val info: Color,
    val error: Color
)

data class AppUiTheme(
    val id: String,
    val label: String,
    val isLight: Boolean,
    val palette: TuiPalette
)

object AppUiThemes {
    val Obsidian = AppUiTheme(
        id = "obsidian",
        label = "TTY Dark",
        isLight = false,
        palette = TuiPalette(
            bg = Color(0xFF07090C),
            panel = Color(0xFF0D1117),
            panelAlt = Color(0xFF151B23),
            border = Color(0xFF2B3442),
            text = Color(0xFFE6EDF3),
            textMuted = Color(0xFF8B949E),
            accent = Color(0xFF00E676),
            accentText = Color(0xFF04140A),
            info = Color(0xFF38BDF8),
            error = Color(0xFFFF5555)
        )
    )

    val Amoled = AppUiTheme(
        id = "amoled",
        label = "AMOLED",
        isLight = false,
        palette = TuiPalette(
            bg = Color(0xFF000000),
            panel = Color(0xFF080A0D),
            panelAlt = Color(0xFF12161C),
            border = Color(0xFF262C36),
            text = Color(0xFFF3F4F6),
            textMuted = Color(0xFF9CA3AF),
            accent = Color(0xFF22D3EE),
            accentText = Color(0xFF02161A),
            info = Color(0xFF4ADE80),
            error = Color(0xFFFF5555)
        )
    )

    val Midnight = AppUiTheme(
        id = "midnight",
        label = "Midnight",
        isLight = false,
        palette = TuiPalette(
            bg = Color(0xFF0B0E14),
            panel = Color(0xFF111622),
            panelAlt = Color(0xFF1A2233),
            border = Color(0xFF2F3B54),
            text = Color(0xFFC8D3F5),
            textMuted = Color(0xFF828BB8),
            accent = Color(0xFF7AA2F7),
            accentText = Color(0xFF070B14),
            info = Color(0xFF7DCFFF),
            error = Color(0xFFF7768E)
        )
    )

    val Daylight = AppUiTheme(
        id = "light",
        label = "Paper",
        isLight = true,
        palette = TuiPalette(
            bg = Color(0xFFF3F4F6),
            panel = Color(0xFFFFFFFF),
            panelAlt = Color(0xFFE5E7EB),
            border = Color(0xFF9CA3AF),
            text = Color(0xFF111827),
            textMuted = Color(0xFF4B5563),
            accent = Color(0xFF0F766E),
            accentText = Color(0xFFFFFFFF),
            info = Color(0xFF0369A1),
            error = Color(0xFFDC2626)
        )
    )

    val all = listOf(Obsidian, Amoled, Midnight, Daylight)

    fun byId(id: String): AppUiTheme =
        all.firstOrNull { it.id == id } ?: Obsidian
}

val LocalTuiPalette = staticCompositionLocalOf { AppUiThemes.Obsidian.palette }

@Composable
fun TermLinuxTheme(
    appThemeId: String = "obsidian",
    content: @Composable () -> Unit
) {
    val palette = AppUiThemes.byId(appThemeId).palette
    CompositionLocalProvider(LocalTuiPalette provides palette) {
        content()
    }
}
