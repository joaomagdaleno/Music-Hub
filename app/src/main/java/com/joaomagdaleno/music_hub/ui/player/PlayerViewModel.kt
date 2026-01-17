package com.joaomagdaleno.music_hub.ui.player

import android.content.SharedPreferences
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.ThumbRating
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.session.MediaController
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Message
import com.joaomagdaleno.music_hub.common.models.Streamable
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.common.models.MediaState
import com.joaomagdaleno.music_hub.playback.MediaItemUtils
import com.joaomagdaleno.music_hub.playback.PlayerCommands
import com.joaomagdaleno.music_hub.playback.PlayerService
import com.joaomagdaleno.music_hub.playback.PlayerState
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.Serializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max

@OptIn(UnstableApi::class)
class PlayerViewModel(
    val app: App,
    val playerState: PlayerState,
    val settings: SharedPreferences,
    val cache: SimpleCache,
    val repository: com.joaomagdaleno.music_hub.data.repository.MusicRepository,
    downloader: Downloader,
) : ViewModel() {
    private val downloadFlow = downloader.flow

    val browser = MutableStateFlow<MediaController?>(null)
    private fun withBrowser(block: suspend (MediaController) -> Unit) {
        viewModelScope.launch {
            val controller = browser.first { it != null }!!
            block(controller)
        }
    }

    var queue: List<MediaItem> = emptyList()
    val queueFlow = MutableSharedFlow<Unit>()
    private val context = app.context
    val controllerFutureRelease = PlayerService.getController(context) { player ->
        browser.value = player
        player.addListener(PlayerUiListener(player, this))

        if (player.mediaItemCount != 0) return@getController
        if (!settings.getBoolean(KEEP_QUEUE, true)) return@getController

        player.sendCustomCommand(PlayerCommands.resumeCommand, Bundle.EMPTY)
    }

    override fun onCleared() {
        super.onCleared()
        controllerFutureRelease()
    }

    fun play(position: Int) {
        withBrowser {
            it.seekTo(position, 0)
            it.playWhenReady = true
        }
    }

    fun seek(position: Int) {
        withBrowser { it.seekTo(position, 0) }
    }

    fun removeQueueItem(position: Int) {
        withBrowser { it.removeMediaItem(position) }
    }

    fun moveQueueItems(fromPos: Int, toPos: Int) {
        withBrowser { it.moveMediaItem(fromPos, toPos) }
    }

    fun clearQueue() {
        withBrowser { it.clearMediaItems() }
    }

    fun seekTo(pos: Long) {
        withBrowser { it.seekTo(pos) }
    }

    fun seekToAdd(position: Int) {
        withBrowser { it.seekTo(max(0, it.currentPosition + position)) }
    }

    fun setPlaying(isPlaying: Boolean) {
        withBrowser {
            it.prepare()
            it.playWhenReady = isPlaying
        }
    }

    fun next() {
        withBrowser { it.seekToNextMediaItem() }
    }

    fun previous() {
        withBrowser { it.seekToPrevious() }
    }

    fun setShuffle(isShuffled: Boolean, changeCurrent: Boolean = false) {
        withBrowser {
            it.shuffleModeEnabled = isShuffled
            if (changeCurrent) it.seekTo(0, 0)
        }
    }

    fun setRepeat(repeatMode: Int) {
        withBrowser { it.repeatMode = repeatMode }
    }

    suspend fun isLikeClient(origin: String): Boolean = true

    private fun createException(throwable: Throwable) {
        viewModelScope.launch { app.throwFlow.emit(throwable) }
    }

    fun likeCurrent(isLiked: Boolean) = withBrowser { controller ->
        val future = controller.setRating(ThumbRating(isLiked))
        ContextUtils.listenFuture(app.context, future) { sessionResult ->
            sessionResult.getOrElse { createException(it) }
        }
    }

    fun setSleepTimer(timer: Long) {
        withBrowser { it.sendCustomCommand(PlayerCommands.sleepTimer, bundleOf("ms" to timer)) }
    }

    fun changeTrackSelection(trackGroup: TrackGroup, index: Int) {
        withBrowser {
            it.trackSelectionParameters = it.trackSelectionParameters
                .buildUpon()
                .clearOverride(trackGroup)
                .addOverride(TrackSelectionOverride(trackGroup, index))
                .build()
        }
    }

    private fun changeCurrent(newItem: MediaItem) {
        withBrowser { player ->
            val oldPosition = player.currentPosition
            player.replaceMediaItem(player.currentMediaItemIndex, newItem)
            player.prepare()
            player.seekTo(oldPosition)
        }
    }

    fun changeServer(server: Streamable) {
        val item = playerState.current.value?.mediaItem ?: return
        val index = MediaItemUtils.serverWithDownloads(app.context, item).indexOf(server).takeIf { it != -1 }
            ?: return
        changeCurrent(MediaItemUtils.buildServer(item, index))
    }

    fun changeBackground(background: Streamable?) {
        val item = playerState.current.value?.mediaItem ?: return
        val index = MediaItemUtils.getTrack(item).backgrounds.indexOf(background)
        changeCurrent(MediaItemUtils.buildBackground(item, index))
    }

    fun changeSubtitle(subtitle: Streamable?) {
        val item = playerState.current.value?.mediaItem ?: return
        val index = MediaItemUtils.getTrack(item).subtitles.indexOf(subtitle)
        changeCurrent(MediaItemUtils.buildSubtitle(item, index))
    }

    fun changeCurrentSource(index: Int) {
        val item = playerState.current.value?.mediaItem ?: return
        changeCurrent(MediaItemUtils.buildStream(item, index))
    }

    fun setQueue(id: String, list: List<Track>, index: Int, context: EchoMediaItem?) {
        withBrowser { controller ->
            val mediaItems = list.map {
                MediaItemUtils.build(
                    app,
                    downloadFlow.value,
                    MediaState.Unloaded(id, it),
                    context
                )
            }
            controller.setMediaItems(mediaItems, index, (list[index].playedDuration ?: 0L))
            controller.prepare()
        }
    }

    fun radio(id: String, item: EchoMediaItem, loaded: Boolean) = viewModelScope.launch {
        app.messageFlow.emit(
            Message(app.context.getString(R.string.loading_radio_for_x, item.title))
        )
        withBrowser {
            it.sendCustomCommand(PlayerCommands.radioCommand, Bundle().apply {
                putString("origin", id)
                Serializer.putSerialized(this, "item", item)
                putBoolean("loaded", loaded)
            })
        }
    }

    fun play(id: String, item: EchoMediaItem, loaded: Boolean) = viewModelScope.launch {
        if (item !is Track) app.messageFlow.emit(
            Message(app.context.getString(R.string.playing_x, item.title))
        )
        withBrowser {
            it.sendCustomCommand(PlayerCommands.playCommand, Bundle().apply {
                putString("origin", id)
                Serializer.putSerialized(this, "item", item)
                putBoolean("loaded", loaded)
                putBoolean("shuffle", false)
            })
        }
    }

    fun shuffle(id: String, item: EchoMediaItem, loaded: Boolean) = viewModelScope.launch {
        if (item !is Track) app.messageFlow.emit(
            Message(app.context.getString(R.string.shuffling_x, item.title))
        )
        withBrowser {
            it.sendCustomCommand(PlayerCommands.playCommand, Bundle().apply {
                putString("origin", id)
                Serializer.putSerialized(this, "item", item)
                putBoolean("loaded", loaded)
                putBoolean("shuffle", true)
            })
        }
    }


    fun addToQueue(id: String, item: EchoMediaItem, loaded: Boolean) = viewModelScope.launch {
        if (item !is Track) app.messageFlow.emit(
            Message(app.context.getString(R.string.adding_x_to_queue, item.title))
        )
        withBrowser {
            it.sendCustomCommand(PlayerCommands.addToQueueCommand, Bundle().apply {
                putString("origin", id)
                Serializer.putSerialized(this, "item", item)
                putBoolean("loaded", loaded)
            })
        }
    }

    fun addToNext(id: String, item: EchoMediaItem, loaded: Boolean) = viewModelScope.launch {
        if (!(browser.value?.mediaItemCount == 0 && item is Track)) app.messageFlow.emit(
            Message(app.context.getString(R.string.adding_x_to_next, item.title))
        )
        withBrowser {
            it.sendCustomCommand(PlayerCommands.addToNextCommand, Bundle().apply {
                putString("origin", id)
                Serializer.putSerialized(this, "item", item)
                putBoolean("loaded", loaded)
            })
        }
    }

    val progress = MutableStateFlow(0L to 0L)
    val discontinuity = MutableStateFlow(0L)
    val totalDuration = MutableStateFlow<Long?>(null)

    val buffering = MutableStateFlow(false)
    val isPlaying = MutableStateFlow(false)
    val nextEnabled = MutableStateFlow(false)
    val previousEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(0)
    val shuffleMode = MutableStateFlow(false)

    val tracksFlow = MutableStateFlow<Tracks?>(null)
    val serverAndTracks = tracksFlow.combine(playerState.serverChanged) { tracks, _ -> tracks }
        .combine(playerState.current) { tracks, current ->
            val server = playerState.servers[current?.mediaItem?.mediaId]?.getOrNull()
            val index = current?.mediaItem?.let { MediaItemUtils.getStreamIndex(it) } ?: -1
            Triple(tracks, server, index)
        }.stateIn(viewModelScope, SharingStarted.Lazily, Triple(null, null, null))

    companion object {
        const val KEEP_QUEUE = "keep_queue"
    }
}