package com.joaomagdaleno.music_hub.ui.feed.viewholders.shelf

import android.graphics.drawable.Animatable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.joaomagdaleno.music_hub.common.models.Artist
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.databinding.ItemShelfCategoryBinding
import com.joaomagdaleno.music_hub.databinding.ItemShelfListsMediaBinding
import com.joaomagdaleno.music_hub.databinding.ItemShelfListsThreeTracksBinding
import com.joaomagdaleno.music_hub.playback.PlayerState
import com.joaomagdaleno.music_hub.ui.feed.FeedClickListener
import com.joaomagdaleno.music_hub.ui.feed.viewholders.CategoryViewHolder
import com.joaomagdaleno.music_hub.ui.feed.viewholders.MediaViewHolder
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimViewHolder

sealed class ShelfViewHolder<T : ShelfType>(view: View) : ScrollAnimViewHolder(view) {
    var scrollX = 0
    abstract fun bind(index: Int, list: List<T>)
    open fun onCurrentChanged(current: PlayerState.Current?) {}

    class Category(
        parent: ViewGroup,
        listener: FeedClickListener,
        private val binding: ItemShelfCategoryBinding = ItemShelfCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        ),
    ) : ShelfViewHolder<ShelfType.Category>(binding.root) {

        private var item: ShelfType.Category? = null

        init {
            binding.root.setOnClickListener {
                listener.openFeed(
                    it,
                    item?.origin,
                    item?.id,
                    item?.category?.title,
                    item?.category?.subtitle,
                    item?.category?.feed
                )
            }
            binding.root.updateLayoutParams { width = WRAP_CONTENT }
            binding.icon.clipToOutline = true
        }

        override fun bind(index: Int, list: List<ShelfType.Category>) {
            val item = list[index]
            this.item = item
            CategoryViewHolder.bind(binding, item.category)
        }
    }

    class Media(
        parent: ViewGroup,
        listener: FeedClickListener,
        private val binding: ItemShelfListsMediaBinding = ItemShelfListsMediaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        ),
    ) : ShelfViewHolder<ShelfType.Media>(binding.root) {
        var shelf: ShelfType.Media? = null

        init {
            binding.coverContainer.cover.clipToOutline = true
            binding.root.setOnClickListener {
                when (val item = shelf?.media) {
                    is Track -> {
                        if (item.isPlayable != Track.Playable.Yes) {
                            listener.onMediaClicked(it, shelf?.origin, item, shelf?.context)
                        } else listener.onTracksClicked(
                            it, shelf?.origin, shelf?.context, listOf(item), 0
                        )
                    }

                    else -> listener.onMediaClicked(it, shelf?.origin, item, shelf?.context)
                }
            }
            binding.root.setOnLongClickListener {
                listener.onMediaLongClicked(
                    it, shelf?.origin, shelf?.media,
                    shelf?.context, shelf?.tabId, bindingAdapterPosition
                )
                true
            }
        }

        override fun bind(index: Int, list: List<ShelfType.Media>) {
            val shelf = list[index]
            this.shelf = shelf
            bindMedia(binding, shelf.media)
        }

        override fun onCurrentChanged(current: PlayerState.Current?) {
            val isPlaying = PlayerState.Current.isPlaying(current, shelf?.media?.id)
            binding.coverContainer.isPlaying.isVisible = isPlaying
            (binding.coverContainer.isPlaying.icon as Animatable).start()
        }
    }

    class ThreeTracks(
        parent: ViewGroup,
        listener: FeedClickListener,
        getAllTracks: () -> List<Track>,
        binding: ItemShelfListsThreeTracksBinding = ItemShelfListsThreeTracksBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        ),
    ) : ShelfViewHolder<ShelfType.ThreeTracks>(binding.root) {
        private val bindings = listOf(binding.track1, binding.track2, binding.track3)

        private var shelf: ShelfType.ThreeTracks? = null

        init {
            bindings.forEachIndexed { index, binding ->
                binding.coverContainer.cover.clipToOutline = true
                val actualIndex = shelf?.number?.let { it * 3 + index } ?: index
                binding.root.setOnClickListener { view ->
                    val tracks = getAllTracks()
                    val pos = shelf?.number?.let { it * 3 + index } ?: index
                    val track = tracks.getOrNull(index)
                    if (track?.isPlayable != Track.Playable.Yes) listener.onMediaClicked(
                        view, shelf?.origin, track, shelf?.context
                    ) else listener.onTracksClicked(
                        view, shelf?.origin, shelf?.context, tracks, pos
                    )
                }
                binding.root.setOnLongClickListener {
                    listener.onMediaLongClicked(
                        it, shelf?.origin, shelf?.tracks?.toList()?.getOrNull(index),
                        shelf?.context, shelf?.tabId, actualIndex
                    )
                    true
                }
                binding.more.setOnClickListener {
                    listener.onMediaLongClicked(
                        it, shelf?.origin, shelf?.tracks?.toList()?.getOrNull(index),
                        shelf?.context, shelf?.tabId, actualIndex
                    )
                }
            }
        }

        override fun bind(index: Int, list: List<ShelfType.ThreeTracks>) {
            val shelf = list[index]
            this.shelf = shelf
            val number = shelf.number
            val tracks = shelf.tracks.toList()
            bindings.forEachIndexed { index, view ->
                val track = tracks.getOrNull(index)
                view.root.isVisible = track != null
                if (track == null) return@forEachIndexed
                MediaViewHolder.bind(view, track, number?.let { it * 3 + index })
            }
        }

        override fun onCurrentChanged(current: PlayerState.Current?) {
            val tracks = shelf?.tracks?.toList() ?: return
            bindings.forEachIndexed { index, binding ->
                val track = tracks.getOrNull(index) ?: return@forEachIndexed
                val isPlaying = PlayerState.Current.isPlaying(current, track.id)
                binding.coverContainer.isPlaying.isVisible = isPlaying
                (binding.coverContainer.isPlaying.icon as Animatable).start()
            }
        }
    }

    companion object {
        fun bindMedia(binding: ItemShelfListsMediaBinding, item: EchoMediaItem) {
            val context = binding.root.context
            val gravity = if (item is Artist) Gravity.CENTER else Gravity.NO_GRAVITY
            binding.title.text = item.title
            binding.title.gravity = gravity
            val sub = MediaViewHolder.subtitle(item, context)
            binding.subtitle.text = sub
            binding.subtitle.gravity = gravity
            binding.subtitle.isVisible = !sub.isNullOrBlank()
            MediaViewHolder.applyCover(item, binding.coverContainer.cover, binding.coverContainer.listBg1, binding.coverContainer.listBg2, binding.coverContainer.icon)
        }
    }
}