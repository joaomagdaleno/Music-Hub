package com.joaomagdaleno.music_hub.utils

import android.content.Context
import com.joaomagdaleno.music_hub.utils.Serializer
import java.io.File

object CacheUtils {

    fun cacheDir(context: Context, folderName: String) =
        File(context.cacheDir, "context/$folderName").apply { mkdirs() }

    const val CACHE_FOLDER_SIZE = 50 * 1024 * 1024 //50MB

    inline fun <reified T> saveToCache(
        context: Context, id: String, data: T?, folderName: String = T::class.java.simpleName
    ) = runCatching {
        val fileName = id.hashCode().toString()
        val dir = cacheDir(context, folderName)
        val file = File(dir, fileName)

        var size = dir.walk().sumOf { it.length().toInt() }
        while (size > CACHE_FOLDER_SIZE) {
            val files = dir.listFiles()
            files?.sortBy { it.lastModified() }
            files?.firstOrNull()?.delete()
            size = dir.walk().sumOf { it.length().toInt() }
        }
        file.writeText(Serializer.toJson(data))
    }

    inline fun <reified T> getFromCache(
        context: Context, id: String, folderName: String = T::class.java.simpleName
    ): T? {
        val fileName = id.hashCode().toString()
        val dir = cacheDir(context, folderName)
        val file = File(dir, fileName)
        return if (file.exists()) runCatching {
            Serializer.toData<T>(file.readText()).getOrThrow()
        }.getOrNull() else null
    }
}
