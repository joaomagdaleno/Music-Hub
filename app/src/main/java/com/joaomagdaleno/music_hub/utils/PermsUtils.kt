package com.joaomagdaleno.music_hub.utils

import android.Manifest
import android.Manifest.permission.READ_MEDIA_AUDIO
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.joaomagdaleno.music_hub.R
import kotlinx.coroutines.launch
import java.util.UUID

object PermsUtils {

    fun checkAppPermissions(activity: ComponentActivity, onGranted: suspend () -> Unit) {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOfNotNull(
            Triple(
                Manifest.permission.POST_NOTIFICATIONS,
                activity.getString(R.string.notifications),
                activity.getString(R.string.notifications_permission_summary)
            ),
            Triple(
                READ_MEDIA_AUDIO,
                activity.getString(R.string.audio),
                activity.getString(R.string.audio_permission_summary)
            )
        ) else listOfNotNull(
            Triple(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                activity.getString(R.string.read_storage),
                activity.getString(R.string.audio_permission_summary)
            ),
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) Triple(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                activity.getString(R.string.write_storage),
                activity.getString(R.string.write_storage_permission_summary)
            ) else null
        )
        getPermissionsLauncher(activity, perms, onGranted = {
            if (it == READ_MEDIA_AUDIO) activity.lifecycleScope.launch {
                onGranted()
            }
        })
    }

    private fun getPermissionsLauncher(
        activity: ComponentActivity,
        perms: List<Triple<String, String, String>>,
        onCancel: (String) -> Unit = {},
        onGranted: (String) -> Unit = {},
        onRequest: (String) -> Unit = {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", activity.packageName, null)
                activity.startActivity(this)
            }
        }
    ) = run {
        val contract = ActivityResultContracts.RequestMultiplePermissions()
        val notGranted = perms.filterNot {
            ContextCompat.checkSelfPermission(activity, it.first) == PackageManager.PERMISSION_GRANTED
        }
        val launcher = if (notGranted.isNotEmpty())
            registerActivityResultLauncher(activity, contract) { result ->
                val map = perms.associateBy { it.first }
                result.forEach { (p, granted) ->
                    val perm = map[p]!!
                    if (granted) onGranted(p)
                    else MaterialAlertDialogBuilder(activity)
                        .setTitle(activity.getString(R.string.x_permission_required, perm.second))
                        .setMessage(perm.third)
                        .setPositiveButton(activity.getString(R.string.okay)) { _, _ -> onRequest(p) }
                        .setNegativeButton(activity.getString(R.string.cancel)) { _, _ ->
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.x_was_not_granted, perm.second),
                                Toast.LENGTH_SHORT
                            ).show()
                            onCancel(p)
                        }.show()
                }
            }
        else {
            perms.forEach { onGranted(it.first) }
            null
        }
        launcher?.launch(perms.map { it.first }.toTypedArray())
    }

    fun <I, O> registerActivityResultLauncher(
        activity: ComponentActivity,
        contract: ActivityResultContract<I, O>,
        block: (O) -> Unit
    ): ActivityResultLauncher<I> {
        val key = UUID.randomUUID().toString()
        var launcher: ActivityResultLauncher<I>? = null
        val callback = ActivityResultCallback<O> {
            block.invoke(it)
            launcher?.unregister()
        }

        activity.lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                launcher?.unregister()
            }
        })

        launcher = activity.activityResultRegistry.register(key, contract, callback)
        return launcher
    }
}