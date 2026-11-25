package com.termlinux.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class TerminalColorTheme(
    val id: String,
    val label: String,
    val background: Int,
    val foreground: Int,
    val cursor: Int
)

object TerminalThemes {
    val Obsidian = TerminalColorTheme("obsidian", "Obsidian", 0xFF0C0E12.toInt(), 0xFFE6E9EF.toInt(), 0xFF4ADE80.toInt())
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

data class AppUiTheme(
    val id: String,
    val label: String,
    val isLight: Boolean,
    val colorScheme: ColorScheme
)

object AppUiThemes {
    val Obsidian = AppUiTheme(
        id = "obsidian",
        label = "TTY Dark",
        isLight = false,
        colorScheme = darkColorScheme(
            primary = Color(0xFF00E676),
            onPrimary = Color(0xFF001F0F),
            secondary = Color(0xFF38BDF8),
            onSecondary = Color(0xFF001E2E),
            background = Color(0xFF07090C),
            onBackground = Color(0xFFE2E8F0),
            surface = Color(0xFF0D1117),
            onSurface = Color(0xFFE2E8F0),
            surfaceVariant = Color(0xFF161B22),
            onSurfaceVariant = Color(0xFF8B949E),
            outline = Color(0xFF28303D),
            error = Color(0xFFFF5555),
            onError = Color(0xFF2B0000)
        )
    )

    val Amoled = AppUiTheme(
        id = "amoled",
        label = "AMOLED Black",
        isLight = false,
        colorScheme = darkColorScheme(
            primary = Color(0xFF22D3EE),
            onPrimary = Color(0xFF042F2E),
            secondary = Color(0xFF4ADE80),
            onSecondary = Color(0xFF052E16),
            background = Color(0xFF000000),
            onBackground = Color(0xFFF3F4F6),
            surface = Color(0xFF09090B),
            onSurface = Color(0xFFF3F4F6),
            surfaceVariant = Color(0xFF18181B),
            onSurfaceVariant = Color(0xFFA1A1AA),
            outline = Color(0xFF27272A),
            error = Color(0xFFF87171),
            onError = Color(0xFF450A0A)
        )
    )

    val Midnight = AppUiTheme(
        id = "midnight",
        label = "Midnight Blue",
        isLight = false,
        colorScheme = darkColorScheme(
            primary = Color(0xFF7AA2F7),
            onPrimary = Color(0xFF0F172A),
            secondary = Color(0xFF7DCFFF),
            onSecondary = Color(0xFF0F172A),
            background = Color(0xFF131520),
            onBackground = Color(0xFFC0CAF5),
            surface = Color(0xFF1A1D2D),
            onSurface = Color(0xFFC0CAF5),
            surfaceVariant = Color(0xFF24283B),
            onSurfaceVariant = Color(0xFF9AA5CE),
            outline = Color(0xFF343B58),
            error = Color(0xFFF7768E),
            onError = Color(0xFF450A0A)
        )
    )

    val Daylight = AppUiTheme(
        id = "light",
        label = "Daylight",
        isLight = true,
        colorScheme = lightColorScheme(
            primary = Color(0xFF0F766E),
            onPrimary = Color(0xFFFFFFFF),
            secondary = Color(0xFF0369A1),
            onSecondary = Color(0xFFFFFFFF),
            background = Color(0xFFF8FAFC),
            onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFE2E8F0),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFCBD5E1),
            error = Color(0xFFDC2626),
            onError = Color(0xFFFFFFFF)
        )
    )

    val all = listOf(Obsidian, Amoled, Midnight, Daylight)

    fun byId(id: String): AppUiTheme =
        all.firstOrNull { it.id == id } ?: Obsidian
}

private val LinuxSharpShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(3.dp),
    large = RoundedCornerShape(3.dp),
    extraLarge = RoundedCornerShape(4.dp)
)

@Composable
fun TermLinuxTheme(
    appThemeId: String = "obsidian",
    content: @Composable () -> Unit
) {
    val theme = AppUiThemes.byId(appThemeId)
    MaterialTheme(
        colorScheme = theme.colorScheme,
        shapes = LinuxSharpShapes,
        content = content
    )
}
