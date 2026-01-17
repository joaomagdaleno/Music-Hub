package com.joaomagdaleno.music_hub.utils.image

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import coil3.Image
import coil3.asDrawable
import coil3.imageLoader
import coil3.load
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.error
import coil3.request.placeholder
import coil3.request.target
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import coil3.transform.Transformation
import com.joaomagdaleno.music_hub.common.models.ImageHolder

object ImageUtils {

    private fun <T> tryWith(print: Boolean = false, block: () -> T): T? {
        return try {
            block()
        } catch (e: Throwable) {
            if (print) e.printStackTrace()
            null
        }
    }

    private suspend fun <T> tryWithSuspend(print: Boolean = true, block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: Throwable) {
            if (print) e.printStackTrace()
            null
        }
    }

    private fun enqueue(view: View, builder: ImageRequest.Builder) =
        view.context.imageLoader.enqueue(builder.build())

    fun loadInto(
        holder: ImageHolder?, imageView: ImageView, placeholder: Int? = null, errorDrawable: Int? = null
    ) = tryWith {
        val request = createRequestBuilder(holder, imageView.context, placeholder, errorDrawable)
        request.target(imageView)
        enqueue(imageView, request)
    }

    fun getCachedDrawable(holder: ImageHolder, context: Context): Drawable? {
        val key = getDiskId(holder) ?: return null
        return context.imageLoader.diskCache?.openSnapshot(key)?.use {
            Drawable.createFromPath(it.data.toFile().absolutePath)
        }
    }

    fun <T : View> loadWithThumb(
        holder: ImageHolder?, view: T, thumbnail: Drawable? = null,
        error: Int? = null, onDrawable: (T, Drawable?) -> Unit
    ) = tryWith(true) {
        tryWith(false) { onDrawable(view, thumbnail) }
        val request = createRequestBuilder(holder, view.context, null, error)
        fun setDrawable(image: Image?) {
            val drawable = image?.asDrawable(view.resources)
            tryWith(false) { onDrawable(view, drawable) }
        }
        request.target({}, ::setDrawable, ::setDrawable)
        enqueue(view, request)
    }

    private val circleCrop = CircleCropTransformation()
    private val squareCrop = SquareCropTransformation()
    fun <T : View> loadAsCircle(
        holder: ImageHolder?,
        view: T,
        placeholder: Int? = null,
        error: Int? = null,
        onDrawable: (Drawable?) -> Unit
    ) = tryWith {
        val request = createRequestBuilder(holder, view.context, placeholder, error, circleCrop)
        fun setDrawable(image: Image?) {
            val drawable = image?.asDrawable(view.resources)
            tryWith(false) { onDrawable(drawable) }
        }
        request.target(::setDrawable, ::setDrawable, ::setDrawable)
        enqueue(view, request)
    }

    suspend fun loadDrawable(
        holder: ImageHolder?, context: Context
    ) = tryWithSuspend {
        val request = createRequestBuilder(holder, context, null, null)
        context.imageLoader.execute(request.build()).image?.asDrawable(context.resources)
    }

    suspend fun loadAsCircleDrawable(
        holder: ImageHolder?, context: Context
    ) = tryWithSuspend {
        val request = createRequestBuilder(holder, context, null, null, circleCrop)
        context.imageLoader.execute(request.build()).image?.asDrawable(context.resources)
    }

    fun loadBlurred(imageView: ImageView, drawable: Drawable?, radius: Float) = tryWith {
        if (drawable == null) imageView.setImageDrawable(null)
        imageView.load(drawable) {
            transformations(BlurTransformation(imageView.context, radius))
        }
    }

    private fun getDiskId(holder: ImageHolder) = when (holder) {
            is ImageHolder.NetworkRequestImageHolder -> holder.request.toString().hashCode().toString()
            else -> null
        }

    private fun applyHolderToRequest(
        holder: ImageHolder,
        builder: ImageRequest.Builder,
    ) {
        builder.diskCacheKey(getDiskId(holder))
        when (holder) {
            is ImageHolder.ResourceUriImageHolder -> builder.data(holder.uri)
            is ImageHolder.NetworkRequestImageHolder -> {
                val headerBuilder = NetworkHeaders.Builder()
                holder.request.headers.forEach { (key, value) ->
                    headerBuilder[key] = value
                }
                builder.httpHeaders(headerBuilder.build())
                builder.data(holder.request.url)
            }

            is ImageHolder.ResourceIdImageHolder -> builder.data(holder.resId)
            is ImageHolder.HexColorImageHolder -> builder.data(holder.hex.toColorInt().toDrawable())
        }
    }

    private fun createRequestBuilder(
        holder: ImageHolder?,
        context: Context,
        placeholder: Int?,
        errorDrawable: Int?,
        vararg transformations: Transformation
    ): ImageRequest.Builder {
        val builder = ImageRequest.Builder(context)
        var error = errorDrawable
        if (error == null) error = placeholder

        if (holder == null) {
            if (error != null) builder.data(error)
            return builder
        }
        applyHolderToRequest(holder, builder)
        placeholder?.let { builder.placeholder(it) }
        error?.let { builder.error(it) }
        val list = if (holder.crop) listOf(squareCrop, *transformations) else transformations.toList()
        if (list.isNotEmpty()) builder.transformations(list)
        return builder
    }
}