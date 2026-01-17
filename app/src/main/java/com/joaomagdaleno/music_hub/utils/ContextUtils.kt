package com.joaomagdaleno.music_hub.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build.SUPPORTED_ABIS
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.ListenableFuture
import com.joaomagdaleno.music_hub.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

object ContextUtils {
    fun appVersion() = BuildConfig.VERSION_NAME + " " + BuildConfig.BUILD_TYPE
    fun getArch(): String {
        SUPPORTED_ABIS.firstOrNull()?.let { return it }
        return System.getProperty("os.arch")
            ?: System.getProperty("os.product.cpu.abi")
            ?: "Unknown"
    }

    fun copyToClipboard(context: Context, label: String?, string: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, string)
        clipboard.setPrimaryClip(clip)
    }

    fun <T> observe(owner: LifecycleOwner, flow: Flow<T>, block: suspend (T) -> Unit) =
        owner.lifecycleScope.launch {
            flow.flowWithLifecycle(owner.lifecycle).collectLatest(block)
        }

    fun <T> collect(owner: LifecycleOwner, flow: Flow<T>, block: suspend (T) -> Unit) =
        owner.lifecycleScope.launch {
            flow.collect {
                runCatching { block(it) }
            }
        }

    fun <T> listenFuture(context: Context, future: ListenableFuture<T>, block: (Result<T>) -> Unit) {
        future.addListener({
            val result = runCatching { future.get() }
            block(result)
        }, ContextCompat.getMainExecutor(context))
    }

    fun <T> emit(owner: LifecycleOwner, flow: MutableSharedFlow<T>, value: T) {
        owner.lifecycleScope.launch {
            flow.emit(value)
        }
    }

    const val SETTINGS_NAME = "settings"
    fun getSettings(context: Context) = context.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)!!

    private fun getTempDir(context: Context) = context.cacheDir.resolve("apks").apply { mkdirs() }
    fun getTempFile(context: Context, ext: String = "apk"): File =
        File.createTempFile("temp", ".$ext", getTempDir(context))

    fun cleanupTempApks(context: Context) {
        getTempDir(context).deleteRecursively()
    }
}