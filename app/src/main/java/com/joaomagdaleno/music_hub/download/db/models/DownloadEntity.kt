package com.joaomagdaleno.music_hub.download.db.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.utils.ui.ExceptionData
import com.joaomagdaleno.music_hub.utils.Serializer
import kotlinx.serialization.Serializable
import java.io.File

@Entity
@Serializable
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val origin: String,
    val trackId: String,
    val contextId: Long?,
    val sortOrder: Int? = null,
    val data: String,
    val task: TaskType,
    val loaded: Boolean = false,
    val folderPath: String? = null,
    val streamableId: String? = null,
    val indexesData: String? = null,
    val toMergeFilesData: String? = null,
    val toTagFile: String? = null,
    val finalFile: String? = null,
    val exceptionFile: String? = null,
    val fullyDownloaded: Boolean = false,
) {
    val track by lazy { Serializer.toData<Track>(data) }
    val indexes by lazy { indexesData?.let { Serializer.toData<List<Int>>(it) }?.getOrNull().orEmpty() }
    val toMergeFiles by lazy { toMergeFilesData?.let { Serializer.toData<List<String>>(it) }?.getOrNull().orEmpty() }
    val exception by lazy {
        runCatching {
            exceptionFile?.let { File(it) }?.readText()?.let { Serializer.toData<ExceptionData>(it) }?.getOrThrow()
        }.getOrNull()
    }
    val isFinal by lazy { finalFile != null || exceptionFile != null }
}