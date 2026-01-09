package com.joaomagdaleno.music_hub.playback.source

import android.net.Uri
import androidx.media3.common.MediaItem
import com.joaomagdaleno.music_hub.common.models.Streamable
import com.joaomagdaleno.music_hub.common.models.NetworkRequest
import com.joaomagdaleno.music_hub.common.models.Streamable.Stream.Companion.toStream
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.common.models.MediaState
import com.joaomagdaleno.music_hub.playback.MediaItemUtils
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.backgroundIndex
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.downloaded
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.origin
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.isLoaded
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.serverIndex
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.state
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.track
import com.joaomagdaleno.music_hub.ui.media.MediaHeaderAdapter.Companion.playableString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class StreamableLoader(
    private val app: App,
    private val repository: com.joaomagdaleno.music_hub.data.repository.MusicRepository,
    private val downloadFlow: StateFlow<List<Downloader.Info>>
) {
    suspend fun load(mediaItem: MediaItem) = withContext(Dispatchers.IO) {
        // No need to wait for extensions flow
        val new = if (mediaItem.isLoaded) mediaItem
        else MediaItemUtils.buildLoaded(
            app, downloadFlow.value, mediaItem, loadTrack(mediaItem)
        )

        val server = async { loadOrigin(new) }
        val background = async { null } // Backgrounds not supported yet
        val subtitle = async { null } // Subtitles not supported yet

        MediaItemUtils.buildWithBackgroundAndSubtitle(
            new, background.await(), subtitle.await()
        ) to server.await()
    }

    private suspend fun loadTrack(item: MediaItem): MediaState.Loaded<Track> {
        return when (val state = item.state) {
             is MediaState.Loaded<*> -> state as MediaState.Loaded<Track>
             is MediaState.Unloaded -> {
                 val track = repository.getTrack(state.item.id) ?: throw Exception("Track not found")
                 MediaState.Loaded(
                     origin = state.origin,
                     item = track,
                     isFollowed = null,
                     followers = null,
                     isSaved = null,
                     isLiked = null,
                     isHidden = null,
                     showRadio = true,
                     showShare = true
                 )
             }
        }
    }

    private suspend fun loadServer(mediaItem: MediaItem): Result<Streamable.Media.Server> {
        val downloaded = mediaItem.downloaded
        val servers = mediaItem.track.servers
        val index = mediaItem.serverIndex
        if (!downloaded.isNullOrEmpty() && servers.size == index) {
            return runCatching {
                Streamable.Media.Server(
                    downloaded.map { Uri.fromFile(File(it)).toString().toStream() },
                    true
                )
            }
        }
        return runCatching {
             // Use Repository to get stream URL
             val url = repository.getStreamUrl(mediaItem.track)
             // Create server object
             val stream = Streamable.Stream.Http(
                 request = NetworkRequest(url),
                 quality = 0,
                 format = Streamable.StreamFormat.Progressive
             )
             Streamable.Media.Server(listOf(stream), false)
        }
    }
}