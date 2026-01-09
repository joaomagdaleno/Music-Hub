package com.joaomagdaleno.music_hub.ui.playlist.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaomagdaleno.music_hub.di.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class CreatePlaylistViewModel(
    val app: App,
) : ViewModel() {
    val createPlaylistStateFlow =
        MutableStateFlow<CreateState>(CreateState.CreatePlaylist)
    fun createPlaylist(title: String, desc: String?) {
        createPlaylistStateFlow.value = CreateState.Creating
        viewModelScope.launch(Dispatchers.IO) {
            // TODO: Implement native playlist creation
            // For now, this is a stub as per "The Great Purge" instructions
            createPlaylistStateFlow.value = CreateState.PlaylistCreated("native", null)
        }
    }
}