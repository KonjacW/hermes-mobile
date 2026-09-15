package com.m57.hermescontrol.data.config

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

/**
 * Guards the invariants behind "picking a new wallpaper appeared to do nothing",
 * plus the failure behaviour the fix must not trade away.
 *
 * 1. Every pick must land on a new file name: Coil 3 no longer folds a file's
 *    last-write timestamp into its cache key, so one fixed name kept resolving to
 *    the previously decoded bitmap — and an unchanged config meant no
 *    recomposition either.
 * 2. A pick that produced nothing must leave the wallpaper the user already has
 *    alone, and must not leave a half-written file behind pretending to be one.
 */
class WallpaperFilesTest {
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        filesDir = Files.createTempDirectory("wallpaper-files").toFile()
    }

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun everyPickGetsItsOwnFileName() {
        val first = WallpaperFiles.fileName(1_700_000_000_000L)
        val second = WallpaperFiles.fileName(1_700_000_060_000L)
        assertNotEquals(first, second)
    }

    @Test
    fun consecutiveTimestampsAlsoDiffer() {
        assertNotEquals(WallpaperFiles.fileName(1L), WallpaperFiles.fileName(2L))
    }

    @Test
    fun nameIsAPlainJpgUnderTheWallpaperPrefix() {
        val name = WallpaperFiles.fileName(1_700_000_000_000L)
        assertTrue("expected the wallpaper prefix, got $name", name.startsWith("wallpaper-"))
        assertTrue("expected a .jpg suffix, got $name", name.endsWith(".jpg"))
        assertTrue("expected the timestamp inside the name, got $name", name.contains("1700000000000"))
    }

    @Test
    fun takenNameGetsBumpedInsteadOfColliding() {
        val timestamp = 1_700_000_000_000L
        val taken = WallpaperFiles.fileName(timestamp)
        val actual = WallpaperFiles.fileName(timestamp) { it == taken }
        assertEquals("wallpaper-${timestamp}_1.jpg", actual)
    }

    @Test
    fun bumpingKeepsGoingUntilFree() {
        val timestamp = 1_700_000_000_000L
        val taken = setOf("wallpaper-$timestamp.jpg", "wallpaper-${timestamp}_1.jpg")
        val actual = WallpaperFiles.fileName(timestamp) { it in taken }
        assertEquals("wallpaper-${timestamp}_2.jpg", actual)
    }

    @Test
    fun storeWritesTheFreshPhotoUnderANewName() {
        val previous = File(filesDir, "wallpaper-1.jpg").apply { writeText("previous") }
        val stored = WallpaperFiles.store(filesDir, 2L) { "fresh".byteInputStream() }
        assertEquals(File(filesDir, "wallpaper-2.jpg").absolutePath, stored)
        assertEquals("fresh", File(stored!!).readText())
        assertTrue("store() retires nothing — the caller does that after committing", previous.exists())
    }

    @Test
    fun storeReportsFailureWhenTheStreamIsMissing() {
        val previous = File(filesDir, "wallpaper-1.jpg").apply { writeText("previous") }
        assertNull(WallpaperFiles.store(filesDir, 2L) { null })
        assertTrue("a missing stream must leave the current wallpaper alone", previous.exists())
        assertEquals(listOf("wallpaper-1.jpg"), filesDir.list()!!.sorted())
    }

    @Test
    fun storeRejectsAnEmptyPhoto() {
        assertNull(WallpaperFiles.store(filesDir, 2L) { ByteArrayInputStream(ByteArray(0)) })
        assertTrue("an empty pick must not become the wallpaper", filesDir.list()!!.isEmpty())
    }

    @Test
    fun storeCleansUpAHalfWrittenFile() {
        val broken =
            object : InputStream() {
                override fun read(): Int = throw IOException("broken pick")
            }
        assertNull(WallpaperFiles.store(filesDir, 2L) { broken })
        assertTrue("no half-written file may survive", filesDir.list()!!.isEmpty())
    }
}
