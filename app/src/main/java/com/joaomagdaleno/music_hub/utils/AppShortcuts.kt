package com.joaomagdaleno.music_hub.utils

import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.di.App

fun configureAppShortcuts(app: App) {
    val searchShortcut = ShortcutInfoCompat.Builder(app.context, "search")
        .setShortLabel(app.context.getString(R.string.search))
        .setIcon(IconCompat.createWithResource(app.context, R.drawable.ic_search_outline))
        .setIntent(Intent(Intent.ACTION_VIEW, "echo://search".toUri()))
        .build()

    val libraryShortcut = ShortcutInfoCompat.Builder(app.context, "library")
        .setShortLabel(app.context.getString(R.string.library))
        .setIcon(IconCompat.createWithResource(app.context, R.drawable.ic_library_music))
        .setIntent(Intent(Intent.ACTION_VIEW, "echo://library".toUri()))
        .build()

    ShortcutManagerCompat.setDynamicShortcuts(app.context, listOf(searchShortcut, libraryShortcut))
}