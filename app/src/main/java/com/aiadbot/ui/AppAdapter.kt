package com.aiadbot.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aiadbot.data.TargetApp
import com.aiadbot.databinding.ItemAppBinding

class AppAdapter(
    private val onToggle: (TargetApp) -> Unit,
    private val onDelete: (TargetApp) -> Unit
) : ListAdapter<TargetApp, AppAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: TargetApp) {
            binding.tvAppName.text = app.appName
            binding.tvReward.text = "收益: ${app.reward}"
            binding.switchEnabled.isChecked = app.enabled
            binding.switchEnabled.setOnCheckedChangeListener { _, _ -> onToggle(app) }
            binding.root.setOnLongClickListener { onDelete(app); true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    object DiffCallback : DiffUtil.ItemCallback<TargetApp>() {
        override fun areItemsTheSame(old: TargetApp, new: TargetApp) = old.packageName == new.packageName
        override fun areContentsTheSame(old: TargetApp, new: TargetApp) = old == new
    }
}
