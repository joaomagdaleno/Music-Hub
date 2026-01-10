package com.joaomagdaleno.music_hub.ui.playlist.save

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Message
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.data.repository.MusicRepository
import com.joaomagdaleno.music_hub.di.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SaveToPlaylistViewModel(
    private val origin: String,
    private val item: EchoMediaItem,
    private val app: App,
    private val repository: MusicRepository,
) : ViewModel() {

    // Removed SourceLoader dependency - now using MusicRepository
    // For now, playlist management is stubbed in monolithic mode

    sealed class SaveState {
        data object Initial : SaveState()
        data object LoadingTracks : SaveState()
        data class LoadingPlaylist(val playlist: Playlist) : SaveState()
        data class Saving(val playlist: Playlist, val tracks: List<Track>) : SaveState()
        data class Saved(val success: Boolean) : SaveState()
    }

    val saveFlow = MutableStateFlow<SaveState>(SaveState.Initial)
    fun saveTracks() = viewModelScope.launch(Dispatchers.IO) {
        saveFlow.value = SaveState.LoadingTracks
        val result = runCatching {
            // TODO: Implement internal playlist saving logic
            // For now, this is a stub as per "The Great Purge" instructions
            app.messageFlow.emit(Message(app.context.getString(R.string.saved_to_playlists)))
            true
        }.getOrElse {
            app.throwFlow.emit(it)
            false
        }
        saveFlow.value = SaveState.Saved(result)
    }

    sealed class PlaylistState {
        data object Initial : PlaylistState()
        data object Loading : PlaylistState()
        data class Loaded(val list: List<Pair<Playlist, Boolean>>?) : PlaylistState()
    }

    val playlistsFlow = MutableStateFlow<PlaylistState>(PlaylistState.Initial)
    private suspend fun loadPlaylists(): List<Pair<Playlist, Boolean>> {
        // TODO: Load internal/local playlists
        return emptyList()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            playlistsFlow.value = PlaylistState.Loading
            val result = runCatching { loadPlaylists() }.getOrElse {
                app.throwFlow.emit(it)
                null
            }
            playlistsFlow.value = PlaylistState.Loaded(result)
        }
    }

    fun togglePlaylist(playlist: Playlist) {
        val state = playlistsFlow.value
        if (state !is PlaylistState.Loaded) return
        val newList = state.list?.toMutableList() ?: return
        val index = newList.indexOfFirst { it.first.id == playlist.id }
        if (index == -1) return
        newList[index] = newList[index].copy(second = !newList[index].second)
        playlistsFlow.value = PlaylistState.Loaded(newList)
    }

    fun toggleAll(selected: Boolean) {
        val state = playlistsFlow.value
        if (state !is PlaylistState.Loaded) return
        val newList = state.list?.map { it.copy(second = selected) } ?: return
        playlistsFlow.value = PlaylistState.Loaded(newList)
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            refresh()
        }
    }
}