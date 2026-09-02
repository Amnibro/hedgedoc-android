package org.hedgedoc.android.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class ScientPalette(
    val id: String,
    val label: String,
    val bg: Color,
    val bg2: Color,
    val panel: Color,
    val panel2: Color,
    val border: Color,
    val text: Color,
    val textSoft: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentInk: Color,
    val danger: Color,
    val paper: Color,
    val paperInk: Color,
    val lightBars: Boolean,
)

object ScientPalettes {
    private val charcoal = Triple(
        Color(0xFF08090B),
        Color(0xFF0D0F12),
        Color(0xFF111418),
    )
    private val charcoal2 = Color(0xFF161A20)
    private val charcoalBorder = Color(0xFF20242B)
    private val ink = Color(0xFFEDEFF2)
    private val mute = Color(0xFFA7ADB6)
    private val dangerDark = Color(0xFFFF6B6B)

    private fun product(id: String, label: String, accent: Color, accentSoft: Color, accentInk: Color) = ScientPalette(
        id = id,
        label = label,
        bg = charcoal.first,
        bg2 = charcoal.second,
        panel = charcoal.third,
        panel2 = charcoal2,
        border = charcoalBorder,
        text = ink,
        textSoft = mute,
        accent = accent,
        accentSoft = accentSoft,
        accentInk = accentInk,
        danger = dangerDark,
        paper = charcoal2,
        paperInk = ink,
        lightBars = false,
    )

    val Scient = ScientPalette(
        id = "scient",
        label = "Scient",
        bg = charcoal.first,
        bg2 = charcoal.second,
        panel = charcoal.third,
        panel2 = charcoal2,
        border = charcoalBorder,
        text = ink,
        textSoft = mute,
        accent = Color(0xFFC89B4E),
        accentSoft = Color(0xFFE2BC7C),
        accentInk = Color(0xFF120C03),
        danger = dangerDark,
        paper = charcoal2,
        paperInk = ink,
        lightBars = false,
    )

    val Emerald = ScientPalette(
        id = "emerald",
        label = "Emerald",
        bg = Color(0xFF07110F),
        bg2 = Color(0xFF0A1815),
        panel = Color(0xFF10231F),
        panel2 = Color(0xFF17322C),
        border = Color(0xFF24483F),
        text = ink,
        textSoft = mute,
        accent = Color(0xFF2DD4BF),
        accentSoft = Color(0xFF5EEAD4),
        accentInk = Color(0xFF051210),
        danger = dangerDark,
        paper = Color(0xFF17322C),
        paperInk = ink,
        lightBars = false,
    )

    val Haven = ScientPalette(
        id = "haven",
        label = "Haven",
        bg = Color(0xFF191B28),
        bg2 = Color(0xFF1E2035),
        panel = Color(0xFF252840),
        panel2 = Color(0xFF2C2F4A),
        border = Color(0xFF383B5E),
        text = Color(0xFFE2E4F0),
        textSoft = Color(0xFF9498B3),
        accent = Color(0xFF7C5CFC),
        accentSoft = Color(0xFF9478FF),
        accentInk = Color(0xFF120A24),
        danger = dangerDark,
        paper = Color(0xFF2C2F4A),
        paperInk = Color(0xFFE2E4F0),
        lightBars = false,
    )

    val Learn = ScientPalette(
        id = "learn",
        label = "Learn",
        bg = Color(0xFF0C0F12),
        bg2 = Color(0xFF111418),
        panel = Color(0xFF1A222A),
        panel2 = Color(0xFF24313D),
        border = Color(0xFF304252),
        text = Color(0xFFECF0F1),
        textSoft = Color(0xFF9498B3),
        accent = Color(0xFF4ADE80),
        accentSoft = Color(0xFF74E8A0),
        accentInk = Color(0xFF05170C),
        danger = dangerDark,
        paper = Color(0xFF24313D),
        paperInk = Color(0xFFECF0F1),
        lightBars = false,
    )

    val Amni = product("amni", "Amni", Color(0xFF00FF9D), Color(0xFF33FFB3), Color(0xFF04140C))
    val Crypt = product("crypt", "Crypt", Color(0xFF6BA5FF), Color(0xFF8FBAFF), Color(0xFF06101F))
    val Ai = product("ai", "Ai", Color(0xFFE58B67), Color(0xFFF0A98C), Color(0xFF1D0D06))
    val Core = product("core", "Core", Color(0xFFFF6B6B), Color(0xFFFF8F8F), Color(0xFF1F0707))
    val Explore = product("explore", "Explore", Color(0xFF6BC6FF), Color(0xFF93D6FF), Color(0xFF051520))
    val Calc = product("calc", "Calc", Color(0xFFFF8A5E), Color(0xFFFFA982), Color(0xFF1E0C04))
    val Braid = product("braid", "Braid", Color(0xFFA88FE8), Color(0xFFC4B3F2), Color(0xFF100A1F))

    val Light = ScientPalette(
        id = "light",
        label = "Light",
        bg = Color(0xFFF3F2EF),
        bg2 = Color(0xFFEAE9E4),
        panel = Color(0xFFFFFFFF),
        panel2 = Color(0xFFF8F7F4),
        border = Color(0xFFCBC7BE),
        text = Color(0xFF101216),
        textSoft = Color(0xFF454A52),
        accent = Color(0xFF8A6318),
        accentSoft = Color(0xFFA2762A),
        accentInk = Color(0xFFFFFFFF),
        danger = Color(0xFFC0392B),
        paper = Color(0xFFFFFFFF),
        paperInk = Color(0xFF101216),
        lightBars = true,
    )

    val All = listOf(Scient, Emerald, Amni, Haven, Crypt, Ai, Core, Explore, Calc, Learn, Braid, Light)

    fun byId(id: String): ScientPalette = All.firstOrNull { it.id == id } ?: Scient
}

val LocalScient = staticCompositionLocalOf { ScientPalettes.Scient }
