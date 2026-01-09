package com.joaomagdaleno.music_hub.download

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.joaomagdaleno.music_hub.common.models.DownloadContext
import com.joaomagdaleno.music_hub.common.models.Progress
import com.joaomagdaleno.music_hub.common.models.Streamable
import com.joaomagdaleno.music_hub.common.models.Streamable.Media.Companion.toServerMedia
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.download.db.DownloadDatabase
import com.joaomagdaleno.music_hub.download.db.models.ContextEntity
import com.joaomagdaleno.music_hub.download.db.models.DownloadEntity
import com.joaomagdaleno.music_hub.download.db.models.TaskType
import com.joaomagdaleno.music_hub.download.tasks.TaskManager
import com.joaomagdaleno.music_hub.utils.Serializer.toJson
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.WeakHashMap

class Downloader(
    val app: App,
    val repository: com.joaomagdaleno.music_hub.data.repository.MusicRepository,
    database: DownloadDatabase,
) {
    // val unified = sourceLoader.unified.value // REMOVED
    val downloadFeed = kotlinx.coroutines.flow.MutableStateFlow<List<com.joaomagdaleno.music_hub.common.models.EchoMediaItem>>(emptyList())

    val scope = CoroutineScope(Dispatchers.IO) + CoroutineName("Downloader")

    val dao = database.downloadDao()
    val downloadFlow = dao.getDownloadsFlow()
    private val contextFlow = dao.getContextFlow()
    private val downloadInfoFlow = downloadFlow.combine(contextFlow) { downloads, contexts ->
        downloads.map { download ->
            val context = contexts.find { download.contextId == it.id }
            Info(download, context, listOf())
        }
    }

    val taskManager = TaskManager(this)

    fun add(
        downloads: List<DownloadContext>
    ) = scope.launch {
        val concurrentDownloads = app.settings.getInt("concurrent_downloads", 2)
        taskManager.setConcurrency(concurrentDownloads)
        val contexts = downloads.mapNotNull { it.context }.distinctBy { it.id }.associate {
            it.id to dao.insertContextEntity(ContextEntity(0, it.id, it.toJson()))
        }
        downloads.forEach {
            dao.insertDownloadEntity(
                DownloadEntity(
                    0,
                    it.track.sourceName.takeIf { s -> s != "UNKNOWN" } ?: it.origin,
                    it.track.id,
                    contexts[it.context?.id],
                    it.sortOrder,
                    it.track.toJson(),
                    TaskType.Loading,
                )
            )
        }
        ensureWorker()
    }

    private val workManager by lazy { WorkManager.getInstance(app.context) }
    private fun ensureWorker() {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints(NetworkType.CONNECTED, requiresStorageNotLow = true))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
    }

    @Suppress("IDENTITY_SENSITIVE_OPERATIONS_WITH_VALUE_TYPE")
    private val servers = WeakHashMap<Long, Streamable.Media.Server>()
    @Suppress("IDENTITY_SENSITIVE_OPERATIONS_WITH_VALUE_TYPE")
    private val mutexes = WeakHashMap<Long, Mutex>()

    suspend fun getServer(
        trackId: Long, download: DownloadEntity
    ): Streamable.Media.Server = mutexes.getOrPut(trackId) { Mutex() }.withLock {
        servers.getOrPut(trackId) {
            val track = download.track.getOrThrow()
            // Using download.origin as sourceName if unknown
            val effectiveTrack = if (track.sourceName == "UNKNOWN" && download.origin.isNotBlank()) {
                 track.copy(sourceName = download.origin)
            } else track
            
            val url = repository.getStreamUrl(effectiveTrack)
            url.toServerMedia()
        }
    }

    fun cancel(trackId: Long) {
        taskManager.remove(trackId)
        scope.launch {
            val entity = dao.getDownloadEntity(trackId) ?: return@launch
            dao.deleteDownloadEntity(entity)
            entity.exceptionFile?.let {
                val file = File(it)
                if (file.exists()) file.delete()
            }
            servers.remove(trackId)
            mutexes.remove(trackId)
        }
    }

    fun restart(trackId: Long) {
        taskManager.remove(trackId)
        scope.launch {
            val download = dao.getDownloadEntity(trackId) ?: return@launch
            dao.insertDownloadEntity(
                download.copy(exceptionFile = null, finalFile = null, fullyDownloaded = false)
            )
            download.exceptionFile?.let {
                val file = File(it)
                if (file.exists()) file.delete()
            }
            servers.remove(trackId)
            mutexes.remove(trackId)
            ensureWorker()
        }
    }

    fun cancelAll() {
        taskManager.removeAll()
        scope.launch {
            val downloads = downloadFlow.first().filter { it.finalFile == null }
            downloads.forEach { download ->
                dao.deleteDownloadEntity(download)
                servers.remove(download.id)
                mutexes.remove(download.id)
            }
        }
    }

    fun deleteDownload(id: String) {
        scope.launch {
            val downloads = downloadFlow.first().filter { it.trackId == id }
            downloads.forEach { download ->
                dao.deleteDownloadEntity(download)
            }
        }
    }

    fun deleteContext(id: String) {
        scope.launch {
            val contexts = contextFlow.first().filter { it.itemId == id }
            contexts.forEach { context ->
                dao.deleteContextEntity(context)
                val downloads = downloadFlow.first().filter {
                    it.contextId == context.id
                }
                downloads.forEach { download ->
                    dao.deleteDownloadEntity(download)
                }
            }
        }
    }

    data class Info(
        val download: DownloadEntity,
        val context: ContextEntity?,
        val workers: List<Pair<TaskType, Progress>>
    )

    val flow = downloadInfoFlow.combine(taskManager.progressFlow) { downloads, info ->
        downloads.map { (dl, context) ->
            val workers = info.filter { it.first.trackId == dl.id }.map { (a, b) -> a.type to b }
            Info(dl, context, workers)
        }.sortedByDescending { it.workers.size }
    }.stateIn(scope, SharingStarted.Eagerly, listOf())

    init {
        scope.launch {
            downloadInfoFlow.map { info ->
                info.filter { it.download.fullyDownloaded }.groupBy {
                    it.context?.id
                }.flatMap { (id, infos) ->
                    // Logic to retrieve items. Unified used to convert contexts to Playlists or lists of Tracks.
                    // For now, mapping downloads to Tracks directly.
                    // Contexts were used for grouping by Album/Playlist.
                    // If context exists, we should try to reconstruct grouping.
                    // Simplified: Return list of tracks.
                    infos.mapNotNull { it.download.track.getOrNull() }
                }
            }.collect(downloadFeed)
        }
    }

    companion object {
        private const val TAG = "Downloader"
    }
}