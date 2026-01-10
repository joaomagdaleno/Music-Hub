package com.joaomagdaleno.music_hub.ui.common

import android.content.Context
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.joaomagdaleno.music_hub.MainActivity
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.helpers.ContinuationCallback.Companion.await
import com.joaomagdaleno.music_hub.common.models.Message
import com.joaomagdaleno.music_hub.download.exceptions.DownloadException
import com.joaomagdaleno.music_hub.download.tasks.BaseTask.Companion.getTitle
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.track
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.serverIndex
import com.joaomagdaleno.music_hub.playback.exceptions.PlayerException
import com.joaomagdaleno.music_hub.ui.common.FragmentUtils.openFragment
import com.joaomagdaleno.music_hub.utils.ContextUtils.appVersion
import com.joaomagdaleno.music_hub.utils.ContextUtils.observe
import com.joaomagdaleno.music_hub.utils.Serializer
import com.joaomagdaleno.music_hub.utils.Serializer.rootCause
import com.joaomagdaleno.music_hub.utils.Serializer.toJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

object ExceptionUtils {

    private fun Context.getTitle(throwable: Throwable): String? = when (throwable) {
        is UnknownHostException, is UnresolvedAddressException -> getString(R.string.no_internet)

        is PlayerException -> "${throwable.mediaItem?.track?.title}: ${getFinalTitle(throwable.cause)}"

        is DownloadException -> {
            val title = getTitle(throwable.type, throwable.downloadEntity.track.getOrNull()?.title ?: "???")
            "${title}: ${getFinalTitle(throwable.cause)}"
        }

        else -> null
    }

    private fun getDetails(throwable: Throwable): String? = when (throwable) {
        is PlayerException -> throwable.mediaItem?.let {
            """
            Track: ${it.track.toJson()}
            Stream: ${it.run { track.servers.getOrNull(serverIndex)?.toJson() }}
        """.trimIndent()
        }

        is DownloadException -> """
            Type: ${throwable.type}
            Track: ${throwable.downloadEntity.toJson()}
        """.trimIndent()

        is Serializer.DecodingException -> "JSON: ${throwable.json}"

        else -> null
    }

    fun Context.getFinalTitle(throwable: Throwable): String? =
        getTitle(throwable) ?: throwable.cause?.let { getFinalTitle(it) } ?: throwable.message


    private fun getFinalDetails(throwable: Throwable): String = buildString {
        getDetails(throwable)?.let { appendLine(it) }
        throwable.cause?.let { append(getFinalDetails(it)) }
    }

    private fun getStackTrace(throwable: Throwable): String = buildString {
        appendLine("Version: ${appVersion()}")
        appendLine(getFinalDetails(throwable))
        appendLine("---Stack Trace---")
        appendLine(throwable.stackTraceToString())
    }

    @Serializable
    data class Data(val title: String, val trace: String)

    fun Throwable.toData(context: Context) = run {
        val title = context.getFinalTitle(this) ?: context.getString(
            R.string.error_x,
            message ?: this::class.run { simpleName ?: java.name }
        )
        Data(title, getStackTrace(this))
    }


    fun FragmentActivity.getMessage(throwable: Throwable, view: View?): Message {
        val title = getFinalTitle(throwable) ?: getString(
            R.string.error_x,
            throwable.message ?: throwable::class.run { simpleName ?: java.name }
        )
        return Message(
            message = title,
            Message.Action(getString(R.string.view)) {
                runCatching { openException(Data(title, getStackTrace(throwable)), view) }
            }
        )
    }

    private fun FragmentActivity.openException(data: Data, view: View? = null) {
        openFragment<ExceptionFragment>(view, ExceptionFragment.getBundle(data))
    }

    fun MainActivity.setupExceptionHandler(handler: SnackBarHandler) {
        observe(handler.app.throwFlow) { throwable ->
            val message = getMessage(throwable, null)
            handler.create(message)
        }
    }

    private val client = OkHttpClient()
    suspend fun getPasteLink(data: Data) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://paste.rs")
            .post(data.trace.toRequestBody())
            .build()
        runCatching { client.newCall(request).await().body?.string() }
    }
}