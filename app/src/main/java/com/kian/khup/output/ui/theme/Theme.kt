package com.kian.khup.output.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * KhupTheme 永远走 light + 墨绿色板（D1/D2 决策）。
 * darkTheme / dynamicColor 参数保留以备将来扩展，当前忽略。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun KhupTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    @Suppress("unused") val _reserved = ::isSystemInDarkTheme
    val colorScheme = lightColorScheme(
        primary = Primary,
        onPrimary = OnPrimary,
        primaryContainer = PrimaryDim,
        secondary = Accent,
        onSecondary = OnAccent,
        surface = Surface,
        surfaceVariant = SurfaceDim,
        background = Surface,
        onSurface = TextPrimary,
        onSurfaceVariant = TextSecondary,
        outline = Outline,
        outlineVariant = OutlineSoft,
        error = Error,
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = KhupTypography,
        content = content,
    )
}
