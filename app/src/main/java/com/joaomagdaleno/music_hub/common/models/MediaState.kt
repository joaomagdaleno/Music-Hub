package com.joaomagdaleno.music_hub.common.models

import kotlinx.serialization.Serializable

@Serializable
sealed interface MediaState<T : EchoMediaItem> {
    val origin: String
    val item: T
    val loaded: Boolean

    @Serializable
    class Loaded<T : EchoMediaItem>(
        override val origin: String,
        override val item: T,
        val isFollowed: Boolean? = null,
        val followers: Long? = null,
        val isSaved: Boolean? = null,
        val isLiked: Boolean? = null,
        val isHidden: Boolean? = null,
        val showRadio: Boolean = true,
        val showShare: Boolean = true
    ) : MediaState<T> {
        override val loaded = true
    }

    @Serializable
    data class Unloaded<T : EchoMediaItem>(
        override val origin: String,
        override val item: T
    ) : MediaState<T> {
        override val loaded = false
    }
}
