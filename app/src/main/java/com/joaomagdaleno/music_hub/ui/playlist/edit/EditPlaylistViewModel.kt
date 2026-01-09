package com.joaomagdaleno.music_hub.ui.playlist.edit

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaomagdaleno.music_hub.common.models.*
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class EditPlaylistViewModel(
    private val app: App,
    private val repository: MusicRepository,
    private val extensionId: String,
    private val initial: Playlist,
    private val loaded: Boolean,
    private val selectedTab: String?,
    private val removeIndex: Int,
) : ViewModel() {

    // Stubs for monolithic mode
    val playlistFlow = MutableStateFlow<Playlist?>(initial)
    val tabsFlow = MutableStateFlow<List<Tab>>(emptyList())
    val selectedTabFlow = MutableStateFlow<Tab?>(null)
    val originalList = MutableStateFlow<List<Track>?>(emptyList())
    val currentTracks = MutableStateFlow<List<Track>?>(emptyList())

    fun edit(action: Action<Track>) {
        // TODO: Implement native edit
    }

    val newActions = MutableStateFlow<List<Action<Track>>?>(emptyList())

    data class Data(
        val title: String,
        val desc: String?,
        val coverEditable: Boolean,
        val cover: ImageHolder?,
    )

    val nameFlow = MutableStateFlow(initial.title)
    val descriptionFlow = MutableStateFlow(initial.description)

    sealed interface CoverState {
        data object Initial : CoverState
        data object Removed : CoverState
        data class Changed(val file: File) : CoverState
    }

    val coverFlow = MutableStateFlow<CoverState>(CoverState.Initial)
    val dataFlow = combine(nameFlow, descriptionFlow, coverFlow) { title, desc, cover ->
        Data(title, desc, false, initial.cover)
    }

    sealed interface SaveState {
        data object Initial : SaveState
        data class Performing(val action: Action<Track>, val tracks: List<Track>) : SaveState
        data object Saving : SaveState
        data class Saved(val result: Result<Unit>) : SaveState
    }

    val saveState = MutableStateFlow<SaveState>(SaveState.Initial)
    val isSaveable = MutableStateFlow(false)

    fun save() = viewModelScope.launch {
        saveState.value = SaveState.Saving
        // TODO: Implement native save
        saveState.value = SaveState.Saved(Result.success(Unit))
    }

    fun changeCover(activity: FragmentActivity) = viewModelScope.launch {
        // TODO: Implement native cover change
    }

    init {
        viewModelScope.launch {
            val tracks = repository.getPlaylistTracks(initial.id)
            originalList.value = tracks
            currentTracks.value = tracks
        }
    }

    sealed interface Action<T> {
        data class Add<T>(val index: Int, val items: MutableList<T>) : Action<T>
        data class Remove<T>(val indexes: List<Int>) : Action<T>
        data class Move<T>(val from: Int, val to: Int) : Action<T>
    }
}
