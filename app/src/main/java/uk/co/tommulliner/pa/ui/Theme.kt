package uk.co.tommulliner.pa.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable fun PATheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme=darkColorScheme(
        primary=Color(0xFFD5BE8A),onPrimary=Color(0xFF251E10),
        background=Color(0xFF101215),surface=Color(0xFF191C20),surfaceVariant=Color(0xFF262A30),
        onBackground=Color(0xFFF0F0EC),onSurface=Color(0xFFF0F0EC),onSurfaceVariant=Color(0xFFB5B8BE)
    ),content=content)
}
