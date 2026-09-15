package com.m57.hermescontrol.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.m57.hermescontrol.data.config.WallpaperConfig

/**
 * Full-bleed wallpaper layer: the single photo (brightness + blur) under a
 * navy scrim. Renders nothing when no wallpaper is set.
 *
 * The scrim reuses the active theme's `background` color (deep navy under the
 * BlackGold preset) so text readability follows the theme instead of a
 * hardcoded hex — which would also trip the `checkColorLiterals` guard.
 */
@Composable
fun WallpaperLayer(config: WallpaperConfig) {
    val uri = config.uri ?: return
    val scrim = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxSize()) {
        val brightnessMatrix =
            ColorMatrix().apply {
                setToScale(config.brightness, config.brightness, config.brightness, 1f)
            }
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(brightnessMatrix),
            modifier =
                Modifier
                    .fillMaxSize()
                    .blur(config.blurRadius.dp),
        )
        // Scrim — keeps warm-white text readable on bright photos.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(scrim.copy(alpha = config.scrimAlpha)),
        )
    }
}
