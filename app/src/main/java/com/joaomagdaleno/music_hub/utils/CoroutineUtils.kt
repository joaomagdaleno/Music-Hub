package com.joaomagdaleno.music_hub.utils

import android.content.Context
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.CoroutineContext

object CoroutineUtils {
    fun setDebug() {
        System.setProperty(
            kotlinx.coroutines.DEBUG_PROPERTY_NAME,
            kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
        )
    }

    suspend fun <T, R> collectWith(flow1: Flow<T>, flow2: Flow<R>, block: suspend (T, R) -> Unit) {
        flow1.combine(flow2) { t, r -> t to r }.collectLatest { (t, r) -> block(t, r) }
    }

    fun <T> throttleLatest(flow: Flow<T>, delayMillis: Long): Flow<T> = flow.conflate().transform {
        emit(it)
        delay(delayMillis)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    inline fun <reified T, R> combineTransformLatest(
        vararg flows: Flow<T>,
        noinline transform: suspend FlowCollector<R>.(Array<T>) -> Unit
    ): Flow<R> {
        return combine(*flows) { it }
            .transformLatest(transform)
    }

    fun <T1, T2, R> combineTransformLatest(
        flow1: Flow<T1>,
        flow2: Flow<T2>,
        transform: suspend FlowCollector<R>.(T1, T2) -> Unit
    ): Flow<R> {
        return combineTransformLatest(flow1, flow2) { args ->
            @Suppress("UNCHECKED_CAST")
            transform(
                args[0] as T1,
                args[1] as T2
            )
        }
    }

    fun <T> future(
        scope: CoroutineScope, context: CoroutineContext = Dispatchers.IO, block: suspend () -> T
    ): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        scope.launch(context) {
            future.set(block())
        }
        return future
    }

    fun <T> futureCatching(
        scope: CoroutineScope, context: CoroutineContext = Dispatchers.IO, block: suspend () -> T
    ): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        scope.launch(context) {
            runCatching {
                future.set(block())
            }.getOrElse {
                future.setException(it)
            }
        }
        return future
    }

    suspend fun <T> await(future: ListenableFuture<T>, context: Context) = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            future.cancel(true)
        }
        future.addListener({
            continuation.resumeWith(runCatching { future.get()!! })
        }, ContextCompat.getMainExecutor(context))
    }
}