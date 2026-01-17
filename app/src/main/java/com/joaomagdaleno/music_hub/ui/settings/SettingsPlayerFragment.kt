package com.joaomagdaleno.music_hub.ui.settings


import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.ImageHolder
import com.joaomagdaleno.music_hub.playback.PlayerService
import com.joaomagdaleno.music_hub.playback.listener.PlayerRadio
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.ui.player.PlayerViewModel
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.ui.prefs.MaterialListPreference
import com.joaomagdaleno.music_hub.utils.ui.prefs.MaterialSliderPreference
import com.joaomagdaleno.music_hub.utils.ui.prefs.TransitionPreference

class SettingsPlayerFragment : BaseSettingsFragment() {
    override val title get() = getString(R.string.player)
    override val icon get() = ImageHolder.toResourceImageHolder(R.drawable.ic_play_circle)
    override val creator = { AudioPreference() }

    class AudioPreference : PreferenceFragmentCompat() {

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            BaseSettingsFragment.configure(this)
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val context = preferenceManager.context
            preferenceManager.sharedPreferencesName = ContextUtils.SETTINGS_NAME
            preferenceManager.sharedPreferencesMode = Context.MODE_PRIVATE
            val screen = preferenceManager.createPreferenceScreen(context)
            preferenceScreen = screen

            PreferenceCategory(context).apply {
                title = getString(R.string.playback)
                key = "playback"
                isIconSpaceReserved = false
                layoutResource = R.layout.preference_category
                screen.addPreference(this)

                TransitionPreference(context).apply {
                    key = AudioEffectsFragment.AUDIO_FX
                    title = getString(R.string.audio_fx)
                    summary = getString(R.string.audio_fx_summary)
                    layoutResource = R.layout.preference
                    isIconSpaceReserved = false
                    addPreference(this)
                }

                MaterialListPreference(context).apply {
                    key = PlayerService.STREAM_QUALITY
                    title = getString(R.string.stream_quality)
                    summary = getString(R.string.stream_quality_summary)
                    entries = context.resources.getStringArray(R.array.stream_qualities)
                    entryValues = PlayerService.streamQualities
                    layoutResource = R.layout.preference
                    isIconSpaceReserved = false
                    setDefaultValue(PlayerService.streamQualities[1])
                    addPreference(this)
                }

                MaterialListPreference(context).apply {
                    key = PlayerService.UNMETERED_STREAM_QUALITY
                    title = getString(R.string.unmetered_stream_quality)
                    summary = getString(R.string.unmetered_stream_quality_summary)
                    entries =
                        context.resources.getStringArray(R.array.stream_qualities) + getString(R.string.off)
                    entryValues = PlayerService.streamQualities + "off"
                    layoutResource = R.layout.preference
                    isIconSpaceReserved = false
                    setDefaultValue("off")
                    addPreference(this)
                }
            }

            PreferenceCategory(context).apply {
                title = getString(R.string.behavior)
                key = "behavior"
                isIconSpaceReserved = false
                layoutResource = R.layout.preference_category
                screen.addPreference(this)

                SwitchPreferenceCompat(context).apply {
                    key = PlayerViewModel.KEEP_QUEUE
                    title = getString(R.string.keep_queue)
                    summary = getString(R.string.keep_queue_summary)
                    layoutResource = R.layout.preference_switch
                    isIconSpaceReserved = false
                    setDefaultValue(true)
                    addPreference(this)
                }

                SwitchPreferenceCompat(context).apply {
                    key = PlayerService.CLOSE_PLAYER
                    title = getString(R.string.stop_player)
                    summary = getString(R.string.stop_player_summary)
                    layoutResource = R.layout.preference_switch
                    isIconSpaceReserved = false
                    setDefaultValue(false)
                    addPreference(this)
                }

                SwitchPreferenceCompat(context).apply {
                    key = PlayerService.SKIP_SILENCE
                    title = getString(R.string.skip_silence)
                    summary = getString(R.string.skip_silence_summary)
                    layoutResource = R.layout.preference_switch
                    isIconSpaceReserved = false
                    setDefaultValue(true)
                    addPreference(this)
                }

                SwitchPreferenceCompat(context).apply {
                    key = PlayerService.MORE_BRAIN_CAPACITY
                    title = getString(R.string.more_brain_capacity)
                    summary = getString(R.string.more_brain_capacity_summary)
                    layoutResource = R.layout.preference_switch
                    isIconSpaceReserved = false
                    setDefaultValue(false)
                    addPreference(this)
                }

                SwitchPreferenceCompat(context).apply {
                    key = PlayerRadio.AUTO_START_RADIO
                    title = getString(R.string.auto_start_radio)
                    summary = getString(R.string.auto_start_radio_summary)
                    layoutResource = R.layout.preference_switch
                    isIconSpaceReserved = false
                    setDefaultValue(true)
                    addPreference(this)
                }

                MaterialSliderPreference(context, 200, 1000, allowOverride = true).apply {
                    key = PlayerService.CACHE_SIZE
                    title = getString(R.string.cache_size)
                    summary = getString(R.string.cache_size_summary)
                    isIconSpaceReserved = false
                    setDefaultValue(250)
                    addPreference(this)
                }
            }
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            val view = listView.findViewById<View>(preference.key.hashCode())
            return when (preference.key) {
                AudioEffectsFragment.AUDIO_FX -> {
                    UiUtils.openFragment<AudioEffectsFragment>(requireActivity(), view)
                    true
                }

                else -> false
            }
        }
    }
}
