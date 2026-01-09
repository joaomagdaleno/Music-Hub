package com.joaomagdaleno.music_hub.ui.main

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.ImageHolder
import com.joaomagdaleno.music_hub.databinding.ItemMainHeaderBinding
import com.joaomagdaleno.music_hub.ui.common.GridAdapter
import com.joaomagdaleno.music_hub.ui.settings.SettingsBottomSheet
import com.joaomagdaleno.music_hub.utils.image.ImageUtils.loadAsCircle
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimRecyclerAdapter
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimViewHolder

class HeaderAdapter(
    private val fragment: Fragment,
) : ScrollAnimRecyclerAdapter<HeaderAdapter.ViewHolder>(), GridAdapter {

    override val adapter = this
    override fun getSpanSize(position: Int, width: Int, count: Int) = count
    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val holder = ViewHolder(parent)
        val binding = holder.binding
        val parentFragmentManager = fragment.parentFragmentManager

        binding.accountsCont.setOnClickListener {
            SettingsBottomSheet().show(parentFragmentManager, null)
        }
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = with(holder.binding) {
        super.onBindViewHolder(holder, position)
        val context = root.context
        title.text = context.getString(R.string.app_name)
        
        // In monolithic mode, just show a generic settings/account icon
        accounts.loadBigIcon(null, R.drawable.ic_settings_outline_32dp)
    }

    class ViewHolder(
        parent: ViewGroup,
        val binding: ItemMainHeaderBinding = ItemMainHeaderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        ),
    ) : ScrollAnimViewHolder(binding.root)

    companion object {
        fun <T> View.setLoopedLongClick(
            list: List<T>,
            getCurrent: (View) -> T?,
            onSelect: (T) -> Unit,
        ) {
            // Disabled in monolithic mode
        }

        fun ImageView.loadBigIcon(image: ImageHolder?, placeholder: Int) {
            val color = ColorStateList.valueOf(
                MaterialColors.getColor(
                    this,
                    androidx.appcompat.R.attr.colorControlNormal
                )
            )
            image.loadAsCircle(this) {
                if (it == null) {
                    imageTintList = color
                    setImageResource(placeholder)
                } else {
                    imageTintList = null
                    setImageDrawable(it)
                }
            }
        }
    }
}