package com.aiadbot.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TargetApp::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun targetAppDao(): TargetAppDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "aiadbot_db")
                    .build().also { INSTANCE = it }
            }
        }
    }
}
