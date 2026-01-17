package com.joaomagdaleno.music_hub.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.common.models.Shelf
import com.joaomagdaleno.music_hub.data.repository.MusicRepository
import com.joaomagdaleno.music_hub.di.App
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

class FeedViewModel(
    val app: App,
    private val repository: MusicRepository,
) : ViewModel() {
    val feedDataMap = hashMapOf<String, FeedData>()
    
    fun getFeedData(
        id: String,
        buttons: Feed.Buttons = Feed.Buttons(),
        noVideos: Boolean = false,
        vararg extraLoadFlow: Flow<*>,
        cached: suspend (MusicRepository) -> FeedData.State<Feed<Shelf>>? = { null },
        loader: suspend (MusicRepository) -> FeedData.State<Feed<Shelf>>?
    ): FeedData {
        return feedDataMap.getOrPut(id) {
            FeedData(
                feedId = id,
                scope = viewModelScope,
                app = app,
                repository = repository,
                cached = cached,
                load = loader,
                defaultButtons = buttons,
                noVideos = noVideos,
                extraLoadFlow = extraLoadFlow.toList().merge(),
            )
        }
    }
}