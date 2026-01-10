package com.joaomagdaleno.music_hub.common.providers

import com.joaomagdaleno.music_hub.common.settings.Setting
import com.joaomagdaleno.music_hub.common.settings.Settings

/**
 * Interface to provide [Settings] to the source
 */
interface SettingsProvider {
    /**
     * List of [Setting]s to be displayed in the settings screen
     */
    suspend fun getSettingItems() : List<Setting>

    /**
     * Called when the source is initialized, to provide the [Settings] to the source
     */
    fun setSettings(settings: Settings)
}