package com.aiadbot.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vms")
data class VirtualMachine(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val host: String,
    var enabled: Boolean = true
)
