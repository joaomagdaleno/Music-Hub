package com.joaomagdaleno.music_hub.ui.player.audiofx

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewModelScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.databinding.DialogPlayerAudioFxBinding
import com.joaomagdaleno.music_hub.databinding.FragmentAudioFxBinding
import com.joaomagdaleno.music_hub.playback.listener.EffectsListener
import com.joaomagdaleno.music_hub.ui.player.PlayerViewModel
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.PermsUtils
import com.joaomagdaleno.music_hub.utils.ui.AutoClearedValue.Companion.autoCleared
import com.joaomagdaleno.music_hub.utils.ui.RulerAdapter
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class AudioEffectsBottomSheet : BottomSheetDialogFragment() {

    var binding by autoCleared<DialogPlayerAudioFxBinding>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogPlayerAudioFxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val viewModel by activityViewModel<PlayerViewModel>()
        var mediaId: String? = null

        fun bind() {
            val settings = EffectsListener.globalFx(requireContext())
            settings.edit {
                val customEffects = settings.getStringSet(EffectsListener.CUSTOM_EFFECTS, null) ?: emptySet()
                putStringSet(EffectsListener.CUSTOM_EFFECTS, customEffects + mediaId?.hashCode()?.toString())
            }
            binding.audioFxDescription.isVisible = mediaId != null
            val mediaSettings =
                EffectsListener.getFxPrefs(requireContext(), settings, mediaId?.hashCode()) ?: settings
            Companion.bind(binding.audioFxFragment, mediaSettings) { onEqualizerClicked(this) }
        }
        ContextUtils.observe(this, viewModel.playerState.current) {
            mediaId = it?.mediaItem?.mediaId
            bind()
        }
        binding.topAppBar.setNavigationOnClickListener { dismiss() }
        binding.topAppBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_refresh -> {
                    val context = requireContext()
                    val id = mediaId ?: return@setOnMenuItemClickListener false
                    EffectsListener.deleteFxPrefs(context, id.hashCode())
                    bind()
                    true
                }

                else -> false
            }
        }
    }

    companion object {
        @SuppressLint("SetTextI18n")
        fun bind(
            binding: FragmentAudioFxBinding,
            settings: SharedPreferences, 
            onEqualizerClicked: () -> Unit
        ) {
            val speed = settings.getInt(EffectsListener.PLAYBACK_SPEED, EffectsListener.speedRange.indexOf(1f))
            val adapter = RulerAdapter(object : RulerAdapter.Listener<Int> {
                override fun intervalText(value: Int) = "${EffectsListener.speedRange.getOrNull(value) ?: 1f}x"
                override fun onSelectItem(value: Int) {
                    binding.speedValue.text = "${EffectsListener.speedRange.getOrNull(value) ?: 1f}x"
                    settings.edit { putInt(EffectsListener.PLAYBACK_SPEED, value) }
                }
            })

            binding.speedRecycler.adapter = adapter
            adapter.submitList(List(EffectsListener.speedRange.size) { index -> index to (index % 2 == 0) }, speed)

            binding.pitchSwitch.isChecked = settings.getBoolean(EffectsListener.CHANGE_PITCH, true)
            binding.pitch.setOnClickListener {
                binding.pitchSwitch.isChecked = !binding.pitchSwitch.isChecked
            }
            binding.pitchSwitch.setOnCheckedChangeListener { _, isChecked ->
                settings.edit { putBoolean(EffectsListener.CHANGE_PITCH, isChecked) }
            }
            binding.bassBoostSlider.value = settings.getInt(EffectsListener.BASS_BOOST, 0).toFloat()
            binding.bassBoostSlider.addOnChangeListener { _, value, _ ->
                settings.edit { putInt(EffectsListener.BASS_BOOST, value.toInt()) }
            }
            binding.equalizer.setOnClickListener { onEqualizerClicked() }
        }

        private fun openEqualizer(activity: ComponentActivity, sessionId: Int) {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, activity.packageName)
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            val contract = ActivityResultContracts.StartActivityForResult()
            PermsUtils.registerActivityResultLauncher(activity, contract) {}.launch(intent)
        }

        fun onEqualizerClicked(fragment: Fragment) {
            val viewModel by fragment.activityViewModel<PlayerViewModel>()
            val sessionId = viewModel.playerState.session.value
            runCatching { openEqualizer(fragment.requireActivity(), sessionId) }.getOrElse {
                viewModel.run { viewModelScope.launch { app.throwFlow.emit(it) } }
            }
        }
    }
}
