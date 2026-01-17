package com.joaomagdaleno.music_hub.utils.ui

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback

object ViewPager2Utils {

    fun supportBottomSheetBehavior(viewPager: ViewPager2) {
        val recycler = viewPager.getChildAt(0) as RecyclerView
        recycler.run {
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
    }

    fun registerOnUserPageChangeCallback(
        viewPager: ViewPager2,
        listener: (position: Int, userInitiated: Boolean) -> Unit
    ) {
        var previousState: Int = -1
        var userScrollChange = false
        viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {

            override fun onPageSelected(position: Int) {
                listener(position, userScrollChange)
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (previousState == ViewPager.SCROLL_STATE_DRAGGING &&
                    state == ViewPager.SCROLL_STATE_SETTLING
                ) {
                    userScrollChange = true
                } else if (previousState == ViewPager.SCROLL_STATE_SETTLING &&
                    state == ViewPager.SCROLL_STATE_IDLE
                ) {
                    userScrollChange = false
                }
                previousState = state
            }
        })
    }
}