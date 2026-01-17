package com.joaomagdaleno.music_hub.ui.playlist.edit

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.databinding.ItemLoadingBinding
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.Serializer
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class EditPlaylistBottomSheet : BottomSheetDialogFragment(R.layout.item_loading) {
    companion object {
        fun newInstance(
            origin: String, playlist: Playlist, tabId: String?, index: Int
        ) = EditPlaylistBottomSheet().apply {
            arguments = Bundle().apply {
                putString("origin", origin)
                Serializer.putSerialized(this, "playlist", playlist)
                putString("tabId", tabId)
                putInt("removeIndex", index)
            }
        }

        fun getSaveStateText(
            context: Context, playlist: Playlist, state: EditPlaylistViewModel.SaveState
        ) = when (state) {
            is EditPlaylistViewModel.SaveState.Performing -> when (val action = state.action) {
                is EditPlaylistViewModel.Action.Add ->
                    context.getString(R.string.adding_x, state.tracks.joinToString(", ") { it.title })

                is EditPlaylistViewModel.Action.Move ->
                    context.getString(R.string.moving_x, state.tracks.first().title)

                is EditPlaylistViewModel.Action.Remove ->
                    context.getString(R.string.removing_x, state.tracks.joinToString(", ") { it.title })
            }

            EditPlaylistViewModel.SaveState.Saving ->
                context.getString(R.string.saving_x, playlist.title)

            EditPlaylistViewModel.SaveState.Initial -> context.getString(R.string.loading)
            is EditPlaylistViewModel.SaveState.Saved -> context.getString(R.string.loading)
        }
    }

    val args by lazy { requireArguments() }
    val origin by lazy { args.getString("origin")!! }
    val playlist by lazy { Serializer.getSerialized<Playlist>(args, "playlist")!!.getOrThrow() }
    val tabId by lazy { args.getString("tabId") }
    val removeIndex by lazy { args.getInt("removeIndex", -1).takeIf { it != -1 }!! }

    val vm by viewModel<EditPlaylistViewModel> {
        parametersOf(origin, playlist, true, tabId, removeIndex)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = ItemLoadingBinding.bind(view)
        ContextUtils.observe(this, vm.saveState) { save ->
            binding.textView.text = getSaveStateText(requireContext(), playlist, save)
            val saved = save as? EditPlaylistViewModel.SaveState.Saved ?: return@observe
            if (saved.result.isSuccess) parentFragmentManager.setFragmentResult(
                "reload", bundleOf("id" to playlist.id)
            )
            dismiss()
        }
    }
}