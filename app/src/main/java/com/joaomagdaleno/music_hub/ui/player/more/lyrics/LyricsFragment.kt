package com.joaomagdaleno.music_hub.ui.player.more.lyrics

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.CONSUMED
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.behavior.HideViewOnScrollBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.transition.MaterialSharedAxis
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Lyrics
import com.joaomagdaleno.music_hub.databinding.FragmentPlayerLyricsBinding
import com.joaomagdaleno.music_hub.databinding.ItemLyricsItemBinding
import com.joaomagdaleno.music_hub.ui.common.UiViewModel
import com.joaomagdaleno.music_hub.ui.player.PlayerColors.Companion.defaultPlayerColors
import com.joaomagdaleno.music_hub.ui.player.PlayerViewModel
import com.joaomagdaleno.music_hub.utils.ContextUtils.observe
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils.setupTransition
import com.joaomagdaleno.music_hub.utils.ui.AutoClearedValue.Companion.autoCleared
import com.joaomagdaleno.music_hub.utils.ui.FastScrollerHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class LyricsFragment : Fragment() {

    private var binding by autoCleared<FragmentPlayerLyricsBinding>()
    private val viewModel by activityViewModel<LyricsViewModel>()
    private val playerVM by activityViewModel<PlayerViewModel>()
    private val uiViewModel by activityViewModel<UiViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        binding = FragmentPlayerLyricsBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var currentLyricsPos = -1
    private var currentLyrics: Lyrics.Lyric? = null
    private val lyricAdapter by lazy {
        LyricAdapter(uiViewModel) { adapter, lyric ->
            if (adapter.itemCount <= 1) return@LyricAdapter
            currentLyricsPos = -1
            playerVM.seekTo(lyric.startTime)
            updateLyrics(lyric.startTime)
        }
    }

    private var shouldAutoScroll = true
    val layoutManager by lazy {
        binding.lyricsRecyclerView.layoutManager as LinearLayoutManager
    }

    private fun updateLyrics(current: Long) {
        val lyrics = currentLyrics as? Lyrics.Timed ?: return
        val currentIndex = lyrics.list.indexOfLast { lyric ->
            lyric.startTime <= current
        }
        lyricAdapter.updateCurrent(currentIndex)
        if (!shouldAutoScroll) return
        binding.appBarLayout.setExpanded(false)
        slideDown()
        if (currentIndex < 0) return
        val smoothScroller = CenterSmoothScroller(requireContext())
        smoothScroller.targetPosition = currentIndex
        layoutManager.startSmoothScroll(smoothScroller)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupTransition(view, false, axis = MaterialSharedAxis.Y)
        FastScrollerHelper.applyTo(binding.lyricsRecyclerView)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, _ -> CONSUMED }
        observe(uiViewModel.moreSheetState) {
            binding.root.keepScreenOn = it == BottomSheetBehavior.STATE_EXPANDED
        }

        // Search and source selection removed in monolithic mode
        binding.searchBarText.isVisible = false

        var job: Job? = null
        binding.lyricsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) return
                shouldAutoScroll = false
                job?.cancel()
                job = lifecycleScope.launch {
                    delay(3500)
                    shouldAutoScroll = true
                }
            }
        })

        observe(uiViewModel.playerColors) {
            lyricAdapter.updateColors()
            val colors = it ?: requireContext().defaultPlayerColors()
            binding.noLyrics.setTextColor(colors.onBackground)
        }

        binding.lyricsRecyclerView.adapter = lyricAdapter
        binding.lyricsRecyclerView.itemAnimator = null
        observe(viewModel.lyricsState) { state ->
            binding.noLyrics.isVisible = state == LyricsViewModel.State.Empty
            
            val lyricsItem = (state as? LyricsViewModel.State.Loaded)?.result?.getOrNull()
            binding.lyricsItem.bind(lyricsItem)
            currentLyricsPos = -1
            currentLyrics = lyricsItem?.lyrics
            val list = when (val lyrics = currentLyrics) {
                is Lyrics.Simple -> listOf(Lyrics.Item(lyrics.text, 0, 0))
                is Lyrics.Timed -> lyrics.list
                is Lyrics.WordByWord -> lyrics.list.flatten()
                null -> emptyList()
            }
            lyricAdapter.submitList(list)
        }

        observe(playerVM.progress) { updateLyrics(it.first) }
    }

    fun ItemLyricsItemBinding.bind(lyrics: Lyrics?) = root.run {
        if (lyrics == null) {
            isVisible = false
            return
        }
        isVisible = true
        setTitle(lyrics.title)
        setSubtitle(lyrics.subtitle)
        setBackgroundResource(R.color.amoled_bg)
    }

    class CenterSmoothScroller(context: Context) : LinearSmoothScroller(context) {
        override fun calculateDtToFit(
            viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int,
        ): Int {
            val midPoint = boxEnd / 2
            val targetMidPoint = ((viewEnd - viewStart) / 2) + viewStart
            return midPoint - targetMidPoint
        }

        override fun getVerticalSnapPreference() = SNAP_TO_START
        override fun calculateTimeForDeceleration(dx: Int) = 650
    }

    @SuppressLint("WrongConstant")
    private fun slideDown() {
        val params = binding.lyricsItem.root.layoutParams as CoordinatorLayout.LayoutParams
        val behavior = params.behavior as HideViewOnScrollBehavior
        behavior.setViewEdge(1)
        behavior.slideOut(binding.lyricsItem.root)
    }
}