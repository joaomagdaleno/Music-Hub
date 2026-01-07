package com.joaomagdaleno.music_hub.utils

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.io.FileOutputStream
import com.joaomagdaleno.music_hub.common.helpers.ContinuationCallback.Companion.await

object TagInjector {

    private val client = OkHttpClient()

    suspend fun writeMetadata(
        file: File,
        title: String,
        artist: String,
        album: String?,
        coverUrl: String?,
        lyrics: String? = null
    ) {
        try {
            // Important for Android compatibility
            TagOptionSingleton.getInstance().isAndroid = true

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault

            tag.setField(FieldKey.TITLE, title)
            tag.setField(FieldKey.ARTIST, artist)
            album?.let { tag.setField(FieldKey.ALBUM, it) }
            lyrics?.let { tag.setField(FieldKey.LYRICS, it) }

            coverUrl?.let { url ->
                val artworkFile = downloadImage(url)
                if (artworkFile != null) {
                    val artwork = ArtworkFactory.createArtworkFromFile(artworkFile)
                    tag.deleteArtworkField()
                    tag.setField(artwork)
                    artworkFile.delete()
                }
            }

            audioFile.commit()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun downloadImage(url: String): File? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).await()
            if (!response.isSuccessful) return null

            val tempFile = File.createTempFile("cover", ".jpg")
            val outputStream = FileOutputStream(tempFile)
            response.body?.byteStream()?.copyTo(outputStream)
            outputStream.close()
            tempFile
        } catch (e: Exception) {
            null
        }
    }
}
