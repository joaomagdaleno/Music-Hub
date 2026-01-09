package com.joaomagdaleno.music_hub.playback

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.core.os.bundleOf
import androidx.media3.common.Player
import androidx.media3.common.Rating
import androidx.media3.common.ThumbRating
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionResult.RESULT_SUCCESS
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.helpers.PagedData
import com.joaomagdaleno.music_hub.common.models.Album
import com.joaomagdaleno.music_hub.common.models.Artist
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Feed.Companion.pagedDataOfFirst
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.common.models.Radio
import com.joaomagdaleno.music_hub.common.models.Shelf
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.common.models.MediaState
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.origin
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.track
import com.joaomagdaleno.music_hub.playback.ResumptionUtils.recoverPlaylist
import com.joaomagdaleno.music_hub.playback.ResumptionUtils.recoverRepeat
import com.joaomagdaleno.music_hub.playback.ResumptionUtils.recoverShuffle
import com.joaomagdaleno.music_hub.playback.ResumptionUtils.recoverTracks
import com.joaomagdaleno.music_hub.playback.exceptions.PlayerException
import com.joaomagdaleno.music_hub.playback.listener.PlayerRadio
import com.joaomagdaleno.music_hub.utils.CoroutineUtils.future
import com.joaomagdaleno.music_hub.utils.Serializer.getSerialized
import com.joaomagdaleno.music_hub.utils.image.ImageUtils.loadDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(UnstableApi::class)
class PlayerCallback(
    override val app: App,
    override val scope: CoroutineScope,
    private val throwableFlow: MutableSharedFlow<Throwable>,
    private val repository: com.joaomagdaleno.music_hub.data.repository.MusicRepository,
    private val radioFlow: MutableStateFlow<PlayerState.Radio>,
    override val downloadFlow: StateFlow<List<Downloader.Info>>,
) : AndroidAutoCallback(app, scope, kotlinx.coroutines.flow.MutableStateFlow(emptyList()), downloadFlow) {

    override fun onConnect(
        session: MediaSession, controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val sessionCommands = with(PlayerCommands) {
            MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(likeCommand).add(unlikeCommand).add(repeatCommand).add(repeatOffCommand)
                .add(repeatOneCommand).add(radioCommand).add(sleepTimer)
                .add(playCommand).add(addToQueueCommand).add(addToNextCommand)
                .add(resumeCommand).add(imageCommand)
                .build()
        }
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands).build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> = with(PlayerCommands) {
        val player = session.player
        when (customCommand) {
            likeCommand -> onSetRating(session, controller, ThumbRating(true))
            unlikeCommand -> onSetRating(session, controller, ThumbRating())
            repeatOffCommand -> setRepeat(player, Player.REPEAT_MODE_OFF)
            repeatOneCommand -> setRepeat(player, Player.REPEAT_MODE_ONE)
            repeatCommand -> setRepeat(player, Player.REPEAT_MODE_ALL)
            playCommand -> playItem(player, args)
            addToQueueCommand -> addToQueue(player, args)
            addToNextCommand -> addToNext(player, args)
            radioCommand -> radio(player, args)
            sleepTimer -> onSleepTimer(player, args.getLong("ms"))
            resumeCommand -> resume(player, args.getBoolean("cleared", true))
            imageCommand -> getImage(player)
            else -> super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private fun getImage(player: Player) = scope.future {
        val item = player.with { currentMediaItem }
            ?: context.recoverPlaylist(app, downloadFlow.value, false).run { first.getOrNull(second) }
            ?: return@future SessionResult(SessionError.ERROR_UNKNOWN)
        val image = item.track.cover.loadDrawable(context)?.toScaledBitmap(720)
        SessionResult(RESULT_SUCCESS, Bundle().apply { putParcelable("image", image) })
    }

    private fun Drawable.toScaledBitmap(width: Int) = toBitmap().let { bmp ->
        val ratio = width.toFloat() / bmp.width
        val height = (bmp.height * ratio).toInt()
        bmp.scale(width, height)
    }

    private fun resume(player: Player, withClear: Boolean) = scope.future {
        withContext(Dispatchers.Main) {
            player.shuffleModeEnabled = context.recoverShuffle() == true
            player.repeatMode = context.recoverRepeat() ?: Player.REPEAT_MODE_OFF
        }
        val (items, index, pos) = context.recoverPlaylist(app,downloadFlow.value, withClear)
        withContext(Dispatchers.Main) {
            player.setMediaItems(items, index, pos)
            player.prepare()
        }
        SessionResult(RESULT_SUCCESS)
    }

    private var timerJob: Job? = null
    private fun onSleepTimer(player: Player, ms: Long): ListenableFuture<SessionResult> {
        timerJob?.cancel()
        val time = when (ms) {
            0L -> return Futures.immediateFuture(SessionResult(RESULT_SUCCESS))
            Long.MAX_VALUE -> player.run { duration - currentPosition }
            else -> ms
        }

        timerJob = scope.launch {
            delay(time)
            player.with { pause() }
        }
        return Futures.immediateFuture(SessionResult(RESULT_SUCCESS))
    }

    private fun setRepeat(player: Player, repeat: Int) = run {
        player.repeatMode = repeat
        Futures.immediateFuture(SessionResult(RESULT_SUCCESS))
    }


    @OptIn(UnstableApi::class)
    private fun radio(player: Player, args: Bundle) = scope.future {
        val error = SessionResult(SessionError.ERROR_UNKNOWN)
        val origin = args.getString("origin") ?: return@future error
        val item = args.getSerialized<EchoMediaItem>("item")?.getOrNull() ?: return@future error
        // Use repository for radio instead of sources
        val tracks = when (item) {
            is Track -> repository.getRadio(item.id)
            else -> emptyList()
        }
        if (tracks.isEmpty()) return@future error
        radioFlow.value = PlayerState.Radio.Loading
        val mediaItems = tracks.map { track ->
            MediaItemUtils.build(
                app, downloadFlow.value, MediaState.Unloaded("internal", track), item
            )
        }
        player.with {
            clearMediaItems()
            shuffleModeEnabled = false
            setMediaItems(mediaItems)
            prepare()
            play()
        }
        radioFlow.value = PlayerRadio.start(repository, item) ?: PlayerState.Radio.Empty
        SessionResult(RESULT_SUCCESS)
    }

    // Removed source-based loadItem - use repository instead
    private suspend fun loadItem(item: EchoMediaItem): EchoMediaItem = when (item) {
        is Track -> repository.getTrack(item.id) ?: item
        is Album -> repository.getAlbum(item.id)
        is Playlist -> repository.getPlaylist(item.id) ?: item
        is Artist -> repository.getArtist(item.id)
        is Radio -> item
    }

    // Removed source-based listTracks - use repository instead
    private suspend fun listTracks(item: EchoMediaItem): List<Track> = when (item) {
        is Album -> repository.getAlbumTracks(item.id)
        is Playlist -> repository.getPlaylistTracks(item.id)
        is Artist -> repository.getArtistTracks(item.id)
        is Track -> listOf(item)
        is Radio -> repository.getRadio(item.id)
    }

    private fun playItem(player: Player, args: Bundle) = scope.future {
        val error = SessionResult(SessionError.ERROR_UNKNOWN)
        val origin = args.getString("origin") ?: "internal"
        val item = args.getSerialized<EchoMediaItem>("item")?.getOrNull() ?: return@future error
        val loaded = args.getBoolean("loaded", false)
        val shuffle = args.getBoolean("shuffle", false)
        
        when (item) {
            is Track -> {
                val mediaItem = MediaItemUtils.build(
                    app, downloadFlow.value, MediaState.Unloaded(origin, item), null
                )
                player.with {
                    setMediaItem(mediaItem)
                    prepare()
                    seekTo(item.playedDuration ?: 0)
                    play()
                }
            }

            else -> {
                val trackList = listTracks(item)
                if (trackList.isEmpty()) return@future error
                
                val list = if (shuffle) trackList.shuffled() else trackList
                player.with {
                    setMediaItems(list.map {
                        MediaItemUtils.build(
                            app, downloadFlow.value, MediaState.Unloaded(origin, it), item
                        )
                    })
                    shuffleModeEnabled = shuffle
                    seekTo(0, list.firstOrNull()?.playedDuration ?: 0)
                    play()
                }
            }
        }
        SessionResult(RESULT_SUCCESS)
    }

    private suspend fun <T> Player.with(block: suspend Player.() -> T): T =
        withContext(Dispatchers.Main) { block() }

    private suspend fun <T : Any> PagedData<T>.load(
        pages: Int = 5,
    ) = runCatching {
        val list = mutableListOf<T>()
        var page = loadPage(null)
        list.addAll(page.data)
        var count = 0
        while (page.continuation != null && count < pages) {
            page = loadPage(page.continuation)
            list.addAll(page.data)
            count++
        }
        list
    }

    private fun addToQueue(player: Player, args: Bundle) = scope.future {
        val error = SessionResult(SessionError.ERROR_UNKNOWN)
        val origin = args.getString("origin") ?: "internal"
        val item = args.getSerialized<EchoMediaItem>("item")?.getOrNull() ?: return@future error
        
        val tracks = listTracks(item)
        if (tracks.isEmpty()) return@future error
        val mediaItems = tracks.map { track ->
            MediaItemUtils.build(
                app,
                downloadFlow.value,
                MediaState.Unloaded(origin, track),
                null
            )
        }
        player.with {
            addMediaItems(mediaItems)
            prepare()
        }
        SessionResult(RESULT_SUCCESS)
    }

    private var next = 0
    private var nextJob: Job? = null
    private fun addToNext(player: Player, args: Bundle) = scope.future {
        val error = SessionResult(SessionError.ERROR_UNKNOWN)
        val origin = args.getString("origin") ?: "internal"
        val item = args.getSerialized<EchoMediaItem>("item")?.getOrNull() ?: return@future error
        nextJob?.cancel()
        
        val tracks = listTracks(item)
        if (tracks.isEmpty()) return@future error
        val mediaItems = tracks.map { track ->
            MediaItemUtils.build(
                app,
                downloadFlow.value,
                MediaState.Unloaded(origin, track),
                null
            )
        }
        player.with {
            if (mediaItemCount == 0) playWhenReady = true
            addMediaItems(currentMediaItemIndex + 1 + next, mediaItems)
            prepare()
        }
        next += mediaItems.size
        nextJob = scope.launch {
            delay(5000)
            next = 0
        }
        SessionResult(RESULT_SUCCESS)
    }

    override fun onSetRating(
        session: MediaSession, controller: MediaSession.ControllerInfo, rating: Rating,
    ): ListenableFuture<SessionResult> {
        return if (rating !is ThumbRating) super.onSetRating(session, controller, rating)
        else scope.future {
            val item = session.player.with { currentMediaItem }
                ?: return@future SessionResult(SessionError.ERROR_UNKNOWN)
            val track = item.track

            // Toggle like in database
            val liked = rating.isThumbsUp
            if (liked != repository.isLiked(track)) {
                repository.toggleLike(track)
            }

            val newItem = item.run {
                buildUpon().setMediaMetadata(
                    mediaMetadata.buildUpon().setUserRating(ThumbRating(liked)).build()
                )
            }.build()
            session.player.with {
                replaceMediaItem(currentMediaItemIndex, newItem)
            }
            SessionResult(RESULT_SUCCESS, bundleOf("liked" to liked))
        }
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
    ) = scope.future {
        withContext(Dispatchers.Main) {
            mediaSession.player.shuffleModeEnabled = context.recoverShuffle() ?: false
            mediaSession.player.repeatMode = context.recoverRepeat() ?: Player.REPEAT_MODE_OFF
        }
        val (items, index, pos) = context.recoverPlaylist(app, downloadFlow.value)
        MediaItemsWithStartPosition(items, index, pos)
    }

    class ButtonReceiver : MediaButtonReceiver() {
        override fun shouldStartForegroundService(context: Context, intent: Intent): Boolean {
            val isEmpty = context.recoverTracks().isNullOrEmpty()
            if (isEmpty) Toast.makeText(
                context,
                context.getString(R.string.no_last_played_track_found),
                Toast.LENGTH_SHORT
            ).show()
            return !isEmpty
        }
    }

    companion object {
        fun PagedData<Shelf>.toTracks() = map {
            it.getOrThrow().mapNotNull { shelf ->
                when (shelf) {
                    is Shelf.Category -> null
                    is Shelf.Item -> listOfNotNull(shelf.media as? Track)
                    is Shelf.Lists.Categories -> null
                    is Shelf.Lists.Items -> shelf.list.filterIsInstance<Track>()
                    is Shelf.Lists.Tracks -> shelf.list
                }
            }.flatten()
        }
    }
}