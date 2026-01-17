package com.joaomagdaleno.music_hub.ui.playlist.edit.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.databinding.FragmentPlaylistSearchBinding
import com.joaomagdaleno.music_hub.ui.common.GridAdapter
import com.joaomagdaleno.music_hub.ui.main.search.SearchFragment
import com.joaomagdaleno.music_hub.ui.playlist.SelectableMediaAdapter
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.Serializer
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils
import com.joaomagdaleno.music_hub.utils.ui.AutoClearedValue
import com.joaomagdaleno.music_hub.utils.ui.UiUtils

class EditPlaylistSearchFragment : Fragment() {
    companion object {
        fun getBundle(origin: String) = Bundle().apply {
            putString("origin", origin)
        }
    }

    private var binding by AutoClearedValue.autoCleared<FragmentPlaylistSearchBinding>(this)
    private val viewModel by viewModels<EditPlaylistSearchViewModel>()

    private val args by lazy { requireArguments() }
    private val origin by lazy { args.getString("origin")!! }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentPlaylistSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        AnimationUtils.setupTransition(this, view)
        val behavior = BottomSheetBehavior.from(binding.bottomSheet)
        binding.bottomSheetDragHandle.setOnClickListener { behavior.state = STATE_EXPANDED }
        var topInset = 0
        UiUtils.applyInsets(this) { insets ->
            topInset = insets.top
            behavior.peekHeight = UiUtils.dpToPx(requireContext(), 72) + insets.bottom
            binding.playlistSearchContainer.updatePadding(bottom = insets.bottom)
            binding.recyclerView.updatePadding(top = insets.top)
        }

        val backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) =
                behavior.startBackProgress(backEvent)

            override fun handleOnBackProgressed(backEvent: BackEventCompat) =
                behavior.updateBackProgress(backEvent)

            override fun handleOnBackPressed() = behavior.handleBackInvoked()
            override fun handleOnBackCancelled() = behavior.cancelBackProgress()
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(p0: View, p1: Int) {
                backCallback.isEnabled = p1 == STATE_EXPANDED
            }

            override fun onSlide(p0: View, p1: Float) {
                binding.selectedSongsLayout.translationY = p1 * topInset
                binding.recyclerView.alpha = p1
                binding.bottomSheetDragHandle.alpha = 1 - p1
            }
        })

        val searchFragment = binding.playlistSearchContainer.getFragment<SearchFragment>()
        searchFragment.arguments = Bundle().apply {
            putString("origin", origin)
            putString("feedListener", "playlist_search")
        }
        searchFragment.parentFragmentManager.addFragmentOnAttachListener { _, fragment ->
            val arguments = fragment.arguments ?: Bundle()
            arguments.putAll(searchFragment.arguments ?: Bundle())
            fragment.arguments = arguments
        }

        val adapter = SelectableMediaAdapter { _, item ->
            viewModel.toggleTrack(item as Track)
        }
        GridAdapter.configureGridLayout(binding.recyclerView, adapter, false)
        binding.addTracks.setOnClickListener {
            parentFragmentManager.setFragmentResult("searchedTracks", Bundle().apply {
                Serializer.putSerialized(this, "tracks", viewModel.selectedTracks.value)
            })
            viewModel.selectedTracks.value = emptyList()
            parentFragmentManager.popBackStack()
        }

        ContextUtils.observe(this, viewModel.selectedTracks) { list ->
            val items = list.map {
                it to (it in viewModel.selectedTracks.value)
            }
            adapter.submitList(items)
            binding.addTracks.isEnabled = items.isNotEmpty()
            val tracks = items.size
            binding.selectedSongs.text = runCatching {
                resources.getQuantityString(R.plurals.number_songs, tracks, tracks)
            }.getOrNull() ?: getString(R.string.n_songs, tracks)
        }
    }
}