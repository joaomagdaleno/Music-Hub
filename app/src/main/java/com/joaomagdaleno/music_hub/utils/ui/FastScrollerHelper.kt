package com.joaomagdaleno.music_hub.utils.ui

import android.content.Context
import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.joaomagdaleno.music_hub.utils.ContextUtils
import me.zhanghai.android.fastscroll.FastScroller
import me.zhanghai.android.fastscroll.FastScrollerBuilder

object FastScrollerHelper {
    const val SCROLL_BAR = "scroll_bar"
    
    fun isScrollBarEnabled(context: Context) = ContextUtils.getSettings(context).getBoolean(SCROLL_BAR, false)

    fun applyTo(view: RecyclerView): FastScroller? {
        view.isVerticalScrollBarEnabled = false
        if (!isScrollBarEnabled(view.context)) return null
        return FastScrollerBuilder(view).apply {
            useMd2Style()
            val pad = UiUtils.dpToPx(view.context, 8)
            setPadding(pad, pad, pad, pad)
        }.build()
    }

    fun applyTo(view: NestedScrollView): FastScroller? {
        view.isVerticalScrollBarEnabled = false
        if (!isScrollBarEnabled(view.context)) return null
        return FastScrollerBuilder(view).apply {
            useMd2Style()
            val pad = UiUtils.dpToPx(view.context, 8)
            setPadding(pad, pad, pad, pad)
        }.build()
    }

}
