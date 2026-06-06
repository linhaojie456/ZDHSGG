package com.aiadbot.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aiadbot.data.TargetApp
import com.aiadbot.databinding.ItemAppBinding

class AppAdapter(private val onToggle: (TargetApp) -> Unit) :
    ListAdapter<TargetApp, AppAdapter.VH>(object : DiffUtil.ItemCallback<TargetApp>() {
        override fun areItemsTheSame(a: TargetApp, b: TargetApp) = a.packageName == b.packageName
        override fun areContentsTheSame(a: TargetApp, b: TargetApp) = a == b
    }) {
    inner class VH(private val b: ItemAppBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(app: TargetApp) {
            b.tvAppName.text = app.appName
            b.tvReward.text = "收益: ${app.reward}"
            b.switchEnabled.isChecked = app.enabled
            b.switchEnabled.setOnCheckedChangeListener { _, _ -> onToggle(app) }
        }
    }
    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(ItemAppBinding.inflate(LayoutInflater.from(p.context), p, false))
    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))
}
