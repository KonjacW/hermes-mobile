package com.m57.hermescontrol.data.config

import java.io.File
import java.io.InputStream

/**
 * File names for the persisted wallpaper photo.
 *
 * Every pick has to land on a **new** name. Coil 3 stopped folding a file's
 * last-write timestamp into its cache key (Coil 2 did; Coil 3 dropped it so the
 * cache key never touches the disk on the main thread). With one fixed name the
 * second photo therefore resolves to the first photo's cached bitmap — and
 * because the stored config would then be byte-identical, nothing would even
 * recompose. A fresh name per pick changes the loaded model, which invalidates
 * every cache layer in one move.
 */
object WallpaperFiles {
    private const val PREFIX = "wallpaper-"
    private const val SUFFIX = ".jpg"

    /**
     * `wallpaper-<timestampMillis>.jpg`, falling back to `…_1.jpg`, `…_2.jpg` when
     * that name is already taken (two picks inside the same millisecond).
     */
    fun fileName(
        timestampMillis: Long,
        exists: (String) -> Boolean = { false },
    ): String {
        val base = "$PREFIX$timestampMillis"
        var candidate = "$base$SUFFIX"
        var bump = 1
        while (exists(candidate)) {
            candidate = "${base}_$bump$SUFFIX"
            bump++
        }
        return candidate
    }

    /**
     * Copy [open]'s stream into [filesDir] under a fresh name and return the new
     * absolute path — or null when no new photo was produced (no stream, an empty
     * stream, or a copy that blew up).
     *
     * Nothing the user already has is touched on the null path: a wallpaper that
     * works must survive a failed pick, and a half-written file must not be left
     * behind pretending to be one. Retiring the superseded photo is deliberately
     * *not* done here — the caller does it after the new path is committed, so an
     * interruption can only ever leave an orphan file behind, never a config
     * pointing at a photo that no longer exists.
     *
     * Context-free on purpose: this is the part worth unit-testing, and an
     * Android `Context` would keep it out of reach of the JVM tests.
     */
    fun store(
        filesDir: File,
        timestampMillis: Long,
        open: () -> InputStream?,
    ): String? {
        val target =
            File(
                filesDir,
                fileName(timestampMillis) { name -> File(filesDir, name).exists() },
            )
        val copied =
            runCatching {
                val input = open() ?: return@runCatching false
                input.use { stream ->
                    target.outputStream().use { output -> stream.copyTo(output) }
                }
                true
            }.getOrDefault(false)
        if (!copied || target.length() == 0L) {
            // An empty stream copies "successfully" without throwing, so a zero-byte
            // file has to be rejected explicitly — otherwise it would be committed as
            // the wallpaper and the photo it replaced would be retired for nothing.
            target.delete()
            return null
        }
        return target.absolutePath
    }
}
