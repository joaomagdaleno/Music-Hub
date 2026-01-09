package com.joaomagdaleno.music_hub.data.providers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import com.joaomagdaleno.music_hub.common.helpers.ContinuationCallback.Companion.await

@Serializable
data class SlavArtTrack(
    val id: String,
    val title: String? = null,
    val name: String? = null,
    val artist: JsonElement? = null,
    val artists: JsonElement? = null,
    val performer: JsonElement? = null,
    val album: SlavArtAlbum? = null,
    val cover: String? = null,
    val image: String? = null,
    val duration: Int? = null,
    val url: String? = null,
    val link: String? = null,
    val maximum_bit_depth: Int? = null,
    val maximum_sampling_rate: Double? = null,
    val audioQuality: String? = null
)

@Serializable
data class SlavArtAlbum(
    val title: String? = null,
    val name: String? = null,
    val cover: String? = null
)

class SlavArtApi(private val client: OkHttpClient = OkHttpClient()) {
    private val baseUrl = "https://slavart.gamesdrive.net"
    private val downloadBaseUrl = "https://slavart-api.gamesdrive.net"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String): List<SlavArtResult> {
        val url = "$baseUrl/api/search".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("q", query)
            ?.build() ?: return emptyList()

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).await()

        if (!response.isSuccessful) return emptyList()

        val responseBody = response.body?.string() ?: return emptyList()
        val data = json.parseToJsonElement(responseBody).jsonObject

        val results = mutableListOf<SlavArtResult>()

        for (source in listOf("qobuz", "tidal", "deezer")) {
            val sourceData = data[source] ?: continue
            val items = when {
                sourceData.jsonObject.containsKey("results") ->
                    sourceData.jsonObject["results"]?.jsonArray
                else -> null
            } ?: continue

            for (item in items) {
                try {
                    val track = json.decodeFromJsonElement<SlavArtTrack>(item)
                    results.add(parseTrack(track, source))
                } catch (e: Exception) {
                    continue
                }
            }
        }
        return results
    }

    private fun parseTrack(track: SlavArtTrack, source: String): SlavArtResult {
        return SlavArtResult(
            id = track.id,
            title = track.title ?: track.name ?: "Unknown",
            artist = extractArtist(track),
            album = track.album?.title ?: track.album?.name,
            thumbnail = track.cover ?: track.album?.cover ?: track.image,
            duration = track.duration,
            source = source,
            url = track.url ?: track.link ?: "",
            quality = parseQuality(track, source)
        )
    }

    private fun extractArtist(track: SlavArtTrack): String {
        track.artist?.let { artist ->
            if (artist is kotlinx.serialization.json.JsonObject) {
                return artist["name"]?.toString()?.removeSurrounding("\"") ?: "Unknown"
            }
        }
        track.artists?.let { artists ->
            if (artists is kotlinx.serialization.json.JsonArray && artists.isNotEmpty()) {
                val first = artists[0]
                if (first is kotlinx.serialization.json.JsonObject) {
                    return first["name"]?.toString()?.removeSurrounding("\"") ?: "Unknown"
                } else if (first is kotlinx.serialization.json.JsonPrimitive) {
                    return first.content
                }
            }
        }
        track.performer?.let { performer ->
            if (performer is kotlinx.serialization.json.JsonObject) {
                return performer["name"]?.toString()?.removeSurrounding("\"") ?: "Unknown"
            }
        }
        return track.artist?.toString()?.removeSurrounding("\"") ?: "Unknown"
    }

    private fun parseQuality(track: SlavArtTrack, source: String): String? {
        return when (source) {
            "qobuz" -> {
                if (track.maximum_bit_depth != null && track.maximum_sampling_rate != null) {
                    "${track.maximum_bit_depth}-bit/${track.maximum_sampling_rate}kHz"
                } else "FLAC"
            }
            "tidal" -> {
                when (track.audioQuality) {
                    "HI_RES" -> "MQA 24-bit"
                    "LOSSLESS" -> "FLAC 16-bit"
                    else -> track.audioQuality
                }
            }
            "deezer" -> "FLAC 16-bit"
            else -> null
        }
    }

    suspend fun getDownloadUrl(trackUrl: String): String? {
        val url = "$downloadBaseUrl/api/download".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("url", trackUrl)
            ?.build() ?: return null

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).await()

        if (!response.isSuccessful) return null

        val responseBody = response.body?.string() ?: return null
        val data = json.parseToJsonElement(responseBody).jsonObject
        return data["download_url"]?.toString()?.removeSurrounding("\"")
    }

    suspend fun getAlbumTracks(albumId: String): List<SlavArtResult> {
        val url = "$baseUrl/api/album/$albumId".toHttpUrlOrNull() ?: return emptyList()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).await()

        if (!response.isSuccessful) return emptyList()

        val responseBody = response.body?.string() ?: return emptyList()
        val data = json.parseToJsonElement(responseBody).jsonObject
        val tracks = data["tracks"]?.jsonArray ?: return emptyList()

        return tracks.mapNotNull {
            try {
                val track = json.decodeFromJsonElement<SlavArtTrack>(it)
                parseTrack(track, "slavart")
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun getArtistInfo(artistId: String): SlavArtArtistInfo {
        val url = "$baseUrl/api/artist/$artistId".toHttpUrlOrNull() ?: return SlavArtArtistInfo(emptyList(), emptyList())
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).await()

        if (!response.isSuccessful) return SlavArtArtistInfo(emptyList(), emptyList())

        val responseBody = response.body?.string() ?: return SlavArtArtistInfo(emptyList(), emptyList())
        val data = json.parseToJsonElement(responseBody).jsonObject

        val topTracks = data["top_tracks"]?.jsonArray?.mapNotNull {
            try {
                val track = json.decodeFromJsonElement<SlavArtTrack>(it)
                parseTrack(track, "slavart")
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()

        val albums = data["albums"]?.jsonArray?.mapNotNull {
            try {
                json.decodeFromJsonElement<SlavArtAlbumResult>(it)
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()

        return SlavArtArtistInfo(topTracks, albums)
    }
}

@Serializable
data class SlavArtAlbumResult(
    val id: String,
    val title: String? = null,
    val name: String? = null,
    val cover: String? = null,
    val year: String? = null
)

data class SlavArtArtistInfo(
    val topTracks: List<SlavArtResult>,
    val albums: List<SlavArtAlbumResult>
)

data class SlavArtResult(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val thumbnail: String?,
    val duration: Int?,
    val source: String,
    val url: String,
    val quality: String?
)
