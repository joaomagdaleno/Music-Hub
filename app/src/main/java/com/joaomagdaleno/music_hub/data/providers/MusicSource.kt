package com.joaomagdaleno.music_hub.data.providers

import com.joaomagdaleno.music_hub.common.models.Shelf
import com.joaomagdaleno.music_hub.common.models.Track

interface MusicSource {
    val name: String

    suspend fun search(query: String): List<Track>
    suspend fun getStreamUrl(track: Track): String
    suspend fun getRecommendations(): List<Track>
    
    // Default implementations for optional methods
    suspend fun getHomeFeed(): List<Shelf> = emptyList()
    suspend fun getAlbumTracks(albumId: String): List<Track> = emptyList()
    suspend fun getArtistTracks(artistId: String): List<Track> = emptyList()
    suspend fun getTrack(trackId: String): Track? = null
    suspend fun getRadio(trackId: String): List<Track> = emptyList()
    suspend fun getPlaylistTracks(playlistId: String): List<Track> = emptyList()
}
