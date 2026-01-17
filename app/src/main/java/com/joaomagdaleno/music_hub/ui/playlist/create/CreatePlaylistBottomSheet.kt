package com.joaomagdaleno.music_hub.ui.playlist.create

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.databinding.DialogPlaylistCreateBinding
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.Serializer
import com.joaomagdaleno.music_hub.utils.ui.AutoClearedValue
import org.koin.androidx.viewmodel.ext.android.viewModel

class CreatePlaylistBottomSheet : BottomSheetDialogFragment() {

    var binding by AutoClearedValue.autoCleared<DialogPlaylistCreateBinding>(this)
    val viewModel by viewModel<CreatePlaylistViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = DialogPlaylistCreateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.playlistName.setOnEditorActionListener { _, _, _ ->
            binding.playlistDesc.requestFocus()
            true
        }

        binding.playlistDesc.setOnEditorActionListener { _, _, _ ->
            createPlaylist()
            true
        }

        binding.playlistCreateButton.setOnClickListener { createPlaylist() }
        binding.topAppBar.setNavigationOnClickListener { dismiss() }

        ContextUtils.observe(this, viewModel.createPlaylistStateFlow) {
            when (it) {
                CreateState.CreatePlaylist -> {
                    binding.nestedScrollView.isVisible = true
                    binding.saving.root.isVisible = false
                }

                CreateState.Creating -> {
                    binding.nestedScrollView.isVisible = false
                    binding.saving.root.isVisible = true
                    binding.saving.textView.text =
                        getString(R.string.creating_x, binding.playlistName.text)
                }

                is CreateState.PlaylistCreated -> {
                    if (it.playlist != null) parentFragmentManager.setFragmentResult(
                        "createPlaylist",
                        Bundle().apply {
                            putString("origin", it.origin)
                            Serializer.putSerialized(this, "playlist", it.playlist)
                        }
                    )
                    viewModel.createPlaylistStateFlow.value = CreateState.CreatePlaylist
                    dismiss()
                }
            }
        }
    }

    private fun createPlaylist() {
        val title = binding.playlistName.text.toString()
        if (title.isEmpty()) {
            binding.playlistName.error = getString(R.string.playlist_name_empty)
            binding.playlistName.requestFocus()
            return
        }
        val desc = binding.playlistDesc.text.toString().takeIf { it.isNotBlank() }
        viewModel.createPlaylist(title, desc)
    }
}