package com.joaomagdaleno.music_hub.ui.media

import androidx.lifecycle.viewModelScope
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.common.models.MediaState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MediaViewModel(
    repository: com.joaomagdaleno.music_hub.data.repository.MusicRepository,
    downloader: Downloader,
    val app: App,
    loadFeeds: Boolean,
    val origin: String,
    val item: EchoMediaItem,
    val loaded: Boolean,
) : MediaDetailsViewModel(
    downloader, app, loadFeeds, repository
) {

    override fun getItem(): Triple<String, EchoMediaItem, Boolean> {
        val result = itemResultFlow.value?.getOrNull()?.item
        return Triple(
            origin,
            result ?: item,
            loaded || result != null
        )
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
             // Simply load item logic using Repository
             val loadedItem = when(item) {
                 is com.joaomagdaleno.music_hub.common.models.Track -> repository.getTrack(item.id)
                 // Others need implementing in Repository.
                 // If not implemented, just return the item as is (if we can't fully load it yet).
                 else -> item
             }
             
             if (loadedItem != null) {
                 val state = MediaState.Loaded(origin = "native", item = loadedItem)
                 itemResultFlow.value = Result.success(state)
             }
        }
    }
}
