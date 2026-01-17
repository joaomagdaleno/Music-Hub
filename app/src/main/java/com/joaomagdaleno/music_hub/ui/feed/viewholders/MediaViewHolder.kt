package com.joaomagdaleno.music_hub.ui.feed.viewholders

import android.content.Context
import android.graphics.drawable.Animatable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Album
import com.joaomagdaleno.music_hub.common.models.Artist
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.common.models.Radio
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.databinding.ItemShelfMediaBinding
import com.joaomagdaleno.music_hub.playback.PlayerState
import com.joaomagdaleno.music_hub.ui.feed.FeedClickListener
import com.joaomagdaleno.music_hub.ui.feed.FeedType
import com.joaomagdaleno.music_hub.ui.media.MediaHeaderAdapter
import com.joaomagdaleno.music_hub.utils.image.ImageUtils

class MediaViewHolder(
    parent: ViewGroup,
    listener: FeedClickListener,
    getAllTracks: (FeedType) -> Pair<List<Track>, Int>,
    private val binding: ItemShelfMediaBinding = ItemShelfMediaBinding.inflate(
        LayoutInflater.from(parent.context), parent, false
    ),
) : FeedViewHolder<FeedType.Media>(binding.root) {
    var feed: FeedType.Media? = null

    init {
        binding.coverContainer.cover.clipToOutline = true
        binding.root.setOnClickListener {
            when (val item = feed?.item) {
                is Track -> {
                    if (item.isPlayable != Track.Playable.Yes) {
                        listener.onMediaClicked(it, feed?.origin, item, feed?.context)
                        return@setOnClickListener
                    }
                    val (tracks, pos) = getAllTracks(feed!!)
                    listener.onTracksClicked(it, feed?.origin, feed?.context, tracks, pos)
                }

                else -> listener.onMediaClicked(it, feed?.origin, item, feed?.context)
            }
        }
        binding.root.setOnLongClickListener {
            listener.onMediaLongClicked(
                it, feed?.origin, feed?.item,
                feed?.context, feed?.tabId, bindingAdapterPosition
            )
            true
        }

        binding.more.setOnClickListener {
            listener.onMediaLongClicked(
                it, feed?.origin, feed?.item,
                feed?.context, feed?.tabId, bindingAdapterPosition
            )
        }
        binding.play.setOnClickListener {
            listener.onPlayClicked(
                it, feed?.origin, feed?.item, null, false
            )
        }
    }

    override fun bind(feed: FeedType.Media) {
        this.feed = feed
        Companion.bind(binding, feed.item, feed.number?.toInt())
    }

    override fun canBeSwiped() = feed?.item is Track
    override fun onSwipe() = feed

    override fun onCurrentChanged(current: PlayerState.Current?) {
        val isPlaying = PlayerState.Current.isPlaying(current, feed?.item?.id)
        binding.coverContainer.isPlaying.isVisible = isPlaying
        (binding.coverContainer.isPlaying.icon as Animatable).start()
    }

    companion object {
        fun bind(binding: ItemShelfMediaBinding, item: EchoMediaItem, index: Int? = null) {
            binding.title.text = if (index == null) item.title
            else binding.root.context.getString(R.string.n_dot_x, index + 1, item.title)
            val subtitleText = subtitle(item, binding.root.context)
            binding.subtitle.text = subtitleText
            binding.subtitle.isVisible = !subtitleText.isNullOrBlank()
            binding.coverContainer.run {
                applyCover(item, cover, listBg1, listBg2, icon)
                isPlaying.setBackgroundResource(
                    if (item is Artist) R.drawable.rounded_rectangle_cover_profile
                    else R.drawable.rounded_rectangle_cover
                )
            }
            binding.play.isVisible = item !is Track
        }

        fun getPlaceHolder(item: EchoMediaItem) = when (item) {
            is Track -> R.drawable.art_music
            is Artist -> R.drawable.art_artist
            is Album -> R.drawable.art_album
            is Playlist -> R.drawable.art_library_music
            is Radio -> R.drawable.art_sensors
        }

        fun getIcon(item: EchoMediaItem) = when (item) {
            is Track -> R.drawable.ic_music
            is Artist -> R.drawable.ic_artist
            is Album -> R.drawable.ic_album
            is Playlist -> R.drawable.ic_library_music
            is Radio -> R.drawable.ic_sensors
        }

        fun applyCover(
            item: EchoMediaItem,
            cover: ImageView,
            listBg1: View,
            listBg2: View,
            icon: ImageView,
        ) {
            icon.isVisible = when (item) {
                is Track, is Artist, is Album -> false
                else -> true
            }
            icon.setImageResource(getIcon(item))
            cover.setBackgroundResource(
                if (item is Artist) R.drawable.rounded_rectangle_cover_profile
                else R.drawable.rounded_rectangle_cover
            )
            val bgVisible = item is EchoMediaItem.Lists
            listBg1.isVisible = bgVisible
            listBg2.isVisible = bgVisible
            cover.updateLayoutParams {
                height = if (item is Artist) width else WRAP_CONTENT
            }
            ImageUtils.loadInto(item.cover, cover, getPlaceHolder(item))
        }

        fun subtitle(item: EchoMediaItem, context: Context) = when (item) {
            is Track -> MediaHeaderAdapter.playableString(item, context) ?: item.subtitleWithE
            else -> item.subtitleWithE
        }
    }
}