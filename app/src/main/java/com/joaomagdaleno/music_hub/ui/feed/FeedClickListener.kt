package com.joaomagdaleno.music_hub.ui.feed

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.common.models.Radio
import com.joaomagdaleno.music_hub.common.models.Shelf
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.ui.media.MediaFragment
import com.joaomagdaleno.music_hub.ui.media.MediaFragment.Companion.getBundle
import com.joaomagdaleno.music_hub.ui.media.more.MediaMoreBottomSheet
import com.joaomagdaleno.music_hub.ui.player.PlayerViewModel
import com.joaomagdaleno.music_hub.ui.playlist.edit.search.EditPlaylistSearchClickListener
import org.koin.androidx.viewmodel.ext.android.viewModel

open class FeedClickListener(
    private val fragment: Fragment,
    val fragmentManager: FragmentManager,
    private val containerId: Int,
    private val afterOpen: () -> Unit = {}
) {
    companion object {
        fun getFeedListener(
            fragment: Fragment,
            navFragment: Fragment = fragment,
            afterOpen: () -> Unit = {}
        ): FeedClickListener {
            val key = fragment.arguments?.getString("feedListener")
            return when (key) {
                "playlist_search" -> EditPlaylistSearchClickListener(fragment)
                else -> FeedClickListener(
                    fragment,
                    navFragment.parentFragmentManager,
                    navFragment.id,
                    afterOpen
                )
            }
        }
    }

    open fun onTabSelected(
        view: View?,
        feedId: String?,
        origin: String?,
        position: Int
    ): Boolean {
        val vm by fragment.viewModel<FeedViewModel>()
        val feedData = vm.feedDataMap[feedId] ?: return notFoundSnack(R.string.feed)
        feedData.selectTab(origin, position)
        return true
    }

    open fun onSortClicked(view: View?, feedId: String?): Boolean {
        val vm by fragment.viewModel<FeedViewModel>()
        val feedData = vm.feedDataMap[feedId] ?: return notFoundSnack(R.string.feed)
        feedData.feedSortState.value = feedData.feedSortState.value ?: FeedSort.State()
        FeedSortBottomSheet.newInstance(feedId!!).show(fragment.childFragmentManager, null)
        return true
    }

    open fun onPlayClicked(
        view: View?,
        origin: String?,
        context: EchoMediaItem?,
        tracks: List<Track>?,
        shuffle: Boolean
    ): Boolean {
        if (origin == null) return notFoundSnack(R.string.sources)
        val vm by fragment.activityViewModels<PlayerViewModel>()
        if (tracks != null) {
            if (tracks.isEmpty()) return notFoundSnack(R.string.tracks)
            vm.setQueue(origin, tracks, 0, context)
            vm.setShuffle(shuffle, true)
            vm.setPlaying(true)
            return true
        }
        if (context == null) return notFoundSnack(R.string.item)
        if (shuffle) vm.shuffle(origin, context, true)
        else vm.play(origin, context, true)
        return true
    }

    open fun openFeed(
        view: View?,
        origin: String?,
        feedId: String?,
        title: String?,
        subtitle: String?,
        feed: Feed<Shelf>?
    ): Boolean {
        val fragmentInstance = fragmentManager.findFragmentById(containerId)
            ?: return notFoundSnack(R.string.view)
        val vm by fragmentInstance.activityViewModels<FeedFragment.VM>()
        vm.origin = origin ?: return notFoundSnack(R.string.sources)
        vm.feedId = feedId ?: return notFoundSnack(R.string.item)
        vm.feed = feed ?: return notFoundSnack(R.string.feed)
        UiUtils.openFragment<FeedFragment>(fragmentInstance, view, FeedFragment.getBundle(title.orEmpty(), subtitle))
        afterOpen()
        return true
    }

    fun notFoundSnack(id: Int): Boolean = with(fragment) {
        val notFound = getString(R.string.no_x_found, getString(id))
        UiUtils.createSnack(this, notFound)
        false
    }

    open fun onMediaClicked(
        view: View?, origin: String?, item: EchoMediaItem?, context: EchoMediaItem?
    ): Boolean {
        if (origin == null) return notFoundSnack(R.string.sources)
        if (item == null) return notFoundSnack(R.string.item)
        return when (item) {
            is Radio -> {
                val vm by fragment.activityViewModels<PlayerViewModel>()
                vm.play(origin, item, false)
                true
            }

            else -> {
                val fragmentInstance = fragmentManager.findFragmentById(containerId)
                    ?: return notFoundSnack(R.string.view)
                UiUtils.openFragment<MediaFragment>(fragmentInstance, view, getBundle(origin, item, false))
                afterOpen()
                true
            }
        }
    }

    open fun onMediaLongClicked(
        view: View?, origin: String?, item: EchoMediaItem?, context: EchoMediaItem?,
        tabId: String?, index: Int
    ): Boolean {
        if (origin == null) return notFoundSnack(R.string.sources)
        if (item == null) return notFoundSnack(R.string.item)
        MediaMoreBottomSheet.newInstance(
            containerId, origin, item, false,
            context = context, tabId = tabId, pos = index
        ).show(fragmentManager, null)
        return true
    }

    open fun onTracksClicked(
        view: View?,
        origin: String?,
        context: EchoMediaItem?,
        tracks: List<Track>?,
        pos: Int
    ): Boolean {
        if (origin == null) return notFoundSnack(R.string.sources)
        if (tracks.isNullOrEmpty()) return notFoundSnack(R.string.tracks)
        val vm by fragment.activityViewModels<PlayerViewModel>()
        vm.setQueue(origin, tracks, pos, context)
        vm.setPlaying(true)
        return true
    }

    open fun onTrackSwiped(
        view: View?, origin: String?, track: Track?,
    ): Boolean {
        if (origin == null) return notFoundSnack(R.string.sources)
        if (track == null) return notFoundSnack(R.string.track)
        val vm by fragment.activityViewModels<PlayerViewModel>()
        vm.addToNext(origin, track, false)
        return true
    }
}