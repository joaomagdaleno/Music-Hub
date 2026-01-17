package com.joaomagdaleno.music_hub.download.db.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.utils.Serializer

@Entity
data class ContextEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val itemId: String,
    val data: String,
) {
    val mediaItem by lazy { Serializer.toData<EchoMediaItem>(data) }
}
