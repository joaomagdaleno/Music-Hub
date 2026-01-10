package com.joaomagdaleno.music_hub.playback.source

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.joaomagdaleno.music_hub.common.models.Streamable
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.playback.MediaItemUtils
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.backgroundIndex
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.origin
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.retries
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.serverIndex
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.streamIndex
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.subtitleIndex
import com.joaomagdaleno.music_hub.playback.PlayerService.Companion.select
import com.joaomagdaleno.music_hub.playback.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException

@UnstableApi
class StreamableMediaSource(
    private var mediaItem: MediaItem,
    private val app: App,
    private val scope: CoroutineScope,
    private val state: PlayerState,
    private val loader: StreamableLoader,
    private val cacheFactories: Factories,
    private val factories: Factories,
    private val changeFlow: MutableSharedFlow<Pair<MediaItem, MediaItem>>,
) : CompositeMediaSource<Nothing>() {

    private var error: Throwable? = null
    override fun maybeThrowSourceInfoRefreshError() {
        error?.let { throw IOException(it) }
        super.maybeThrowSourceInfoRefreshError()
    }

    fun getFactory(stream: Streamable.Stream) = if (stream.isLive) factories else cacheFactories

    private lateinit var actualSource: MediaSource
    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        super.prepareSourceInternal(mediaTransferListener)
        val handler = Util.createHandlerForCurrentLooper()
        scope.launch {
            var (new, serv) = runCatching { loader.load(mediaItem) }.getOrElse {
                error = it
                return@launch
            }
            val server = serv.getOrNull()
            state.servers[new.mediaId] = serv
            state.serverChanged.emit(Unit)
            val streams = server?.streams
            actualSource = when (streams?.size) {
                0, null -> factories.create(new, -1, null)
                1 -> {
                    val stream = streams.first()
                    getFactory(stream).create(new, 0, stream)
                }

                else -> {
                    if (server.merged) MergingMediaSource(
                        *streams.mapIndexed { index, stream ->
                            getFactory(stream).create(new, index, stream)
                        }.toTypedArray()
                    ) else {
                        val index = mediaItem.streamIndex
                        val stream = streams.getOrNull(index)
                            ?: select(app, new.origin, streams) { it.quality }
                        val newIndex = streams.indexOf(stream)
                        new = MediaItemUtils.buildStream(new, newIndex)
                        getFactory(stream!!).create(new, newIndex, stream)
                    }
                }
            }

            changeFlow.emit(mediaItem to new)
            mediaItem = new

            handler.post {
                runCatching {
                    prepareChildSource(null, actualSource)
                }.getOrElse {
                    it.printStackTrace()
                }
            }
        }
    }

    override fun onChildSourceInfoRefreshed(
        childSourceId: Nothing?, mediaSource: MediaSource, newTimeline: Timeline,
    ) = refreshSourceInfo(newTimeline)

    override fun getMediaItem() = mediaItem

    override fun createPeriod(
        id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long,
    ) = actualSource.createPeriod(id, allocator, startPositionUs)

    override fun releasePeriod(mediaPeriod: MediaPeriod) =
        actualSource.releasePeriod(mediaPeriod)

    override fun canUpdateMediaItem(mediaItem: MediaItem) = run {
        this.mediaItem.apply {
            if (retries != mediaItem.retries) return@run false
            if (serverIndex != mediaItem.serverIndex) return@run false
            if (this.streamIndex != mediaItem.streamIndex) return@run false
            if (backgroundIndex != mediaItem.backgroundIndex) return@run false
            if (subtitleIndex != mediaItem.subtitleIndex) return@run false
        }
        if (::actualSource.isInitialized) actualSource.canUpdateMediaItem(mediaItem)
        else false
    }

    override fun updateMediaItem(mediaItem: MediaItem) {
        this.mediaItem = mediaItem
        actualSource.updateMediaItem(mediaItem)
    }

    data class Factories(
        val dash: Lazy<MediaSource.Factory>,
        val hls: Lazy<MediaSource.Factory>,
        val default: Lazy<MediaSource.Factory>,
    ) {
        fun create(mediaItem: MediaItem, index: Int, stream: Streamable.Stream?): MediaSource {
            val format = (stream as? Streamable.Stream.Http)?.format
            val factory = when (format) {
                Streamable.StreamFormat.DASH -> dash
                Streamable.StreamFormat.HLS -> hls
                Streamable.StreamFormat.Progressive, null -> default
            }
            val new = MediaItemUtils.buildForStream(mediaItem, index, stream)
            return factory.value.createMediaSource(new)
        }
    }

    class Factory(
        private val app: App,
        private val scope: CoroutineScope,
        private val state: PlayerState,
        val repository: com.joaomagdaleno.music_hub.data.repository.MusicRepository,
        cache: SimpleCache,
        downloadFlow: StateFlow<List<Downloader.Info>>,
        private val changeFlow: MutableSharedFlow<Pair<MediaItem, MediaItem>>,
    ) : MediaSource.Factory {

        private val loader = StreamableLoader(app, repository, downloadFlow)

        val dataSourceFactory = StreamableDataSource.Factory(app.context)
        val streamableResolver = StreamableResolver(app.context, state.servers)

        private val cacheDataSource = ResolvingDataSource.Factory(
            CacheDataSource.Factory().setCache(cache)
                .setUpstreamDataSourceFactory(dataSourceFactory),
            streamableResolver
        )

        private val dataSource = ResolvingDataSource.Factory(
            dataSourceFactory, streamableResolver
        )

        private val cacheFactories = createFactories(cacheDataSource)

        private val factories = createFactories(dataSource)

        private fun createFactories(dataSource: ResolvingDataSource.Factory) = Factories(
            lazily { DashMediaSource.Factory(dataSource) },
            lazily { HlsMediaSource.Factory(dataSource) },
            lazily { DefaultMediaSourceFactory(dataSource) }
        )

        private var drmSessionManagerProvider: DrmSessionManagerProvider? = null
        private var loadErrorHandlingPolicy: LoadErrorHandlingPolicy? = null
        private fun lazily(factory: () -> MediaSource.Factory) = lazy {
            factory().apply {
                drmSessionManagerProvider?.let { setDrmSessionManagerProvider(it) }
                loadErrorHandlingPolicy?.let { setLoadErrorHandlingPolicy(it) }
            }
        }

        override fun getSupportedTypes() = intArrayOf(
            C.CONTENT_TYPE_OTHER, C.CONTENT_TYPE_HLS, C.CONTENT_TYPE_DASH
        )

        override fun setDrmSessionManagerProvider(
            drmSessionManagerProvider: DrmSessionManagerProvider,
        ): MediaSource.Factory {
            this.drmSessionManagerProvider = drmSessionManagerProvider
            return this
        }

        override fun setLoadErrorHandlingPolicy(
            loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
        ): MediaSource.Factory {
            this.loadErrorHandlingPolicy = loadErrorHandlingPolicy
            return this
        }

        override fun createMediaSource(mediaItem: MediaItem) = StreamableMediaSource(
            mediaItem, app, scope, state, loader, cacheFactories, factories, changeFlow
        )
    }
}