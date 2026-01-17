package com.joaomagdaleno.music_hub.ui.feed

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.common.models.Shelf
import com.joaomagdaleno.music_hub.databinding.FragmentGenericCollapsableBinding
import com.joaomagdaleno.music_hub.databinding.FragmentRecyclerWithRefreshBinding
import com.joaomagdaleno.music_hub.ui.common.GridAdapter
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.common.models.Feed.Companion.toFeed
import com.joaomagdaleno.music_hub.ui.feed.FeedAdapter
import com.joaomagdaleno.music_hub.ui.feed.FeedClickListener
import com.joaomagdaleno.music_hub.ui.main.MainFragment
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.ui.FastScrollerHelper
import kotlinx.coroutines.flow.combine
import org.koin.androidx.viewmodel.ext.android.viewModel

class FeedFragment : Fragment(R.layout.fragment_generic_collapsable) {
    companion object {
        fun getBundle(title: String, subtitle: String?) = Bundle().apply {
            putString("title", title)
            putString("subtitle", subtitle)
        }
    }

    class VM : ViewModel() {
        var initialized = false
        var origin: String? = null
        var feedId: String? = null
        var feed: Feed<Shelf>? = null
    }

    private val activityVm by activityViewModels<VM>()
    private val vm by viewModels<VM>()

    private val feedData by lazy {
        val feedViewModel by viewModel<FeedViewModel>()
        if (!vm.initialized) {
            vm.initialized = true
            vm.origin = activityVm.origin
            vm.feedId = activityVm.feedId
            vm.feed = activityVm.feed
        }
        feedViewModel.getFeedData(
            vm.feedId ?: "",
            cached = { null }
        ) {
            val feedId = vm.feedId!!
            val shelves = getFeed(feedId)
            FeedData.State("internal", null, shelves.toFeed())
        }
    }

    private suspend fun getFeed(id: String): List<Shelf> {
        // Stubbed: Implement actual feed fetching from repository if needed
        return emptyList()
    }

    private val title by lazy { arguments?.getString("title")!! }
    private val subtitle by lazy { arguments?.getString("subtitle") }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentGenericCollapsableBinding.bind(view)
        binding.toolBar.setNavigationOnClickListener { requireActivity().onBackPressed() }
        binding.toolBar.title = title
        binding.toolBar.subtitle = subtitle
        MainFragment.applyPlayerBg(this, view) {
            mainBgDrawable.combine(feedData.backgroundImageFlow) { a, b -> b ?: a }
        }
        if (savedInstanceState == null) childFragmentManager.commit {
            replace<Actual>(R.id.genericFragmentContainer, null, arguments)
        }
    }

    class Actual : Fragment(R.layout.fragment_recycler_with_refresh) {
        private val feedData by lazy {
            val vm by requireParentFragment().viewModel<FeedViewModel>()
            vm.feedDataMap.values.first()
        }

        private val listener by lazy { FeedClickListener.getFeedListener(requireParentFragment()) }
        private val feedAdapter by lazy {
            FeedAdapter.getFeedAdapter(this, feedData, listener)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            val binding = FragmentRecyclerWithRefreshBinding.bind(view)
            UiUtils.applyInsets(this) { insets ->
                UiUtils.applyContentInsets(binding.recyclerView, insets, 20, 8, 16)
            }
            FastScrollerHelper.applyTo(binding.recyclerView)
            GridAdapter.configureGridLayout(
                binding.recyclerView,
                feedAdapter.withLoading(this)
            )
            FeedAdapter.getTouchHelper(listener).attachToRecyclerView(binding.recyclerView)
            binding.swipeRefresh.run {
                UiUtils.configureSwipeRefresh(this)
                setOnRefreshListener { feedData.refresh() }
                ContextUtils.observe(this@Actual, feedData.isRefreshingFlow) { refreshing ->
                    isRefreshing = refreshing
                }
            }
        }
    }
}