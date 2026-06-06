package com.aiadbot.data
import androidx.lifecycle.LiveData
import androidx.room.*
@Dao
interface TargetAppDao {
    @Query("SELECT * FROM target_apps")
    fun getAll(): LiveData<List<TargetApp>>
    @Query("SELECT * FROM target_apps WHERE enabled = 1")
    suspend fun getEnabled(): List<TargetApp>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: TargetApp)
    @Update
    suspend fun update(app: TargetApp)
    @Delete
    suspend fun delete(app: TargetApp)
    @Query("UPDATE target_apps SET reward = reward + :added WHERE packageName = :pkg")
    suspend fun addReward(pkg: String, added: Long)
}
