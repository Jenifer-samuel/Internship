package com.anitha.remindme.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: UserEntity): Long

    @Query("SELECT * FROM users ORDER BY name")
    fun observeUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY name")
    suspend fun getUsers(): List<UserEntity>

    @Query("UPDATE users SET faceHash = :hash WHERE id = :userId")
    suspend fun updateFaceHash(userId: Long, hash: String?)

    @Query("SELECT * FROM users WHERE faceHash IS NOT NULL")
    suspend fun getEnrolledUsers(): List<UserEntity>
}

@Dao
interface TaskDao {
    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET completed = 1 WHERE id = :id")
    suspend fun markCompleted(id: Long)

    @Query("SELECT * FROM tasks WHERE userId = :userId AND completed = 0 ORDER BY time")
    fun observePendingTasks(userId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE userId = :userId ORDER BY time")
    suspend fun getTasks(userId: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE completed = 0")
    suspend fun getAllPending(): List<TaskEntity>
}
