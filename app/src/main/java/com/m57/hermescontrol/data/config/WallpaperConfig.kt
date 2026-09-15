package com.m57.hermescontrol.data.config

import kotlinx.serialization.Serializable

/**
 * User wallpaper — a single photo picked from the device gallery, persisted to
 * app-private storage, with three user-tunable visual parameters.
 *
 * Deliberately NOT a slideshow / rotation system: one photo only. The tunables
 * replace the desktop plugin's per-layer controls with the three that matter on
 * a phone — brightness, scrim (dark overlay) opacity, and blur radius.
 */
@Serializable
data class WallpaperConfig(
    /** Absolute file path to the persisted wallpaper image, or null = no wallpaper. */
    val uri: String? = null,
    /** Brightness multiplier applied to the image. 1.0f = untouched. */
    val brightness: Float = 1.0f,
    /**
     * Opacity of the navy scrim laid over the image to keep warm-white text
     * readable. 0.6 keeps the photo clearly visible; raise it toward 1.0 on a
     * very bright photo where text contrast suffers.
     */
    val scrimAlpha: Float = 0.6f,
    /** Blur radius in dp. 0f = no blur. */
    val blurRadius: Float = 0f,
)
