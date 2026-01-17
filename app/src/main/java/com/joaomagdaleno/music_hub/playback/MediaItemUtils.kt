package com.joaomagdaleno.music_hub.playback

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.ThumbRating
import androidx.media3.common.util.UnstableApi
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.ImageHolder
import com.joaomagdaleno.music_hub.common.models.Streamable
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.common.models.MediaState
import com.joaomagdaleno.music_hub.playback.PlayerService.Companion.selectServerIndex
import com.joaomagdaleno.music_hub.utils.Serializer
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.text.Charsets.UTF_8

object MediaItemUtils {

    fun build(
        app: App,
        downloads: List<Downloader.Info>,
        state: MediaState.Unloaded<Track>,
        context: EchoMediaItem?,
    ): MediaItem {
        val item = MediaItem.Builder()
        val metadata = toMetaData(state, Bundle(), downloads, context, false, app)
        item.setMediaMetadata(metadata)
        item.setMediaId(state.item.id)
        item.setUri(state.item.id)
        return item.build()
    }

    fun buildLoaded(
        app: App,
        downloads: List<Downloader.Info>,
        mediaItem: MediaItem,
        state: MediaState.Loaded<Track>,
    ): MediaItem {
        val item = mediaItem.buildUpon()
        val metadata = toMetaData(
            state, mediaItem.mediaMetadata.extras ?: Bundle(), downloads, getContext(mediaItem), true, app
        )
        item.setMediaMetadata(metadata)
        return item.build()
    }

    fun buildServer(mediaItem: MediaItem, index: Int): MediaItem {
        val bundle = Bundle().apply {
            putAll(mediaItem.mediaMetadata.extras ?: Bundle())
            putInt("serverIndex", index)
            putInt("retries", 0)
        }
        return buildWithBundle(mediaItem, bundle)
    }

    fun buildStream(mediaItem: MediaItem, index: Int): MediaItem {
        val bundle = Bundle().apply {
            putAll(mediaItem.mediaMetadata.extras ?: Bundle())
            putInt("streamIndex", index)
            putInt("retries", 0)
        }
        return buildWithBundle(mediaItem, bundle)
    }

    fun buildBackground(mediaItem: MediaItem, index: Int): MediaItem {
        val bundle = Bundle().apply {
            putAll(mediaItem.mediaMetadata.extras ?: Bundle())
            putInt("backgroundIndex", index)
        }
        return buildWithBundle(mediaItem, bundle)
    }

    fun buildSubtitle(mediaItem: MediaItem, index: Int): MediaItem {
        val bundle = Bundle().apply {
            putAll(mediaItem.mediaMetadata.extras ?: Bundle())
            putInt("subtitleIndex", index)
        }
        return buildWithBundle(mediaItem, bundle)
    }


    fun withRetry(item: MediaItem): MediaItem {
        val bundle = Bundle().apply {
            putAll(item.mediaMetadata.extras ?: Bundle())
            val retries = getInt("retries") + 1
            putBoolean("loaded", false)
            putInt("retries", retries)
        }
        return buildWithBundle(item, bundle)
    }

    private fun buildWithBundle(mediaItem: MediaItem, bundle: Bundle): MediaItem {
        val item = mediaItem.buildUpon()
        val metadata =
            mediaItem.mediaMetadata.buildUpon().setExtras(bundle).setSubtitle(indexes(bundle))
                .build()
        item.setMediaMetadata(metadata)
        return item.build()
    }

    @Serializable
    data class Key(val trackId: String, val streamIndex: Int, val origin: String)

    fun toKey(value: String) = runCatching {
        Serializer.toData<Key>(Base64.decode(value).toString(UTF_8)).getOrThrow()
    }

    fun buildForStream(
        mediaItem: MediaItem, index: Int, stream: Streamable.Stream?,
    ): MediaItem {
        val item = mediaItem.buildUpon()
        item.setUri(Base64.encode(Serializer.toJson(Key(getTrack(mediaItem).id, index, getOrigin(mediaItem))).toByteArray()))
        when (val decryption = (stream as? Streamable.Stream.Http)?.decryption) {
            null -> {}
            is Streamable.Decryption.Widevine -> {
                val drmRequest = decryption.license
                val config = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(drmRequest.url).setMultiSession(decryption.isMultiSession)
                    .setLicenseRequestHeaders(drmRequest.headers).build()
                item.setDrmConfiguration(config)
            }
        }
        return item.build()
    }

    fun buildWithBackgroundAndSubtitle(
        mediaItem: MediaItem,
        background: Streamable.Media.Background?,
        subtitle: Streamable.Media.Subtitle?,
    ): MediaItem {
        val bundle = mediaItem.mediaMetadata.extras ?: Bundle()
        Serializer.putSerialized(bundle, "background", background)
        val item = mediaItem.buildUpon()
        item.setSubtitleConfigurations(
            if (subtitle == null) listOf()
            else listOf(
                MediaItem.SubtitleConfiguration.Builder(subtitle.url.toUri())
                    .setMimeType(toMimeType(subtitle.type))
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
            )
        )
        return item.build()
    }


    @OptIn(UnstableApi::class)
    private fun toMetaData(
        state: MediaState<Track>,
        bundle: Bundle,
        downloads: List<Downloader.Info>,
        context: EchoMediaItem? = getSerializedContext(bundle),
        loaded: Boolean = bundle.getBoolean("loaded"),
        app: App,
        serverIndex: Int? = null,
        backgroundIndex: Int? = null,
        subtitleIndex: Int? = null,
    ): MediaMetadata {
        val isLiked = (state as? MediaState.Loaded<*>)?.isLiked == true
        return with(state.item) {
            MediaMetadata.Builder()
                .setTitle(title)
                .setAlbumTitle(album?.title)
                .setAlbumArtist(album?.artists?.joinToString(", ") { it.name })
                .setArtist(artists.joinToString(", ") { it.name })
                .setArtworkUri(cover?.let { toUriWithJson(it) })
                .setUserRating(
                    if (isLiked) ThumbRating(true) else ThumbRating()
                )
                .setExtras(Bundle().apply {
                    putAll(bundle)
                    Serializer.putSerialized(this, "unloadedCover", bundle.stateNullable?.item?.cover)
                    Serializer.putSerialized(this, "state", state)
                    Serializer.putSerialized(this, "context", context)
                    putBoolean("loaded", loaded)
                    putInt("subtitleIndex", subtitleIndex ?: 0.takeIf { subtitles.isNotEmpty() } ?: -1)
                    putInt(
                        "backgroundIndex", backgroundIndex ?: 0.takeIf {
                            backgrounds.isNotEmpty() && showBackground(app.settings)
                        } ?: -1
                    )
                    val downloaded =
                        downloads.filter { it.download.trackId == id }
                            .mapNotNull { it.download.finalFile }
                    putInt(
                        "serverIndex",
                        serverIndex ?: selectServerIndex(app, origin, servers, downloaded)
                    )
                    Serializer.putSerialized(this, "downloaded", downloaded)
                })
                .setSubtitle(indexes(bundle))
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
        }
    }

    private fun indexes(bundle: Bundle) =
        "${bundle.getInt("serverIndex")} ${bundle.getInt("streamIndex")} ${bundle.getInt("backgroundIndex")} ${bundle.getInt("subtitleIndex")}"

    val Bundle?.stateNullable
        get() = this?.let { Serializer.getSerialized<MediaState<Track>>(it, "state")?.getOrNull() }
    
    fun getState(bundle: Bundle?) = requireNotNull(bundle.stateNullable)
    fun getTrack(bundle: Bundle?) = getState(bundle).item
    fun isLoaded(bundle: Bundle?) = bundle?.getBoolean("loaded") ?: false
    fun getOrigin(bundle: Bundle?) = getState(bundle).origin
    fun getSerializedContext(bundle: Bundle?) = bundle?.let { Serializer.getSerialized<EchoMediaItem?>(it, "context")?.getOrNull() }
    fun getServerIndex(bundle: Bundle?) = bundle?.getInt("serverIndex", -1) ?: -1
    fun getStreamIndex(bundle: Bundle?) = bundle?.getInt("streamIndex", -1) ?: -1
    fun getBackgroundIndex(bundle: Bundle?) = bundle?.getInt("backgroundIndex", -1) ?: -1
    fun getSubtitleIndex(bundle: Bundle?) = bundle?.getInt("subtitleIndex", -1) ?: -1
    fun getBackground(bundle: Bundle?) = bundle?.let { Serializer.getSerialized<Streamable.Media.Background?>(it, "background")?.getOrNull() }
    fun getRetries(bundle: Bundle?) = bundle?.getInt("retries") ?: 0
    fun getUnloadedCover(bundle: Bundle?) = bundle?.let { Serializer.getSerialized<ImageHolder?>(it, "unloadedCover")?.getOrNull() }
    fun getDownloaded(bundle: Bundle?) = bundle?.let { Serializer.getSerialized<List<String>>(it, "downloaded")?.getOrNull() }

    fun getState(item: MediaItem) = getState(item.mediaMetadata.extras)
    fun getTrack(item: MediaItem) = getTrack(item.mediaMetadata.extras)
    fun getOrigin(item: MediaItem) = getOrigin(item.mediaMetadata.extras)
    fun getContext(item: MediaItem) = getSerializedContext(item.mediaMetadata.extras)
    fun isLoaded(item: MediaItem) = isLoaded(item.mediaMetadata.extras)
    fun getServerIndex(item: MediaItem) = getServerIndex(item.mediaMetadata.extras)
    fun getStreamIndex(item: MediaItem) = getStreamIndex(item.mediaMetadata.extras)
    fun getBackgroundIndex(item: MediaItem) = getBackgroundIndex(item.mediaMetadata.extras)
    fun getSubtitleIndex(item: MediaItem) = getSubtitleIndex(item.mediaMetadata.extras)
    fun getBackground(item: MediaItem) = getBackground(item.mediaMetadata.extras)
    fun isLiked(metadata: MediaMetadata) = (metadata.userRating as? ThumbRating)?.isThumbsUp == true
    fun isLiked(item: MediaItem) = isLiked(item.mediaMetadata)
    fun getRetries(item: MediaItem) = getRetries(item.mediaMetadata.extras)
    fun getUnloadedCover(item: MediaItem) = getUnloadedCover(item.mediaMetadata.extras)
    fun getDownloaded(item: MediaItem) = getDownloaded(item.mediaMetadata.extras)

    private fun toMimeType(type: Streamable.SubtitleType) = when (type) {
        Streamable.SubtitleType.VTT -> MimeTypes.TEXT_VTT
        Streamable.SubtitleType.SRT -> MimeTypes.APPLICATION_SUBRIP
        Streamable.SubtitleType.ASS -> MimeTypes.TEXT_SSA
    }

    private fun toUriWithJson(holder: ImageHolder): Uri {
        val main = when (holder) {
            is ImageHolder.ResourceUriImageHolder -> holder.uri
            is ImageHolder.NetworkRequestImageHolder -> holder.request.url
            is ImageHolder.ResourceIdImageHolder -> "res://${holder.resId}"
            is ImageHolder.HexColorImageHolder -> ""
        }.toUri()
        val json = Serializer.toJson(holder)
        return main.buildUpon().appendQueryParameter("actual_data", json).build()
    }

    const val SHOW_BACKGROUND = "show_background"
    fun showBackground(prefs: SharedPreferences?) = prefs?.getBoolean(SHOW_BACKGROUND, true) ?: true

    fun serverWithDownloads(
        context: Context,
        item: MediaItem
    ): List<Streamable> {
        val track = getTrack(item)
        val downloaded = getDownloaded(item)
        return track.servers + listOfNotNull(
            Streamable.server(
                "DOWNLOADED", Int.MAX_VALUE, context.getString(R.string.downloads)
            ).takeIf { !downloaded.isNullOrEmpty() }
        )
    }
}
