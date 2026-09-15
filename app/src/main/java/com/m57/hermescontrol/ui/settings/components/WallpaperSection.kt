package com.m57.hermescontrol.ui.settings.components

import android.widget.Toast
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.config.WallpaperConfig
import com.m57.hermescontrol.data.config.WallpaperFiles
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
    // The picker can sit open for a while, so the callback reads the newest config
    // rather than whatever the launcher captured when it was created.
    val currentWallpaper = rememberUpdatedState(wallpaper)
    val pickLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                scope.launch {
                    val previous = currentWallpaper.value.uri
                    val path =
                        withContext(Dispatchers.IO) {
                            WallpaperFiles.store(context.filesDir, System.currentTimeMillis()) {
                                context.contentResolver.openInputStream(uri)
                            }
                        }
                    if (path == null) {
                        // A silent no-op here is indistinguishable from the display
                        // not refreshing, which is exactly what made this hard to
                        // diagnose once.
                        Toast.makeText(context, R.string.settings_wallpaper_pick_failed, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    onWallpaperChange(currentWallpaper.value.copy(uri = path))
                    // Retire the superseded photo only once the new path is the live
                    // config. Interrupted before this point, the app keeps an orphan
                    // file (harmless) instead of a config pointing at a deleted photo.
                    if (previous != null && previous != path) {
                        withContext(Dispatchers.IO) { File(previous).delete() }
                    }
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
            wallpaper.uri?.let { path ->
                TextButton(
                    onClick = {
                        onWallpaperRemoved()
                        scope.launch { withContext(Dispatchers.IO) { File(path).delete() } }
                    },
                ) {
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
