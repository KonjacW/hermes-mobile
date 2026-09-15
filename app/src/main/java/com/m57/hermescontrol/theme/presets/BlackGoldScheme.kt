package com.m57.hermescontrol.theme.presets

import androidx.compose.ui.graphics.Color
import com.m57.hermescontrol.theme.HermesStatusColors
import com.m57.hermescontrol.theme.PaletteColors
import com.m57.hermescontrol.theme.buildThemeDarkOnly

// ---------------------------------------------------------------------
// BlackGold — navy + warm-white + gold accent. Dark only.
//
// Design rules (user's standing UI palette):
//   - Deep navy base #0a182a; layering via same-hue lift, no second hue.
//   - Warm-white text ladder (#f5efe2 / #d8d2c2 / #b4b0a4 / #8f8c82).
//   - Gold #c9a05c / bright gold #d9b87a as ACCENT ONLY — never a wash.
//   - Zero gradient / glow / neon / purple / bright blue.
//   - Semantic colors: success #8aa68f, warning #c9a05c (reuses gold),
//     error #c07a6e (warm brick red).
// Light mode is NOT shipped (a light black-gold is a contradiction) —
// the dispatcher falls back to the default theme, same as AMOLED.
// ---------------------------------------------------------------------

private val BgNavy = Color(0xFF0A182A) // background / surface / lowest
private val BgNavyBar = Color(0xFF081422) // sunken bars — darker than the base
private val BgNavyLow = Color(0xFF0C1A2D) // surfaceContainerLow
private val BgNavyMid = Color(0xFF0F2036) // surfaceContainer
private val BgNavyHigh = Color(0xFF12263F) // surfaceContainerHigh
private val BgNavyHighest = Color(0xFF162C48) // surfaceContainerHighest
private val BgNavyVariant = Color(0xFF0D1B2E) // surfaceVariant

private val GoldPrimary = Color(0xFFC9A05C) // accent — buttons, focus ring, scrollbar
private val GoldBright = Color(0xFFD9B87A) // links, labels, small emphasis

private val InkPrimary = Color(0xFFF5EFE2) // warm white — titles + body
private val InkSecondary = Color(0xFFD8D2C2) // subtitles, secondary
private val InkTertiary = Color(0xFFB4B0A4) // metadata
private val InkFaint = Color(0xFF8F8C82) // weakest, placeholders

private val ContainerGold = Color(0xFF3A2F1E) // dark-gold container (selected chips)
private val ContainerGoldDim = Color(0xFF2A2518) // dimmer gold container

private val OutlineNavy = Color(0xFF3A4A5E) // outline
private val OutlineNavyDim = Color(0xFF223347) // outlineVariant

// Semantic (from the black-gold token set)
private val SuccessGreen = Color(0xFF8AA68F) // soft grey-green
private val SuccessContainer = Color(0xFF22352A)
private val WarnGold = Color(0xFFC9A05C) // reuses gold body
private val WarnContainer = Color(0xFF3A2F1E)
private val ErrorRed = Color(0xFFC07A6E) // warm brick red
private val ErrorContainer = Color(0xFF3A2522)
private val InfoInk = Color(0xFFD8D2C2) // info reuses secondary ink
private val InfoContainer = Color(0xFF16202E)

/**
 * BlackGold theme — dark-only, navy + warm-white + gold accent.
 * Background is pure #0a182a. Light mode isn't shipped; the dispatcher
 * falls back to the default theme in light mode (same contract as AMOLED).
 */
val BlackGoldTheme =
    buildThemeDarkOnly(
        dark =
            PaletteColors(
                primary = GoldPrimary,
                onPrimary = BgNavy,
                primaryContainer = ContainerGold,
                onPrimaryContainer = GoldBright,
                secondary = GoldBright,
                onSecondary = BgNavy,
                secondaryContainer = ContainerGoldDim,
                onSecondaryContainer = InkSecondary,
                tertiary = InkSecondary,
                onTertiary = BgNavy,
                tertiaryContainer = BgNavyHigh,
                onTertiaryContainer = InkSecondary,
                background = BgNavy,
                onBackground = InkPrimary,
                surface = BgNavy,
                onSurface = InkPrimary,
                surfaceVariant = BgNavyVariant,
                onSurfaceVariant = InkSecondary,
                surfaceContainerLowest = BgNavy,
                surfaceContainerLow = BgNavyLow,
                surfaceContainer = BgNavyMid,
                surfaceContainerHigh = BgNavyHigh,
                surfaceContainerHighest = BgNavyHighest,
                inverseSurface = InkPrimary,
                inverseOnSurface = BgNavy,
                inversePrimary = BgNavy,
                outline = OutlineNavy,
                outlineVariant = OutlineNavyDim,
                scrim = Color.Black,
                status =
                    HermesStatusColors(
                        success = SuccessGreen,
                        successContainer = SuccessContainer,
                        onSuccess = BgNavy,
                        warning = WarnGold,
                        warningContainer = WarnContainer,
                        onWarning = BgNavy,
                        error = ErrorRed,
                        errorContainer = ErrorContainer,
                        onError = BgNavy,
                        onErrorContainer = InkPrimary,
                        info = InfoInk,
                        infoContainer = InfoContainer,
                        onInfo = BgNavy,
                        neutral = InkFaint,
                    ),
            ),
    )
