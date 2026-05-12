package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kian.khup.core.data.db.entities.UserMemory

@Dao
interface UserMemoryDao {

    @Query("SELECT * FROM user_memory")
    suspend fun getAll(): List<UserMemory>

    @Query("SELECT * FROM user_memory WHERE type = :type")
    suspend fun getByType(type: String): UserMemory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: UserMemory)

    @Query("DELETE FROM user_memory WHERE type = :type")
    suspend fun deleteByType(type: String)
}
