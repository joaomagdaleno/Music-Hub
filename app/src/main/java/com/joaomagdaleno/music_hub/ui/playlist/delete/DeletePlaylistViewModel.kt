package com.joaomagdaleno.music_hub.ui.playlist.delete

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.data.repository.MusicRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DeletePlaylistViewModel(
    private val app: App,
    private val repository: MusicRepository,
    private val origin: String,
    private val item: Playlist,
    private val loaded: Boolean,
) : ViewModel() {

    val playlistFlow = MutableStateFlow<Result<Playlist>?>(Result.success(item))
    private val deleteFlow = MutableSharedFlow<Unit>()
    fun delete() = viewModelScope.launch { deleteFlow.emit(Unit) }

    val deleteStateFlow = deleteFlow.transformLatest {
        emit(DeleteState.Deleting)
        // TODO: Implement internal playlist deletion
        emit(DeleteState.Deleted(Result.success(Unit)))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DeleteState.Initial)

    init {
        // Automatically delete if requested?
        // viewModelScope.launch {
        //     deleteFlow.emit(Unit)
        // }
    }
}