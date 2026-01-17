package com.joaomagdaleno.music_hub.ui.player

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.joaomagdaleno.music_hub.databinding.FragmentPlayerBinding
import com.joaomagdaleno.music_hub.ui.common.UiViewModel
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils
import kotlin.math.max
import kotlin.math.min
import androidx.fragment.app.FragmentActivity

object PlayerAnimations {
    fun configurePlayerOutline(
        view: View,
        uiViewModel: UiViewModel,
        lifecycleOwner: LifecycleOwner,
        collapseHeight: Int
    ) {
        val context = view.context
        val padding = UiUtils.dpToPx(context, 8)
        var currHeight = collapseHeight
        var currRound = padding.toFloat()
        var currRight = 0
        var currLeft = 0
        
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(
                    currLeft, 0, currRight, currHeight, currRound
                )
            }
        }
        view.clipToOutline = true

        var leftPadding = 0
        var rightPadding = 0

        val maxElevation = UiUtils.dpToPx(context, 4).toFloat()
        fun updateOutline() {
            val offset = max(0f, uiViewModel.playerSheetOffset.value)
            val inv = 1 - offset
            val curve = offset * offset // Non-linear for snappier finish
            val invCurve = 1 - curve
            
            view.elevation = maxElevation * inv
            currHeight = collapseHeight + ((view.height - collapseHeight) * offset).toInt()
            currLeft = (leftPadding * invCurve).toInt()
            currRight = view.width - (rightPadding * invCurve).toInt()
            currRound = max(padding * invCurve, padding * uiViewModel.playerBackProgress.value * 2)
            view.invalidateOutline()
        }
        ContextUtils.observe(lifecycleOwner, uiViewModel.combined) {
            leftPadding = (if (UiUtils.isRTL(context)) it.end else it.start) + padding
            rightPadding = (if (UiUtils.isRTL(context)) it.start else it.end) + padding
            updateOutline()
        }
        ContextUtils.observe(lifecycleOwner, uiViewModel.playerBackProgress) { updateOutline() }
        ContextUtils.observe(lifecycleOwner, uiViewModel.playerSheetOffset) { updateOutline() }
        view.doOnLayout { updateOutline() }
    }

    fun configurePlayerCollapsing(
        binding: FragmentPlayerBinding,
        uiViewModel: UiViewModel,
        viewModel: PlayerViewModel,
        adapter: PlayerTrackAdapter,
        lifecycleOwner: LifecycleOwner,
        collapseHeight: Int,
        activity: FragmentActivity
    ) {
        binding.playerCollapsedContainer.root.clipToOutline = true

        val context = binding.root.context
        val collapsedTopPadding = UiUtils.dpToPx(context, 8)
        var currRound = collapsedTopPadding.toFloat()
        var currTop = 0
        var currBottom = collapseHeight
        var currRight = 0
        var currLeft = 0

        val view = binding.viewPager
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(
                    currLeft, currTop, currRight, currBottom, currRound
                )
            }
        }
        view.clipToOutline = true

        val extraEndPadding = UiUtils.dpToPx(context, 108)
        var leftPadding = 0
        var rightPadding = 0
        val isLandscape = UiUtils.isLandscape(context)
        
        fun updateCollapsed() {
            val (collapsedY, offset, collapsedOffset) = uiViewModel.run {
                if (playerSheetState.value == STATE_EXPANDED) {
                    val offset = moreSheetOffset.value
                    Triple(systemInsets.value.top, offset, if (isLandscape) 0f else offset)
                } else {
                    val offset = 1 - max(0f, playerSheetOffset.value)
                    Triple(-collapsedTopPadding, offset, offset)
                }
            }
            val collapsedInv = 1 - collapsedOffset
            binding.playerCollapsedContainer.root.run {
                translationY = (collapsedY - collapseHeight * collapsedInv * 2).toFloat()
                alpha = (collapsedOffset * 2).toFloat()
                translationZ = -1f * collapsedInv
            }
            binding.bgCollapsed.run {
                translationY = (collapsedY - collapseHeight * collapsedInv * 2).toFloat()
                alpha = (min(1f, collapsedOffset * 2) - 0.5).toFloat()
            }
            val alphaInv = 1 - min(1f, offset * 3)
            binding.expandedToolbar.run {
                translationY = (collapseHeight * offset * 2).toFloat()
                alpha = alphaInv
                isVisible = offset < 1
                translationZ = -1f * offset
            }
            binding.playerControls.root.run {
                translationY = (collapseHeight * offset * 2).toFloat()
                alpha = alphaInv
                isVisible = offset < 1
            }
            currTop = uiViewModel.run {
                val top = if (playerSheetState.value != STATE_EXPANDED) 0
                else collapsedTopPadding + systemInsets.value.top
                (top * max(0f, (collapsedOffset - 0.75f) * 4)).toInt()
            }
            val bot = currTop + collapseHeight
            currBottom = bot + ((view.height - bot) * collapsedInv).toInt()
            currLeft = (leftPadding * collapsedOffset).toInt()
            currRight = (view.width - (rightPadding * collapsedOffset)).toInt()
            currRound = (collapsedTopPadding * collapsedOffset).toFloat()
            view.invalidateOutline()
        }

        view.doOnLayout { updateCollapsed() }
        ContextUtils.observe(lifecycleOwner, uiViewModel.combined) {
            val system = uiViewModel.systemInsets.value
            UiUtils.applyInsets(binding.constraintLayout, system, 64, 0)
            UiUtils.applyInsets(binding.expandedToolbar, system)
            val insets = uiViewModel.run {
                if (playerSheetState.value == STATE_EXPANDED) system
                else getCombined()
            }
            UiUtils.applyHorizontalInsets(binding.playerCollapsedContainer.root, insets)
            UiUtils.applyHorizontalInsets(
                binding.playerControls.root,
                insets,
                UiUtils.isLandscape(activity)
            )
            val left = if (UiUtils.isRTL(context)) system.end + extraEndPadding else system.start
            leftPadding = collapsedTopPadding + left
            val right = if (UiUtils.isRTL(context)) system.start else system.end + extraEndPadding
            rightPadding = collapsedTopPadding + right
            updateCollapsed()
            adapter.insetsUpdated()
        }

        ContextUtils.observe(lifecycleOwner, uiViewModel.moreSheetOffset) {
            updateCollapsed()
            adapter.moreOffsetUpdated()
        }
        ContextUtils.observe(lifecycleOwner, uiViewModel.playerSheetOffset) {
            updateCollapsed()
            adapter.playerOffsetUpdated()

            viewModel.browser.value?.volume = 1 + min(0f, it)
            if (it < 1)
                UiUtils.hideSystemUi(activity, false)
            else if (uiViewModel.playerBgVisible.value)
                UiUtils.hideSystemUi(activity, true)
        }

        ContextUtils.observe(lifecycleOwner, uiViewModel.playerSheetState) {
            updateCollapsed()
            if (UiUtils.isFinalState(it)) adapter.playerSheetStateUpdated()
            if (it == STATE_HIDDEN) viewModel.clearQueue()
            else if (it == STATE_COLLAPSED) ContextUtils.emit(lifecycleOwner, uiViewModel.playerBgVisible, false)
        }

        binding.playerControls.root.doOnLayout {
            uiViewModel.playerControlsHeight.value = it.height
            adapter.playerControlsHeightUpdated()
        }
        ContextUtils.observe(lifecycleOwner, uiViewModel.playerBgVisible) {
            AnimationUtils.animateVisibility(binding.fgContainer, !it)
            AnimationUtils.animateVisibility(binding.playerMoreContainer, !it)
            UiUtils.hideSystemUi(activity, it)
        }
        // Note: Listeners are set in the Fragment as they involve navigation/callbacks, or can be passed if simple.
        // For now, these were part of configureCollapsing in original code, so keeping them here
        binding.playerCollapsedContainer.playerClose.setOnClickListener {
            uiViewModel.changePlayerState(STATE_HIDDEN)
        }
        binding.expandedToolbar.setNavigationOnClickListener {
            uiViewModel.collapsePlayer()
        }
    }
}
