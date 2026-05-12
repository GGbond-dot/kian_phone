package com.kian.khup.output.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightScheme = lightColorScheme(
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

private val DarkScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    surface = Primary,
    onSurface = OnPrimary,
)

/**
 * Android 12+ 自动跟随系统取色（dynamic color，类似 MIUI 主题色）。
 * 用户没装 12+ 系统会回落到我们自定义的色板。
 */
@Composable
fun KhupTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = KhupTypography,
        content = content,
    )
}
