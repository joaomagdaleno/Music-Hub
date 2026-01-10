package com.joaomagdaleno.music_hub.download.tasks

import android.content.Context
import com.joaomagdaleno.music_hub.common.models.Progress
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.download.db.models.TaskType
import com.joaomagdaleno.music_hub.download.tasks.TaskManager.Companion.toQueueItem
import com.joaomagdaleno.music_hub.utils.Serializer.toJson

class LoadingTask(
    private val context: Context,
    downloader: Downloader,
    override val trackId: Long,
) : BaseTask(context, downloader, trackId) {

    override val type = TaskType.Loading

    private val manager = downloader.taskManager
    private val repository = downloader.repository

    private val totalSize = 3L

    override suspend fun work(trackId: Long) {
        progressFlow.value = Progress(totalSize, 0)
        var download = dao.getDownloadEntity(trackId)!!
        
        if (!download.loaded) {
            val track = repository.getTrack(download.trackId) ?: throw Exception("Track not found")
            download = download.copy(data = track.toJson(), loaded = true)
            dao.insertDownloadEntity(download)
        }

        progressFlow.value = Progress(totalSize, 1)
        val server = downloader.getServer(trackId, download)

        val indexes = download.indexes.ifEmpty {
            // By default, download all sources if merged, or just the first one if not.
            // Simplified: download the first source.
            if (server.streams.isNotEmpty()) listOf(0) else emptyList()
        }
        if (indexes.isEmpty()) throw Exception("No files to download")
        download = download.copy(indexesData = indexes.toJson())
        dao.insertDownloadEntity(download)

        progressFlow.value = Progress(totalSize, 2)
        // Wait for server/sources logic if needed. 
        // In this simplified version, we just proceed.

        progressFlow.value = Progress(totalSize, 3)

        val requests = indexes.map { index ->
            DownloadingTask(context, downloader, trackId, index)
        }.toQueueItem()
        val mergeRequest = MergingTask(context, downloader, trackId).toQueueItem()
        val taggingRequest = TaggingTask(context, downloader, trackId).toQueueItem()

        manager.enqueue(trackId, listOf(requests, mergeRequest, taggingRequest))
    }
}