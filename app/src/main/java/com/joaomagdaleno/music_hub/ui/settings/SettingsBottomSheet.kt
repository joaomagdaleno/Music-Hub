package com.joaomagdaleno.music_hub.ui.settings

import android.graphics.drawable.Animatable
import android.os.Build.BRAND
import android.os.Build.DEVICE
import android.os.Build.VERSION.CODENAME
import android.os.Build.VERSION.RELEASE
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.joaomagdaleno.music_hub.MainApplication.Companion.getCurrentLanguage
import com.joaomagdaleno.music_hub.MainApplication.Companion.languages
import com.joaomagdaleno.music_hub.MainApplication.Companion.setCurrentLanguage
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.databinding.DialogSettingsBinding
import com.joaomagdaleno.music_hub.ui.common.FragmentUtils.openFragment
import com.joaomagdaleno.music_hub.ui.download.DownloadFragment
import com.joaomagdaleno.music_hub.utils.ContextUtils.appVersion
import com.joaomagdaleno.music_hub.utils.ContextUtils.copyToClipboard
import com.joaomagdaleno.music_hub.utils.ContextUtils.getArch
import com.joaomagdaleno.music_hub.utils.ContextUtils.getSettings
import kotlin.random.Random

class SettingsBottomSheet : BottomSheetDialogFragment(R.layout.dialog_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = DialogSettingsBinding.bind(view)

        binding.closeButton.setOnClickListener { dismiss() }
        binding.logoImage.setOnClickListener {
            binding.logoImage.setImageResource(R.drawable.art_splash_anim)
            (binding.logoImage.drawable as Animatable).start()
            when (Random.nextInt(5)) {
                0 -> Toast.makeText(it.context, "Ooo what do we have here?", Toast.LENGTH_SHORT)
                    .show()

                2 -> Toast.makeText(it.context, "Nothing to see here.", Toast.LENGTH_SHORT).show()
            }
        }
        (binding.logoImage.drawable as Animatable).start()

        binding.player.setOnClickListener {
            dismiss()
            requireActivity().openFragment<SettingsPlayerFragment>()
        }

        binding.lookAndFeel.setOnClickListener {
            dismiss()
            requireActivity().openFragment<SettingsLookFragment>()
        }

        binding.other.setOnClickListener {
            dismiss()
            requireActivity().openFragment<SettingsOtherFragment>()
        }

        binding.downloads.setOnClickListener {
            dismiss()
            requireActivity().openFragment<DownloadFragment>()
        }


        val settings = requireContext().getSettings()
        val language = getCurrentLanguage(settings)
        val languages = mapOf("system" to getString(R.string.system)) + languages
        val langList = languages.entries.toList()
        binding.language.run {
            text = getString(R.string.language_x, languages[language])
            setOnClickListener {
                MaterialAlertDialogBuilder(context)
                    .setSingleChoiceItems(
                        langList.map { it.value }.toTypedArray(),
                        langList.indexOfFirst { it.key == language }
                    ) { dialog, which ->
                        setCurrentLanguage(settings, langList[which].key)
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                    .setTitle(getString(R.string.select_language))
                    .create()
                    .show()
                dismiss()
            }
        }

        binding.wiki.setOnClickListener {
            dismiss()
            // Placeholder link or removed
        }

        val repo = getString(R.string.app_github_repo)
        binding.contributors.setOnClickListener {
            dismiss()
        }

        binding.donate.setOnClickListener {
            dismiss()
        }

        binding.discord.setOnClickListener {
             dismiss()
        }

        binding.github.setOnClickListener {
            dismiss()
        }

        binding.telegram.setOnClickListener {
            dismiss()
        }

        binding.version.run {
            val version = appVersion()
            text = version
            setOnClickListener {
                val info = buildString {
                    appendLine("Music Hub Version: $version")
                    appendLine("Device: $BRAND $DEVICE")
                    appendLine("Architecture: ${getArch()}")
                    appendLine("OS Version: $CODENAME $RELEASE ($SDK_INT)")
                }
                context.copyToClipboard(getString(R.string.version), info)
            }
        }

        binding.shivam.isVisible = false
    }
}