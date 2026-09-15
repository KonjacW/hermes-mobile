package com.m57.hermescontrol.ui.settings.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.config.WallpaperConfig
import com.m57.hermescontrol.ui.settings.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Wallpaper settings: pick a single photo from the gallery, remove it, and
 * tune brightness / scrim opacity / blur. No slideshow, no rotation — one
 * photo only, by design.
 */
@Composable
internal fun WallpaperSection(
    wallpaper: WallpaperConfig,
    onWallpaperChange: (WallpaperConfig) -> Unit,
    onWallpaperRemoved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                scope.launch {
                    val path = withContext(Dispatchers.IO) { persistWallpaper(context, uri) }
                    if (path != null) onWallpaperChange(wallpaper.copy(uri = path))
                }
            }
        }

    SectionCard {
        Text(
            text = stringResource(R.string.settings_wallpaper_title),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickLauncher.launch("image/*") }) {
                Text(stringResource(R.string.settings_wallpaper_pick))
            }
            if (wallpaper.uri != null) {
                TextButton(onClick = onWallpaperRemoved) {
                    Text(stringResource(R.string.settings_wallpaper_remove))
                }
            }
        }

        if (wallpaper.uri != null) {
            Spacer(modifier = Modifier.height(16.dp))
            WallpaperSlider(
                label = stringResource(R.string.settings_wallpaper_brightness),
                value = wallpaper.brightness,
                range = 0.5f..1.5f,
                onValueChange = { onWallpaperChange(wallpaper.copy(brightness = it)) },
            )
            WallpaperSlider(
                label = stringResource(R.string.settings_wallpaper_scrim),
                value = wallpaper.scrimAlpha,
                range = 0f..1f,
                onValueChange = { onWallpaperChange(wallpaper.copy(scrimAlpha = it)) },
            )
            WallpaperSlider(
                label = stringResource(R.string.settings_wallpaper_blur),
                value = wallpaper.blurRadius,
                range = 0f..40f,
                onValueChange = { onWallpaperChange(wallpaper.copy(blurRadius = it)) },
            )
        }
    }
}

@Composable
private fun WallpaperSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Copy a picked gallery image into app-private storage so it survives gallery
 * permission revocation and file moves. Returns the persisted absolute path.
 */
private fun persistWallpaper(
    context: Context,
    uri: Uri,
): String? =
    runCatching {
        val target = File(context.filesDir, "wallpaper.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target.absolutePath
    }.getOrNull()
