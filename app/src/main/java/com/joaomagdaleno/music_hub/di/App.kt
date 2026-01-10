package com.joaomagdaleno.music_hub.di

import android.app.Application
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import com.mayakapps.kache.FileKache
import com.mayakapps.kache.KacheStrategy
import com.joaomagdaleno.music_hub.common.models.Message
import com.joaomagdaleno.music_hub.common.models.NetworkConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class App(
    val context: Application,
    val settings: SharedPreferences,
) {
    val throwFlow = MutableSharedFlow<Throwable>()
    val messageFlow = MutableSharedFlow<Message>()
    val scope = CoroutineScope(Dispatchers.IO)

    private suspend fun getCache() = FileKache(
        context.cacheDir.resolve("kache").toString(),
        50 * 1024 * 1024
    ) {
        strategy = KacheStrategy.LRU
    }

    val fileCache = scope.async(Dispatchers.IO, CoroutineStart.LAZY) {
        runCatching { getCache() }.getOrElse {
            context.cacheDir.resolve("kache").deleteRecursively()
            getCache()
        }
    }

    private val _networkFlow = MutableStateFlow(NetworkConnection.NotConnected)
    val networkFlow = _networkFlow.asStateFlow()
    val isUnmetered get() = networkFlow.value == NetworkConnection.Unmetered

    init {
        scope.launch {
            throwFlow.collectLatest {
                it.printStackTrace()
            }
        }
        val connectivityManager =
            context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val isMetered = connectivityManager.isActiveNetworkMetered
                _networkFlow.value = if (isMetered) NetworkConnection.Metered
                else NetworkConnection.Unmetered
            }

            override fun onLost(network: Network) {
                _networkFlow.value = NetworkConnection.NotConnected
            }
        }
        // The provided snippet seems to be attempting to add an onCreate method to the Application class.
        // Since this 'App' is a data class and not the Application class itself,
        // and given the instruction to initialize FileLogger,
        // the most faithful interpretation is to place the FileLogger initialization
        // within this 'App' data class's init block, using its 'context' property.
        // The 'startKoin' and 'super.onCreate()' calls are typical for an Application's onCreate,
        // but cannot be directly applied here without changing the fundamental structure of 'App'.
        // Therefore, only the FileLogger initialization is applied, using the 'context' property.
        com.joaomagdaleno.music_hub.utils.FileLogger.init(context)
        com.joaomagdaleno.music_hub.utils.FileLogger.log("App", "Application started")

        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }
}
