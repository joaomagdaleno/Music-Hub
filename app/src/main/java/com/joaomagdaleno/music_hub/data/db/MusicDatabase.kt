package com.joaomagdaleno.music_hub.data.db

import android.content.Context
import androidx.core.net.toUri
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Date
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.ImageHolder
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.utils.Serializer
import java.io.File
import java.util.Calendar

const val ORIGIN = "origin"
const val INTERNAL_ID = "internal"

fun Playlist.toEntity(): PlaylistEntity = PlaylistEntity(
    id.toLong(),
    Serializer.toJson(creationDate),
    title,
    description ?: "",
    Serializer.toJson(cover),
    extras["listData"] ?: "[]",
    extras["actualId"] ?: ""
)

fun Track.toTrackEntity(): PlaylistTrackEntity {
    val pId = extras["pId"]!!.toLong()
    val eId = extras["eId"]!!.toLong()
    return PlaylistTrackEntity(eId, pId, id, origin, Serializer.toJson(this))
}

fun EchoMediaItem.toEntity(): SavedEntity {
    return SavedEntity(id, INTERNAL_ID, Serializer.toJson(this))
}


@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        SavedEntity::class,
    ],
    version = 7,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    
    companion object {
        private const val DATABASE_NAME = "music-db"
        fun create(app: android.app.Application) = androidx.room.Room.databaseBuilder(
            app, MusicDatabase::class.java, DATABASE_NAME
        ).fallbackToDestructiveMigration(true).build()
    }

    private val dao by lazy { playlistDao() }

    suspend fun getCreatedPlaylists(): List<Playlist> {
        return dao.getPlaylists().filter { it.actualId.isEmpty() }.map { it.playlist }
    }

    suspend fun getSaved(): List<EchoMediaItem> {
        return dao.getSaved().mapNotNull { it.item.getOrNull() }
    }

    suspend fun isSaved(item: EchoMediaItem): Boolean {
        return dao.isSaved(item.id, INTERNAL_ID)
    }

    suspend fun save(item: EchoMediaItem) {
        dao.insertSaved(item.toEntity())
    }

    suspend fun deleteSaved(item: EchoMediaItem) {
        dao.deleteSaved(item.toEntity())
    }

    private fun getDateNow(): Date {
        val calendar = Calendar.getInstance()
        calendar.time = java.util.Date()
        return Date(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH),
            day = calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    suspend fun getLikedPlaylist(context: Context): Playlist {
        val liked = dao.getPlaylist("Liked")
        return if (liked == null) {
            val playlist = PlaylistEntity(
                0,
                Serializer.toJson(getDateNow()),
                "Liked",
                context.getString(R.string.internal_liked_playlist_summary),
                null,
                "[]"
            )
            val id = dao.insertPlaylist(playlist)
            playlist.copy(id = id).playlist
        } else liked.playlist
    }

    suspend fun createPlaylist(
        title: String,
        description: String?,
        cover: ImageHolder? = null,
        actualId: String = "",
    ): Playlist {
        val playlist = PlaylistEntity(
            0,
            Serializer.toJson(getDateNow()),
            title,
            description ?: "",
            Serializer.toJson(cover),
            "[]",
            actualId
        )
        val id = dao.insertPlaylist(playlist)
        return playlist.copy(id = id).playlist
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        dao.deleteAllTracks(playlist.id.toLong())
        dao.deletePlaylist(playlist.toEntity())
    }

    suspend fun editPlaylistMetadata(playlist: Playlist, title: String, description: String?) {
        val entity = playlist.toEntity().copy(name = title, description = description ?: "")
        dao.insertPlaylist(entity)
    }

    suspend fun editPlaylistCover(playlist: Playlist, file: File?) {
        val image: ImageHolder? = file?.toUri()?.toString()?.let { ImageHolder.toResourceUriImageHolder(it, true) }
        val entity = playlist.toEntity().copy(
            cover = Serializer.toJson(image)
        )
        dao.insertPlaylist(entity)
    }

    suspend fun loadPlaylist(playlist: Playlist): Playlist {
        val entity = dao.getPlaylist(playlist.toEntity().id)
        val tracks = dao.getTracks(entity.id).map { it.toTrack() }
        if (tracks.isEmpty()) return playlist.copy(trackCount = 0, duration = null)
        val durations = tracks.mapNotNull { it.duration }
        val average = durations.average().toLong()
        return entity.playlist.copy(
            trackCount = tracks.size.toLong(),
            duration = average * tracks.size
        )
    }

    suspend fun getTracks(playlist: Playlist): List<Track> {
        val entity = dao.getPlaylist(playlist.toEntity().id)
        val tracks = dao.getTracks(entity.id).associateBy { it.eid }
        if (tracks.isEmpty()) return emptyList()
        return entity.list.map { tracks[it]!!.toTrack() }
    }

    suspend fun addTracksToPlaylist(
        playlist: Playlist, index: Int, new: List<Track>,
    ) {
        if (new.isEmpty()) return
        val entity = dao.getPlaylist(playlist.toEntity().id)
        val newTracks = new.map {
            val trackEntity = PlaylistTrackEntity(
                0, entity.id, it.id, INTERNAL_ID, Serializer.toJson(it)
            )
            dao.insertPlaylistTrack(trackEntity)
        }
        val newEntity = entity.copy(
            listData = Serializer.toJson(entity.list.toMutableList().apply {
                addAll(index, newTracks)
            })
        )
        dao.insertPlaylist(newEntity)
    }

    suspend fun removeTracksFromPlaylist(
        playlist: Playlist, tracks: List<Track>, indexes: List<Int>,
    ) {
        val tracksToEntities = indexes.map { tracks[it].toTrackEntity() }
        tracksToEntities.forEach { dao.deletePlaylistTrack(it) }
        val entity = dao.getPlaylist(playlist.toEntity().id)
        val newEntity = entity.copy(
            listData = Serializer.toJson(entity.list.toMutableList().apply {
                tracksToEntities.forEach { trackEntity ->
                    val index = indexOf(trackEntity.eid)
                    if (index != -1) removeAt(index)
                }
            })
        )
        dao.insertPlaylist(newEntity)
    }

    suspend fun moveTrack(playlist: Playlist, fromIndex: Int, toIndex: Int) {
        val entity = dao.getPlaylist(playlist.toEntity().id)
        val newEntity = entity.copy(
            listData = Serializer.toJson(entity.list.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            })
        )
        dao.insertPlaylist(newEntity)
    }

    suspend fun isLiked(track: Track): Boolean {
        val liked = dao.getPlaylist("Liked") ?: return false
        return dao.getTracks(liked.id)
            .any { it.trackId == track.id }
    }

    suspend fun getOrCreate(app: Context, context: EchoMediaItem): Playlist {
        return dao.getPlaylistByActualId(context.id)?.playlist ?: createPlaylist(
            context.title,
            app.getString(R.string.downloaded_x, ""),
            context.cover,
            context.id
        )
    }

    suspend fun getPlaylist(mediaItem: EchoMediaItem): Playlist? {
        return dao.getPlaylistByActualId(mediaItem.id)?.playlist
    }

    suspend fun getPlaylistByActualId(actualId: String): Playlist? {
        return dao.getPlaylistByActualId(actualId)?.playlist
    }

    suspend fun getPlaylist(id: Long): Playlist? {
        return try {
            dao.getPlaylist(id).playlist
        } catch (e: Exception) {
            null
        }
    }

    @Dao
    interface PlaylistDao {
        @Query("SELECT * FROM PlaylistEntity")
        suspend fun getPlaylists(): List<PlaylistEntity>

        @Query("SELECT * FROM PlaylistEntity WHERE id = :id")
        suspend fun getPlaylist(id: Long): PlaylistEntity

        @Query("SELECT * FROM PlaylistEntity WHERE name = :name")
        suspend fun getPlaylist(name: String): PlaylistEntity?

        @Query("SELECT * FROM PlaylistEntity WHERE actualId = :actualId")
        suspend fun getPlaylistByActualId(actualId: String): PlaylistEntity?

        @Insert(onConflict = REPLACE)
        suspend fun insertPlaylist(playlist: PlaylistEntity): Long

        @Delete
        suspend fun deletePlaylist(playlist: PlaylistEntity)

        @Query("SELECT * FROM PlaylistTrackEntity WHERE playlistId = :playlistId")
        suspend fun getTracks(playlistId: Long): List<PlaylistTrackEntity>

        @Insert(onConflict = REPLACE)
        suspend fun insertPlaylistTrack(playlistTrack: PlaylistTrackEntity): Long

        @Delete
        suspend fun deletePlaylistTrack(playlistTrack: PlaylistTrackEntity)

        @Query("DELETE FROM PlaylistTrackEntity WHERE playlistId = :playlistId")
        suspend fun deleteAllTracks(playlistId: Long)

        @Query("SELECT * FROM SavedEntity")
        suspend fun getSaved(): List<SavedEntity>

        @Query("SELECT EXISTS(SELECT 1 FROM SavedEntity WHERE id = :id AND origin = :origin)")
        suspend fun isSaved(id: String, origin: String): Boolean

        @Insert(onConflict = REPLACE)
        suspend fun insertSaved(saved: SavedEntity): Long

        @Delete
        suspend fun deleteSaved(saved: SavedEntity)

        @Query("SELECT * FROM PlaylistTrackEntity WHERE eid = :eid")
        suspend fun getTrack(eid: Long?): PlaylistTrackEntity?

        @Query("SELECT * FROM PlaylistTrackEntity WHERE \"after\" = :eid")
        suspend fun getAfterTrack(eid: Long): PlaylistTrackEntity?
    }
}

@Entity
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val modified: String,
    val name: String,
    val description: String,
    val cover: String?,
    val listData: String,
    val actualId: String = "",
) {
    val list by lazy {
        Serializer.toData<List<Long>>(listData).getOrThrow()
    }

    val playlist by lazy {
        Playlist(
            id.toString(),
            name,
            true,
            cover = Serializer.toData<ImageHolder?>(cover ?: "").getOrNull(),
            creationDate = Serializer.toData<Date>(modified).getOrNull(),
            description = description.takeIf { it.isNotBlank() },
            extras = mapOf(
                ORIGIN to INTERNAL_ID,
                "listData" to listData,
                "actualId" to actualId
            )
        )
    }
}

@Entity
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val eid: Long,
    val playlistId: Long,
    val trackId: String,
    val origin: String,
    val data: String,
)

fun PlaylistTrackEntity.toTrack(): Track {
    return Serializer.toData<Track>(data).getOrThrow().run {
        this.copy(
            type = type,
            extras = extras + mapOf(
                "pId" to playlistId.toString(),
                "eId" to eid.toString(),
            )
        )
    }
}

@Entity(primaryKeys = ["id", "origin"])
data class SavedEntity(
    val id: String,
    val origin: String,
    val data: String,
) {
    val item by lazy { Serializer.toData<EchoMediaItem>(data) }
}
