package com.joaomagdaleno.music_hub.data.repository

import com.joaomagdaleno.music_hub.common.models.Feed.Companion.pagedDataOfFirst
import com.joaomagdaleno.music_hub.common.models.Shelf
import com.joaomagdaleno.music_hub.common.models.Streamable
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.data.providers.LocalProvider
import com.joaomagdaleno.music_hub.data.providers.YoutubeProvider
import com.joaomagdaleno.music_hub.data.providers.LrcLibApi
import com.joaomagdaleno.music_hub.data.providers.PipedApi
import com.joaomagdaleno.music_hub.data.providers.SlavArtApi
import com.joaomagdaleno.music_hub.common.models.NetworkRequest
import com.joaomagdaleno.music_hub.common.models.ImageHolder
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.common.helpers.PagedData
import com.joaomagdaleno.music_hub.common.models.Tab
import com.joaomagdaleno.music_hub.common.models.Album
import com.joaomagdaleno.music_hub.common.models.Artist
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem

class MusicRepository(
    private val app: com.joaomagdaleno.music_hub.di.App,
    private val localProvider: LocalProvider,
    private val database: com.joaomagdaleno.music_hub.data.db.UnifiedDatabase,
) {
    private val piped = PipedApi()
    private val slavArt = SlavArtApi()
    private val lrcLib = LrcLibApi()
    private val youtubeProvider = YoutubeProvider() // Integrated provider

    suspend fun search(query: String): List<Track> {
        val results = mutableListOf<Track>()
        
        // 1. Local Search
        try {
            results.addAll(localProvider.search(query))
        } catch (e: Exception) { e.printStackTrace() }

        // 2. Remote Search (SlavArt + YouTube)
        try {
             // SlavArt
             val slavArtResults = slavArt.search(query).map { result ->
                Track(
                    id = result.id,
                    title = result.title,
                    origin = "SLAVART",
                    album = result.album?.let { Album(id = "slavart_album:${result.id}", title = it, artists = listOf()) },
                    artists = listOf(Artist(id = "slavart_artist:${result.artist}", name = result.artist)),
                    cover = result.thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
                    isPlayable = Track.Playable.Yes,
                    duration = result.duration?.toLong()?.times(1000),
                    extras = mapOf("media_url" to result.url, "quality" to (result.quality ?: "FLAC"))
                )
             }
             results.addAll(slavArtResults)

             // YouTube (Piped)
             if (slavArtResults.isEmpty()) {
                  val pipedResults = piped.search(query).map { res ->
                    val videoId = res.url.substringAfter("v=", "")
                    Track(
                        id = videoId,
                        title = res.title ?: "Unknown",
                        origin = "YOUTUBE",
                        artists = listOf(Artist(id = res.uploaderUrl?.substringAfterLast("/")?.let { "youtube_channel:$it" } ?: "unknown", name = res.uploaderName ?: "Unknown")),
                        cover = res.thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
                        duration = res.duration?.times(1000),
                        isPlayable = Track.Playable.Yes,
                        extras = mapOf("video_id" to videoId)
                    )
                  }
                  results.addAll(pipedResults)
             }
        } catch (e: Exception) { e.printStackTrace() }

        return results
    }

    private val streamCache = mutableMapOf<String, String>()

    suspend fun getStreamUrl(track: Track): String {
        if (streamCache.containsKey(track.id)) {
            return streamCache[track.id]!!
        }

        val url = resolveStreamUrl(track)
        if (url.isNotEmpty()) {
            streamCache[track.id] = url
        }
        return url
    }

    private suspend fun resolveStreamUrl(track: Track): String {
        // 1. Native Local Check
        if (track.origin == "LOCAL" || track.id.startsWith("content://") || track.originalUrl.startsWith("/")) {
             return localProvider.getStreamUrl(track)
        }

        // 2. Legacy/YouTube Mapping
        if (track.origin == "YOUTUBE" || 
            track.origin.contains("youtube", true) || 
            track.extras.containsKey("video_id")) {
             val videoId = track.extras["video_id"] ?: track.id
             return piped.getStreamUrl(videoId) ?: ""
        }
        
        // 3. SlavArt / Direct URL Fallback
        val url = track.extras["media_url"] ?: track.originalUrl
        if (url.contains(".slavart-api.")) return url
        
        return slavArt.getDownloadUrl(url) ?: ""
    }

    suspend fun getHomeFeed(): List<Shelf> {
        // Combined Native Home Feed
        val shelves = mutableListOf<Shelf>()
        
        // Trending from YouTube
        try {
            val trending = piped.getTrending("BR").take(10).map { res ->
                val videoId = res.url.substringAfter("v=", "")
                Track(
                    id = videoId,
                    title = res.title ?: "Unknown",
                    origin = "YOUTUBE",
                    artists = listOf(Artist(id = res.uploaderUrl?.substringAfterLast("/")?.let { "youtube_channel:$it" } ?: "unknown", name = res.uploaderName ?: "Unknown")),
                    cover = res.thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
                    duration = res.duration?.times(1000),
                    isPlayable = Track.Playable.Yes,
                    extras = mapOf("video_id" to videoId)
                )
            }
            if (trending.isNotEmpty()) {
                shelves.add(Shelf.Lists.Tracks("trending", "Trending Now", trending, type = Shelf.Lists.Type.Grid))
            }
        } catch (_: Exception) {}

        // Discovery from SlavArt
        try {
             val discovery = slavArt.search("top 2024").take(10).map { result ->
                Track(
                    id = result.id,
                    title = result.title,
                    origin = "SLAVART",
                    album = result.album?.let { Album(id = "slavart_album:${result.id}", title = it, artists = listOf()) },
                    artists = listOf(Artist(id = "slavart_artist:${result.artist}", name = result.artist)),
                    cover = result.thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
                    isPlayable = Track.Playable.Yes,
                    duration = result.duration?.toLong()?.times(1000),
                    extras = mapOf("media_url" to result.url)
                )
             }
             if (discovery.isNotEmpty()) {
                shelves.add(Shelf.Lists.Tracks("discovery", "High Quality Picks", discovery, type = Shelf.Lists.Type.Grid))
            }
        } catch (_: Exception) {}

        return shelves
    }

    suspend fun getAlbumTracks(albumId: String): List<Track> {
         if (albumId.startsWith("slavart_album:")) {
             val realId = albumId.removePrefix("slavart_album:")
             return slavArt.getAlbumTracks(realId).map { result ->
                 Track(
                    id = result.id,
                    title = result.title,
                    origin = "SLAVART",
                    artists = listOf(Artist(id = "slavart_artist:${result.artist}", name = result.artist)),
                    cover = result.thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
                    isPlayable = Track.Playable.Yes,
                    duration = result.duration?.toLong()?.times(1000),
                    extras = mapOf("media_url" to result.url)
                )
             }
         }
        return emptyList()
    }

    suspend fun getArtistTracks(artistId: String): List<Track> {
        return emptyList()
    }

    suspend fun getArtistAlbums(artistId: String): List<com.joaomagdaleno.music_hub.common.models.Album> {
        return emptyList()
    }

    suspend fun getTrack(trackId: String): Track? {
        return null
    }

    suspend fun getRadio(trackId: String): List<Track> {
        return emptyList()
    }

    suspend fun getAlbum(id: String): com.joaomagdaleno.music_hub.common.models.Album {
        return com.joaomagdaleno.music_hub.common.models.Album(id = id, title = "")
    }

    suspend fun getArtist(id: String): com.joaomagdaleno.music_hub.common.models.Artist {
        return com.joaomagdaleno.music_hub.common.models.Artist(id = id, name = "")
    }

    suspend fun getPlaylist(id: String): com.joaomagdaleno.music_hub.common.models.Playlist? {
        val fromDb = database.getPlaylistByActualId(id) ?: database.getPlaylist(id.toLongOrNull() ?: -1)
        if (fromDb != null) return fromDb
        return null
    }

    suspend fun isLiked(track: Track): Boolean {
        return database.isLiked(track)
    }

    suspend fun toggleLike(track: Track) {
        val liked = isLiked(track)
        val playlist = database.getLikedPlaylist(app.context)
        if (liked) {
            database.removeTracksFromPlaylist(playlist, listOf(track), listOf(0))
        } else {
            database.addTracksToPlaylist(playlist, 0, listOf(track))
        }
    }

    suspend fun isSaved(item: EchoMediaItem): Boolean {
        return database.isSaved(item)
    }

    suspend fun toggleSave(item: EchoMediaItem) {
        if (isSaved(item)) {
            database.deleteSaved(item)
        } else {
            database.save(item)
        }
    }

    suspend fun getLibraryFeed(): List<Shelf> {
        val localTracks = localProvider.getAllTracks()
        return if (localTracks.isNotEmpty()) {
            listOf(Shelf.Lists.Tracks("local_tracks", "Local Music", localTracks))
        } else {
            emptyList()
        }
    }

    suspend fun getPlaylistTracks(playlistId: String): List<Track> {
        return youtubeProvider.getPlaylistTracks(playlistId)
    }

    suspend fun getFeed(id: String): List<Shelf> {
        return when {
            id == "home" -> getHomeFeed()
            id == "library" -> getLibraryFeed()
            id.startsWith("artist-") && id.endsWith("-albums") -> {
                val artistId = id.removePrefix("artist-").removeSuffix("-albums")
                val albums = getArtistAlbums(artistId)
                listOf(Shelf.Lists.Items(id, "Albums", albums, type = Shelf.Lists.Type.Grid))
            }
            id.startsWith("artist-") && id.endsWith("-tracks") -> {
                val artistId = id.removePrefix("artist-").removeSuffix("-tracks")
                val tracks = getArtistTracks(artistId)
                listOf(Shelf.Lists.Tracks(id, "Tracks", tracks))
            }
            else -> emptyList()
        }
    }
}
