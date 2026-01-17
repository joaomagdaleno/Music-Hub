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
import com.joaomagdaleno.music_hub.MainApplication
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.databinding.DialogSettingsBinding
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.ui.download.DownloadFragment
import com.joaomagdaleno.music_hub.utils.ContextUtils
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
            UiUtils.openFragment<SettingsPlayerFragment>(requireActivity())
        }

        binding.lookAndFeel.setOnClickListener {
            dismiss()
            UiUtils.openFragment<SettingsLookFragment>(requireActivity())
        }

        binding.other.setOnClickListener {
            dismiss()
            UiUtils.openFragment<SettingsOtherFragment>(requireActivity())
        }

        binding.downloads.setOnClickListener {
            dismiss()
            UiUtils.openFragment<DownloadFragment>(requireActivity())
        }


        val settings = ContextUtils.getSettings(requireContext())
        val language = MainApplication.getCurrentLanguage(settings)
        val languages = mapOf("system" to getString(R.string.system)) + MainApplication.languages
        val langList = languages.entries.toList()
        binding.language.run {
            text = getString(R.string.language_x, languages[language])
            setOnClickListener {
                MaterialAlertDialogBuilder(context)
                    .setSingleChoiceItems(
                        langList.map { it.value }.toTypedArray(),
                        langList.indexOfFirst { it.key == language }
                    ) { dialog, which ->
                        MainApplication.setCurrentLanguage(settings, langList[which].key)
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
        }

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
            val version = ContextUtils.appVersion()
            text = version
            setOnClickListener {
                val info = buildString {
                    appendLine("Music Hub Version: $version")
                    appendLine("Device: $BRAND $DEVICE")
                    appendLine("Architecture: ${ContextUtils.getArch()}")
                    appendLine("OS Version: $CODENAME $RELEASE ($SDK_INT)")
                }
                ContextUtils.copyToClipboard(requireContext(), getString(R.string.version), info)
            }
        }

        binding.shivam.isVisible = false
    }
}