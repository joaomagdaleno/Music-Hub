package com.joaomagdaleno.music_hub.ui.common

import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.joaomagdaleno.music_hub.MainActivity
import com.joaomagdaleno.music_hub.common.models.Message
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.utils.ContextUtils.observe
import com.joaomagdaleno.music_hub.utils.ui.UiUtils.dpToPx
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.WeakHashMap

class SnackBarHandler(
    val app: App,
) {

    private val messageFlow = app.messageFlow
    private val messages = mutableListOf<Message>()

    suspend fun create(message: Message) {
        if (messages.isEmpty()) messageFlow.emit(message)
        if (!messages.contains(message)) messages.add(message)
    }


    suspend fun remove(message: Message, dismissed: Boolean) {
        if (dismissed) messages.remove(message)
        if (messages.isNotEmpty()) messageFlow.emit(messages.first())
    }
}
