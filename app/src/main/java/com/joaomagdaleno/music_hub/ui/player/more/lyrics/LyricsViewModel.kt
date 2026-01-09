package com.joaomagdaleno.music_hub.ui.player.more.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaomagdaleno.music_hub.common.models.Lyrics
import com.joaomagdaleno.music_hub.common.models.Tab
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.playback.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LyricsViewModel(
    private val app: App,
    playerState: PlayerState,
) : ViewModel() {

    private val refreshFlow = MutableSharedFlow<Unit>()
    val queryFlow = MutableStateFlow("")
    val selectedTabIndexFlow = MutableStateFlow(-1)
    val lyricsState = MutableStateFlow<State>(State.Initial)
    val tabsFlow = MutableStateFlow<List<Tab>>(emptyList())
    val shouldShowEmpty = lyricsState.map { it is State.Empty }

    private val mediaFlow = playerState.current.map { current ->
        current?.takeIf { it.isLoaded }?.mediaItem
    }.distinctUntilChanged().stateIn(viewModelScope, Eagerly, null)

    sealed interface State {
        data object Initial : State
        data object Loading : State
        data object Empty : State
        data class Loaded(val result: Result<Lyrics>) : State
    }

    fun reloadCurrent() = viewModelScope.launch { refreshFlow.emit(Unit) }

    fun onLyricsSelected(lyricsItem: Lyrics?) = viewModelScope.launch(Dispatchers.IO) {
        lyricsState.value = State.Loading
        if (lyricsItem == null) lyricsState.value = State.Empty else {
            // Stubbed: Loading lyrics internally
            // For now, return empty or try to fetch from repository if implemented
            lyricsState.value = State.Empty
        }
    }

    init {
        reloadCurrent()
    }
}