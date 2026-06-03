package com.aiadbot.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TargetApp::class, OperationRecord::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun targetAppDao(): TargetAppDao
    abstract fun operationRecordDao(): OperationRecordDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "aiadbot_db")
                    .fallbackToDestructiveMigration() // 开发阶段允许清空数据
                    .build().also { INSTANCE = it }
            }
        }
    }
}
