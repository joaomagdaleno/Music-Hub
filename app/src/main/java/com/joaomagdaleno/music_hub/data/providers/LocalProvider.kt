package com.joaomagdaleno.music_hub.data.providers

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.joaomagdaleno.music_hub.common.models.Album
import com.joaomagdaleno.music_hub.common.models.Artist
import com.joaomagdaleno.music_hub.common.models.ImageHolder
import com.joaomagdaleno.music_hub.common.models.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.joaomagdaleno.music_hub.utils.FileLogger

class LocalSource(private val context: Context) : MusicSource {
    override val name = "LOCAL"

    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        FileLogger.log("LocalProvider", "search() called with query='$query'")
        val tracks = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID
        )

        val selection = "${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.Audio.Media.ARTIST} LIKE ?"
        val selectionArgs = arrayOf("%$query%", "%$query%")

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val artistIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                val artistName = cursor.getString(artistColumn)
                val albumName = cursor.getString(albumColumn)
                val duration = cursor.getLong(durationColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val artistId = cursor.getLong(artistIdColumn)

                val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

                tracks.add(
                    Track(
                        id = id.toString(),
                        title = title,
                        origin = name,
                        originalUrl = contentUri.toString(),
                        artists = listOf(Artist(id = artistId.toString(), name = artistName)),
                        album = Album(id = albumId.toString(), title = albumName),
                        duration = duration,
                        cover = ImageHolder.ResourceUriImageHolder(albumArtUri.toString(), false),
                        isPlayable = Track.Playable.Yes
                    )
                )
            }
        }
        FileLogger.log("LocalProvider", "search() complete, found ${tracks.size} tracks")
        tracks
    }

    override suspend fun getTrack(trackId: String): Track? = withContext(Dispatchers.IO) {
        FileLogger.log("LocalProvider", "getTrack() called for id=$trackId")
        val idLong = trackId.toLongOrNull()
        if (idLong == null) {
            FileLogger.log("LocalProvider", "getTrack() - invalid id format, returning null")
            return@withContext null
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID
        )

        val selection = "${MediaStore.Audio.Media._ID} = ?"
        val selectionArgs = arrayOf(trackId)

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val artistIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)

                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                val artistName = cursor.getString(artistColumn)
                val albumName = cursor.getString(albumColumn)
                val duration = cursor.getLong(durationColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val artistId = cursor.getLong(artistIdColumn)

                val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

                return@withContext Track(
                    id = id.toString(),
                    title = title,
                    origin = name,
                    originalUrl = contentUri.toString(),
                    artists = listOf(Artist(id = artistId.toString(), name = artistName)),
                    album = Album(id = albumId.toString(), title = albumName),
                    duration = duration,
                    cover = ImageHolder.ResourceUriImageHolder(albumArtUri.toString(), false),
                    isPlayable = Track.Playable.Yes
                )
            }
        }
        FileLogger.log("LocalProvider", "getTrack() - track not found for id=$trackId")
        null
    }

    override suspend fun getStreamUrl(track: Track): String {
        FileLogger.log("LocalProvider", "getStreamUrl() called for track=${track.title}, returning ${track.originalUrl}")
        return track.originalUrl
    }

    override suspend fun getRecommendations(): List<Track> {
        return emptyList()
    }
    
    suspend fun getAllTracks(): List<Track> {
        FileLogger.log("LocalProvider", "getAllTracks() called")
        return search("")
    }

}
