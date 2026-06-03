package com.aiadbot.data
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aiadbot.model.TargetApp
import com.aiadbot.model.VirtualMachine

@Database(entities = [TargetApp::class, VirtualMachine::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun targetAppDao(): TargetAppDao
    abstract fun vmDao(): VmDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "aiadbot_db").build().also { INSTANCE = it }
            }
        }
    }
}
