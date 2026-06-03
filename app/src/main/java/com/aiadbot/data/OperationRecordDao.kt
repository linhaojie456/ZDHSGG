package com.aiadbot.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OperationRecordDao {
    @Query("SELECT * FROM operation_records ORDER BY timestamp DESC")
    fun getAll(): Flow<List<OperationRecord>>

    @Query("SELECT * FROM operation_records WHERE packageName = :pkg ORDER BY timestamp DESC")
    fun getByPackage(pkg: String): Flow<List<OperationRecord>>

    @Query("SELECT * FROM operation_records WHERE packageName = :pkg AND stateHash = :state AND rewardObtained = 1")
    suspend fun getSuccessfulActions(pkg: String, state: String): List<OperationRecord>

    @Insert
    suspend fun insert(record: OperationRecord)

    @Query("DELETE FROM operation_records")
    suspend fun clearAll()
}
