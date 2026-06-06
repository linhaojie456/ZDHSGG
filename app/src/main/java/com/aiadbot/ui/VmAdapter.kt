package com.aiadbot.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aiadbot.databinding.ItemVmBinding
import com.aiadbot.model.VirtualMachine

class VmAdapter(private val onToggle: (VirtualMachine) -> Unit) :
    ListAdapter<VirtualMachine, VmAdapter.VH>(object : DiffUtil.ItemCallback<VirtualMachine>() {
        override fun areItemsTheSame(a: VirtualMachine, b: VirtualMachine) = a.id == b.id
        override fun areContentsTheSame(a: VirtualMachine, b: VirtualMachine) = a == b
    }) {
    inner class VH(private val b: ItemVmBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(vm: VirtualMachine) {
            b.tvVmAddress.text = vm.host
            b.switchVmEnabled.isChecked = vm.enabled
            b.switchVmEnabled.setOnCheckedChangeListener { _, _ -> onToggle(vm) }
        }
    }
    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(ItemVmBinding.inflate(LayoutInflater.from(p.context), p, false))
    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))
}
