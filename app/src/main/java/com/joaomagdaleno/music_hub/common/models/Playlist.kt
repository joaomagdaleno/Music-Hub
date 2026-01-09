package com.joaomagdaleno.music_hub.common.models

import kotlinx.serialization.Serializable

/**
 * A data class representing a playlist
 *
 * @property id The id of the playlist
 * @property title The title of the playlist
 * @property isEditable Whether the playlist is editable
 * @property isPrivate Whether the playlist is private
 * @property cover The cover image of the playlist
 * @property authors The authors of the playlist
 * @property trackCount The total number of tracks in the playlist
 * @property duration The total duration of the playlist in milliseconds
 * @property creationDate The creation date of the playlist
 * @property description The description of the playlist
 * @property background The background image of the playlist
 * @property subtitle The subtitle of the playlist, used to display information under the title
 * @property extras Any extra data you want to associate with the playlist
 * @property isRadioSupported Whether the playlist can be used to create a radio
 * @property isFollowable Whether the playlist can be followed
 * @property isSaveable Whether the playlist can be saved to library
 * @property isLikeable Whether the playlist can be liked
 * @property isHideable Whether the playlist can be hidden
 * @property isShareable Whether the playlist can be shared
 */
@Serializable
data class Playlist(
    override val id: String,
    override val title: String,
    val isEditable: Boolean,
    override val isPrivate: Boolean = true,
    override val cover: ImageHolder? = null,
    val authors: List<Artist> = listOf(),
    override val trackCount: Long? = null,
    override val duration: Long? = null,
    val creationDate: Date? = null,
    override val description: String? = null,
    override val background: ImageHolder? = cover,
    override val subtitle: String? = null,
    override val extras: Map<String, String> = mapOf(),
    override val isRadioSupported: Boolean = true,
    override val isFollowable: Boolean = false,
    override val isSaveable: Boolean = true,
    override val isLikeable: Boolean = false,
    override val isHideable: Boolean = false,
    override val isShareable: Boolean = true,
) : EchoMediaItem.Lists {
    override val artists = authors
    override val date = creationDate
}