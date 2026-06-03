package com.aiadbot.data
import androidx.lifecycle.LiveData
import androidx.room.*
import com.aiadbot.model.TargetApp

@Dao
interface TargetAppDao {
    @Query("SELECT * FROM target_apps")
    fun getAll(): LiveData<List<TargetApp>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: TargetApp)
    @Update
    suspend fun update(app: TargetApp)
    @Delete
    suspend fun delete(app: TargetApp)
    @Query("SELECT * FROM target_apps WHERE enabled = 1")
    suspend fun getEnabled(): List<TargetApp>
}
