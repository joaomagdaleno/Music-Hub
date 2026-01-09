package com.joaomagdaleno.music_hub.data.providers

import com.joaomagdaleno.music_hub.common.models.Album
import com.joaomagdaleno.music_hub.common.models.Shelf
import com.joaomagdaleno.music_hub.common.models.Track

interface MusicProvider {
    val name: String
    
    suspend fun search(query: String): List<Track>
    suspend fun getStreamUrl(track: Track): String
    suspend fun getRecommendations(): List<Track>
    suspend fun getHomeFeed(): List<Shelf> = emptyList()
    suspend fun getAlbumTracks(albumId: String): List<Track> = emptyList()
    suspend fun getArtistAlbums(artistId: String): List<Album> = emptyList()
    suspend fun getArtistTracks(artistId: String): List<Track> = emptyProviderList()
    suspend fun getTrack(trackId: String): Track? = null
    suspend fun getRadio(trackId: String): List<Track> = emptyList()
    suspend fun getPlaylistTracks(playlistId: String): List<Track> = emptyList()
}
