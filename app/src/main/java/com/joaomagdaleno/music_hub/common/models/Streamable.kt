package com.joaomagdaleno.music_hub.common.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonClassDiscriminator
import java.io.InputStream

@Serializable
data class Streamable(
    val id: String,
    val quality: Int,
    val type: MediaType,
    val title: String? = null,
    val extras: Map<String, String> = mapOf()
) {

    @OptIn(ExperimentalSerializationApi::class)
    @JsonClassDiscriminator("mediaType")
    @Serializable
    sealed class Media {

        @Serializable
        data class Subtitle(val url: String, val type: SubtitleType) : Media()

        @Serializable
        data class Server(val streams: List<Stream>, val merged: Boolean) : Media()

        @Serializable
        data class Background(val request: NetworkRequest) : Media()
        
        companion object {
            fun toMedia(stream: Stream) = Server(listOf(stream), false)

            fun toBackgroundMedia(url: String, headers: Map<String, String> = mapOf()) =
                Background(NetworkRequest.toGetRequest(url, headers))

            fun toServerMedia(
                url: String,
                headers: Map<String, String> = mapOf(),
                type: StreamFormat = StreamFormat.Progressive,
                isVideo: Boolean = false
            ) = toMedia(Stream.toStream(url, headers, type, isVideo))

            fun toSubtitleMedia(url: String, type: SubtitleType) = Subtitle(url, type)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @JsonClassDiscriminator("decryptionType")
    @Serializable
    sealed class Decryption {
        @Serializable
        data class Widevine(
            val license: NetworkRequest,
            val isMultiSession: Boolean,
        ) : Decryption()
    }

    @OptIn(ExperimentalSerializationApi::class)
    @JsonClassDiscriminator("streamType")
    @Serializable
    sealed class Stream {
        abstract val id: String
        abstract val quality: Int
        abstract val title: String?
        abstract val isVideo: Boolean
        abstract val isLive: Boolean

        @Serializable
        data class Http(
            val request: NetworkRequest,
            val format: StreamFormat = StreamFormat.Progressive,
            val decryption: Decryption? = null,
            override val quality: Int = 0,
            override val title: String? = null,
            override val isVideo: Boolean = false,
            override val isLive: Boolean = false
        ) : Stream() {
            override val id = request.url
        }

        @Serializable
        data class Raw(
            @Transient val streamProvider: InputProvider? = null,
            override val id: String,
            override val quality: Int = 0,
            override val title: String? = null,
            override val isVideo: Boolean = false,
            override val isLive: Boolean = false
        ) : Stream()

        companion object {
            fun toStream(
                url: String,
                headers: Map<String, String> = mapOf(),
                format: StreamFormat = StreamFormat.Progressive,
                isVideo: Boolean = false,
                isLive: Boolean = false
            ) = Http(NetworkRequest.toGetRequest(url, headers), format, isVideo = isVideo, isLive = isLive)

            fun toStream(
                id: String, provider: InputProvider, isVideo: Boolean = false, isLive: Boolean = false
            ) = Raw(provider, id, isVideo = isVideo, isLive = isLive)
        }
    }

    fun interface InputProvider {
        suspend fun provide(position: Long, length: Long): Pair<InputStream, Long>
    }

    companion object {
        fun server(
            id: String, quality: Int, title: String? = null, extras: Map<String, String> = mapOf()
        ) = Streamable(id, quality, MediaType.Server, title, extras)

        fun background(
            id: String, quality: Int, title: String? = null, extras: Map<String, String> = mapOf()
        ) = Streamable(id, quality, MediaType.Background, title, extras)

        fun subtitle(
            id: String, title: String? = null, extras: Map<String, String> = mapOf()
        ) = Streamable(id, 0, MediaType.Subtitle, title, extras)
    }

    enum class MediaType {
        Background, Server, Subtitle
    }

    enum class SubtitleType {
        VTT, SRT, ASS
    }

    enum class StreamFormat {
        Progressive, HLS, DASH
    }
}