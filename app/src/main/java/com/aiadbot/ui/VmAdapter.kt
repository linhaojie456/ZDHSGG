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
            b.btnDeleteVm.setOnClickListener { /* 删除功能自行添加 */ }
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemVmBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
