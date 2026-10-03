package com.projectkaka.inventory.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark theme: Navy Blue + White Peach  (UNCHANGED — user requirement)
private val KakaDarkScheme = darkColorScheme(
    primary = Color(0xFFFFE5B4), // White Peach
    onPrimary = Color(0xFF1D2A4C), // Navy Blue
    secondary = Color(0xFFFFCBA4), // Deep Peach
    onSecondary = Color(0xFF1D2A4C),
    background = Color(0xFF0F1626), // Deep Navy
    onBackground = Color(0xFFFFE5B4),
    surface = Color(0xFF1D2A4C), // Navy Blue
    onSurface = Color(0xFFFFE5B4),
    surfaceVariant = Color(0xFF26375E),
    error = Color(0xFFE74C3C)
)

// Light theme: Clinical calm — sage green, warm cream, soft yellow
// Inspired by therapeutic / clinical environments for mental peace
private val KakaLightScheme = lightColorScheme(
    primary = Color(0xFF6BAF8D),           // Clinical sage green
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFE8D58B),         // Warm muted yellow
    onSecondary = Color(0xFF3A3A2E),
    tertiary = Color(0xFF8ABCA7),          // Lighter sage accent
    onTertiary = Color(0xFF1E342A),
    background = Color(0xFFF7F5EE),        // Warm off-white / cream
    onBackground = Color(0xFF2D3A2E),      // Deep forest text
    surface = Color(0xFFFFFDF7),           // Warm white
    onSurface = Color(0xFF2D3A2E),
    surfaceVariant = Color(0xFFE8EDE3),    // Pale sage surface
    onSurfaceVariant = Color(0xFF4A5A4A),
    secondaryContainer = Color(0xFFF0EAC8), // Soft yellow container
    onSecondaryContainer = Color(0xFF3A3A2E),
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF93000A),
    error = Color(0xFFC0392B)
)

/**
 * The mode is an explicit parameter rather than the system setting, because the app owns
 * its appearance: the user's choice is persisted and drives the whole tree from the top.
 */
@Composable
fun KakaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) KakaDarkScheme else KakaLightScheme,
        content = content
    )
}
