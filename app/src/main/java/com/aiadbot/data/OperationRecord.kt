package com.aiadbot.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "operation_records")
data class OperationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val stateHash: String,      // 操作前的界面状态哈希
    val action: String,         // 用户执行的动作 (CLICK:xxx, WAIT:xxx等)
    val rewardObtained: Boolean, // 动作后是否获得奖励
    val timestamp: Long = System.currentTimeMillis()
)
