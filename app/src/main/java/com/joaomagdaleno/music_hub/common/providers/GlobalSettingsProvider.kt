package com.joaomagdaleno.music_hub.common.providers

import com.joaomagdaleno.music_hub.common.settings.Settings

/**
 * Interface to provide global [Settings] to the source
 */
interface GlobalSettingsProvider {
    /**
     * Called when the source is initialized, to provide the global [Settings] to the source
     */
    fun setGlobalSettings(globalSettings: Settings)
}