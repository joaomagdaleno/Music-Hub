package com.joaomagdaleno.music_hub.data.sources

import com.joaomagdaleno.music_hub.common.helpers.ContinuationCallback.Companion.await
import com.joaomagdaleno.music_hub.common.models.Lyrics
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class LrcEntry(
    val id: Int,
    val trackName: String? = null,
    val artistName: String? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    val duration: Double? = null
)

class LrcLibApi(private val client: OkHttpClient = OkHttpClient()) {
    private val baseUrl = "https://lrclib.net/api"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getLyrics(title: String, artist: String, duration: Int? = null): String? {
        val url = "$baseUrl/get".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("artist_name", artist)
            ?.addQueryParameter("track_name", title)
            ?.apply { if (duration != null) addQueryParameter("duration", duration.toString()) }
            ?.build() ?: return null

        val request = Request.Builder().url(url).build()
        val response = try { client.newCall(request).await() } catch (e: Exception) { null }

        if (response != null && response.isSuccessful) {
            val body = response.body?.string() ?: return null
            val entry = json.decodeFromString<LrcEntry>(body)
            return entry.syncedLyrics ?: entry.plainLyrics
        }

        // If exact match fails, try search
        return searchLyrics(title, artist)
    }

    private suspend fun searchLyrics(title: String, artist: String): String? {
        val query = "$artist $title"
        val url = "$baseUrl/search".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("q", query)
            ?.build() ?: return null

        val request = Request.Builder().url(url).build()
        val response = try { client.newCall(request).await() } catch (e: Exception) { null }

        if (response != null && response.isSuccessful) {
            val body = response.body?.string() ?: return null
            val entries = json.decodeFromString<List<LrcEntry>>(body)
            val best = entries.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: entries.firstOrNull()
            return best?.syncedLyrics ?: best?.plainLyrics
        }
        return null
    }

    fun parseLrc(lrc: String): Lyrics.Lyric {
        val items = mutableListOf<Lyrics.Item>()
        val lines = lrc.lines()
        // Regex for [mm:ss.xx] or [mm:ss.xxx]
        val pattern = Regex("\\[(\\d+):(\\d+)\\.(\\d+)\\](.*)")
        
        for (line in lines) {
            val match = pattern.find(line)
            if (match != null) {
                val (min, sec, ms, text) = match.destructured
                // ms part can be 2 or 3 digits. If 2 digits, it's centiseconds.
                val milliseconds = when (ms.length) {
                    2 -> ms.toLong() * 10
                    else -> ms.toLong()
                }
                val startTime = (min.toLong() * 60 * 1000) + (sec.toLong() * 1000) + milliseconds
                items.add(Lyrics.Item(text.trim(), startTime, 0L))
            }
        }
        
        if (items.isEmpty()) return Lyrics.Simple(lrc)

        // Sort by start time just in case
        items.sortBy { it.startTime }

        // Set End Times
        val finalItems = mutableListOf<Lyrics.Item>()
        for (i in 0 until items.size - 1) {
            finalItems.add(items[i].copy(endTime = items[i+1].startTime))
        }
        // Last line
        if (items.isNotEmpty()) {
            finalItems.add(items.last().copy(endTime = items.last().startTime + 10000L)) // 10s buffer
        }

        return Lyrics.Timed(finalItems)
    }
}
