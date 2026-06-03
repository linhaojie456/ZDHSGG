package com.aiadbot.data
import androidx.lifecycle.LiveData
import androidx.room.*
import com.aiadbot.model.VirtualMachine

@Dao
interface VmDao {
    @Query("SELECT * FROM vms")
    fun getAll(): LiveData<List<VirtualMachine>>
    @Insert
    suspend fun insert(vm: VirtualMachine)
    @Update
    suspend fun update(vm: VirtualMachine)
    @Delete
    suspend fun delete(vm: VirtualMachine)
}
