package com.joaomagdaleno.music_hub.playback.source

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource.Resolver
import com.joaomagdaleno.music_hub.common.models.Streamable
import com.joaomagdaleno.music_hub.playback.MediaItemUtils
import com.joaomagdaleno.music_hub.utils.CacheUtils
import java.util.WeakHashMap

class StreamableResolver(
    private val context: Context,
    private val current: WeakHashMap<String, Result<Streamable.Media.Server>>,
) : Resolver {

    @OptIn(UnstableApi::class)
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val (id, index) = MediaItemUtils.toKey(dataSpec.uri.toString()).getOrNull() ?: return dataSpec
        val streamable = runCatching { current[id]!!.getOrThrow().streams[index] }
        val uri = streamable.map {
            if (!it.isLive)
                CacheUtils.saveToCache(context, it.id, dataSpec.uri.toString(), "player")
            Uri.parse(it.id)
        }
        return copy(dataSpec, uri = uri.getOrNull(), customData = streamable)
    }

    companion object {

        @OptIn(UnstableApi::class)
        fun copy(
            dataSpec: DataSpec,
            uri: Uri? = null,
            uriPositionOffset: Long? = null,
            httpMethod: Int? = null,
            httpBody: ByteArray? = null,
            httpRequestHeaders: Map<String, String>? = null,
            position: Long? = null,
            length: Long? = null,
            key: String? = null,
            flags: Int? = null,
            customData: Any? = null,
        ): DataSpec {
            return DataSpec.Builder()
                .setUri(uri ?: dataSpec.uri)
                .setUriPositionOffset(uriPositionOffset ?: dataSpec.uriPositionOffset)
                .setHttpMethod(httpMethod ?: dataSpec.httpMethod)
                .setHttpBody(httpBody ?: dataSpec.httpBody)
                .setHttpRequestHeaders(httpRequestHeaders ?: dataSpec.httpRequestHeaders)
                .setPosition(position ?: dataSpec.position)
                .setLength(length ?: dataSpec.length)
                .setKey(key ?: dataSpec.key)
                .setFlags(flags ?: dataSpec.flags)
                .setCustomData(customData ?: dataSpec.customData)
                .build()
        }
    }
}