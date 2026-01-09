package com.joaomagdaleno.music_hub.data.providers

import android.content.Context
import android.os.Environment
import com.joaomagdaleno.music_hub.common.models.DownloadContext
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Progress
import com.joaomagdaleno.music_hub.common.models.Streamable
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.common.models.ImageHolder
import com.joaomagdaleno.music_hub.common.helpers.ContinuationCallback.Companion.await
import com.joaomagdaleno.music_hub.utils.TagInjector
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class InternalDownloadProvider(
    private val context: Context
) {
    private val client = OkHttpClient()
    private val lrcLib = LrcLibApi(client)

    val concurrentDownloads = 3

    suspend fun getDownloadTracks(
        origin: String,
        item: EchoMediaItem,
        context: EchoMediaItem?
    ): List<DownloadContext> {
        return when (item) {
            is Track -> listOf(DownloadContext(origin, item))
            else -> emptyList()
        }
    }

    suspend fun selectServer(context: DownloadContext): Streamable {
        return context.track.servers.first()
    }

    suspend fun selectSources(
        context: DownloadContext,
        server: Streamable.Media.Server
    ): List<Streamable.Source> {
        return server.sources
    }

    suspend fun download(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        source: Streamable.Source
    ): File {
        val request = Request.Builder().url(source.id).build()
        val response = client.newCall(request).await()
        if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")

        val totalBytes = response.body?.contentLength() ?: -1L
        val destination = File(this.context.cacheDir, "download_${System.currentTimeMillis()}")
        
        response.body?.byteStream()?.use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0) {
                        progressFlow.value = Progress(totalBytes, totalRead)
                    }
                }
            }
        }
        return destination
    }

    suspend fun merge(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        files: List<File>
    ): File {
        if (files.size == 1) return files[0]
        
        val mergedFile = File(this.context.cacheDir, "merged_${System.currentTimeMillis()}")
        FileOutputStream(mergedFile).use { output ->
            files.forEach { file ->
                file.inputStream().use { input ->
                    input.copyTo(output)
                }
                file.delete()
            }
        }
        return mergedFile
    }

    suspend fun tag(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        file: File
    ): File {
        val track = context.track
        val lyrics = try {
            lrcLib.getLyrics(
                title = track.title,
                artist = track.artists.joinToString(", ") { it.name },
                duration = track.duration?.div(1000)?.toInt()
            )
        } catch (e: Exception) {
            null
        }

        TagInjector.writeMetadata(
            file = file,
            title = track.title,
            artist = track.artists.joinToString(", ") { it.name },
            album = track.album?.title,
            coverUrl = (track.cover as? ImageHolder.NetworkRequestImageHolder)?.request?.url,
            lyrics = lyrics
        )
        
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Music Hub"
        )
        if (!publicDir.exists()) publicDir.mkdirs()
        
        val source = if (file.source.isNotEmpty()) file.source else "mp3"
        val fileName = "${track.title} - ${track.artists.firstOrNull()?.name}.$source"
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            
        val finalFile = File(publicDir, fileName)
        file.renameTo(finalFile)
        
        return finalFile
    }
}
