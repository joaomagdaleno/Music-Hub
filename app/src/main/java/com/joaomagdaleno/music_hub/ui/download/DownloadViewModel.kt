package com.joaomagdaleno.music_hub.ui.download

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.DownloadContext
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Message
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.ui.common.FragmentUtils.openFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadViewModel(
    app: App,
    private val repository: com.joaomagdaleno.music_hub.data.repository.MusicRepository,
    val downloader: Downloader,
) : ViewModel() {

    private val app = app.context
    private val messageFlow = app.messageFlow
    private val throwableFlow = app.throwFlow

    // Deprecated extension properties
    // val extensions = extensionLoader // Removed

    val flow = downloader.flow

    fun addToDownload(
        activity: FragmentActivity,
        extensionId: String,
        item: EchoMediaItem,
        context: EchoMediaItem?,
    ) = viewModelScope.launch(Dispatchers.IO) {
        with(activity) {
            messageFlow.emit(Message(getString(R.string.downloading_x, item.title)))
            
            // Native download logic:
            val tracks = when(item) {
                is com.joaomagdaleno.music_hub.common.models.Track -> listOf(item)
                is com.joaomagdaleno.music_hub.common.models.Album -> repository.getAlbumTracks(item.id)
                is com.joaomagdaleno.music_hub.common.models.Playlist -> repository.getPlaylistTracks(item.id)
                else -> emptyList()
            }

            if (tracks.isEmpty()) return@with messageFlow.emit(
                Message(app.getString(R.string.nothing_to_download_in_x, item.title))
            )

            val downloads = tracks.mapIndexed { index, track ->
                 DownloadContext(extensionId = "native", track = track, sortOrder = index, context = context)
            }

            downloader.add(downloads)
            messageFlow.emit(
                Message(
                    getString(R.string.download_started),
                    Message.Action(getString(R.string.view)) {
                        openFragment<DownloadFragment>()
                    }
                )
            )
        }
    }

    fun cancel(trackId: Long) {
        downloader.cancel(trackId)
    }

    fun restart(trackId: Long) {
        downloader.restart(trackId)
    }

    fun cancelAll() {
        downloader.cancelAll()
    }

    fun deleteDownload(item: EchoMediaItem) {
        when (item) {
            is Track -> downloader.deleteDownload(item.id)
            else -> downloader.deleteContext(item.id)
        }
        viewModelScope.launch {
            messageFlow.emit(
                Message(app.getString(R.string.removed_x_from_downloads, item.title))
            )
        }
    }
}