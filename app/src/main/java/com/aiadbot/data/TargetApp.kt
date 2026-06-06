package com.aiadbot.data
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "target_apps")
data class TargetApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    var enabled: Boolean = true,
    var reward: Long = 0L
)
