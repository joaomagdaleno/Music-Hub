package com.joaomagdaleno.music_hub.ui.feed.viewholders

import android.graphics.Color
import android.graphics.Color.HSVToColor
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Shelf
import com.joaomagdaleno.music_hub.databinding.ItemShelfCategoryBinding
import com.joaomagdaleno.music_hub.ui.feed.FeedClickListener
import com.joaomagdaleno.music_hub.ui.feed.FeedType
import com.joaomagdaleno.music_hub.utils.image.ImageUtils
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import kotlin.math.roundToInt

class CategoryViewHolder(
    parent: ViewGroup,
    listener: FeedClickListener,
    private val binding: ItemShelfCategoryBinding = ItemShelfCategoryBinding.inflate(
        LayoutInflater.from(parent.context), parent, false
    )
) : FeedViewHolder<FeedType.Category>(binding.root) {

    private var feed: FeedType.Category? = null

    init {
        binding.root.setOnClickListener {
            listener.openFeed(
                it,
                feed?.origin,
                feed?.id,
                feed?.shelf?.title,
                feed?.shelf?.subtitle,
                feed?.shelf?.feed
            )
        }
    }

    override fun bind(feed: FeedType.Category) {
        this.feed = feed
        val category = feed.shelf
        Companion.bind(binding, category)
    }

    companion object {
        fun bind(binding: ItemShelfCategoryBinding, category: Shelf.Category) {
            binding.title.text = category.title
            binding.subtitle.text = category.subtitle
            binding.subtitle.isVisible = !category.subtitle.isNullOrEmpty()
            binding.icon.isVisible = category.image != null
            ImageUtils.loadInto(category.image, binding.icon)
            binding.root.run {
                val color = applyBackground(this, category.backgroundColor)
                    ?: ResourcesCompat.getColor(resources, R.color.amoled_fg_semi, null)
                setCardBackgroundColor(color)
            }
        }

        fun applyBackground(view: CardView, hex: String?): Int? {
            val hsv = runCatching { hex?.toColorInt() }.getOrNull()?.run {
                val hsvFloat = FloatArray(3)
                Color.colorToHSV(this, hsvFloat)
                hsvFloat
            } ?: return null
            val actualSat = (hsv[1] * 0.25).roundToInt()
            val sat = if (UiUtils.isNightMode(view.context)) (35f + actualSat) / 100 else 0.2f
            val value = if (UiUtils.isNightMode(view.context)) 0.5f else 0.9f
            val color = HSVToColor(floatArrayOf(hsv[0], sat, value))
            val with = MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary)
            return MaterialColors.harmonize(color, with)
        }
    }
}