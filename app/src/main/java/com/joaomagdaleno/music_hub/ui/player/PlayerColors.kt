package com.joaomagdaleno.music_hub.ui.player

import android.content.Context
import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import com.google.android.material.color.MaterialColors
import com.joaomagdaleno.music_hub.MainActivity
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.utils.ui.UiUtils

data class PlayerColors(
    val background: Int,
    val accent: Int,
    val onBackground: Int,
) {
    companion object {
        fun getColorsFrom(context: Context, bitmap: Bitmap?): PlayerColors? {
            bitmap ?: return null
            val palette = Palette.from(bitmap).generate()
            return if (!MainActivity.isAmoled(context)) {
                val lightMode = !UiUtils.isNightMode(context)
                val lightSwatch = palette.run {
                    lightVibrantSwatch ?: vibrantSwatch ?: lightMutedSwatch
                }
                val darkSwatch = palette.run {
                    darkVibrantSwatch ?: darkMutedSwatch ?: mutedSwatch
                }
                val bgSwatch = if (lightMode) lightSwatch else darkSwatch
                val accentSwatch = if (lightMode) darkSwatch else lightSwatch
                bgSwatch?.run {
                    PlayerColors(rgb, accentSwatch?.rgb ?: titleTextColor, bodyTextColor)
                }
            } else defaultPlayerColors(context).let { default ->
                val dominantColor = palette.run {
                    vibrantSwatch?.rgb ?: getDominantColor(bitmap).takeIf { it != 0 }
                } ?: return null
                PlayerColors(default.background, dominantColor, default.onBackground)
            }
        }

        fun defaultPlayerColors(context: Context): PlayerColors {
            val background = MaterialColors.getColor(
                context, R.attr.navBackground, 0
            )
            val primary = MaterialColors.getColor(
                context, androidx.appcompat.R.attr.colorPrimary, 0
            )
            val onSurface = MaterialColors.getColor(
                context, com.google.android.material.R.attr.colorOnSurface, 0
            )
            return PlayerColors(background, primary, onSurface)
        }

        fun getDominantColor(bitmap: Bitmap): Int {
            return Palette.from(bitmap).generate().getDominantColor(0)
        }
    }
}