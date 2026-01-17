package com.joaomagdaleno.music_hub.common.models

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * A class representing a track that can be played in Echo.
 */
@Serializable
data class Track(
    override val id: String,
    override val title: String,
    val origin: String = "UNKNOWN",
    val originalUrl: String = "",
    val type: Type = Type.Song,
    override val cover: ImageHolder? = null,
    val artists: List<Artist> = listOf(),
    val album: Album? = null,
    val duration: Long? = null,
    val playedDuration: Long? = null,
    val plays: Long? = null,
    val releaseDate: Date? = null,
    override val description: String? = null,
    override val background: ImageHolder? = cover,
    val genres: List<String> = listOf(),
    val isrc: String? = null,
    val albumOrderNumber: Long? = null,
    val albumDiscNumber: Long? = null,
    val playlistAddedDate: Date? = null,
    override val isExplicit: Boolean = false,
    override val subtitle: String? = null,
    override val extras: Map<String, String> = mapOf(),
    val isPlayable: Playable = Playable.Yes,
    val streamables: List<Streamable> = listOf(),
    override val isRadioSupported: Boolean = true,
    override val isFollowable: Boolean = false,
    override val isSaveable: Boolean = true,
    override val isLikeable: Boolean = true,
    override val isHideable: Boolean = true,
    override val isShareable: Boolean = true,
) : EchoMediaItem {

    enum class Type {
        Song, Podcast
    }

    @Serializable
    sealed interface Playable {
        @Serializable
        data object Yes : Playable

        @Serializable
        data object RegionLocked : Playable

        @Serializable
        data object Unreleased : Playable

        @Serializable
        data class No(val reason: String) : Playable
    }

    val subtitles: List<Streamable> by lazy {
        streamables.filter { it.type == Streamable.MediaType.Subtitle }
    }

    val servers: List<Streamable> by lazy {
        streamables.filter { it.type == Streamable.MediaType.Server }
    }

    val backgrounds: List<Streamable> by lazy {
        streamables.filter { it.type == Streamable.MediaType.Background }
    }

    override val subtitleWithOutE = subtitle ?: buildString {
        if (duration != null) append(toDurationString(duration))
        val artistsStr = artists.joinToString(", ") { it.name }
        if (artistsStr.isNotBlank()) {
            if (duration != null) append(" • ")
            append(artistsStr)
        }
    }.trim().ifBlank { null }

    override val subtitleWithE = buildString {
        if (isExplicit) append("\uD83C\uDD74 ")
        append(subtitleWithOutE ?: "")
    }.trim().ifBlank { null }

    companion object {
        fun toDurationString(duration: Long): String {
            val seconds = duration / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            return buildString {
                if (hours > 0) append(String.format(Locale.getDefault(), "%02d:", hours))
                append(String.format(Locale.getDefault(), "%02d:%02d", minutes % 60, seconds % 60))
            }.trim()
        }
    }
}