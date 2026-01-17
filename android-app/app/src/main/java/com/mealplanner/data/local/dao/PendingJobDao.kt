package com.mealplanner.data.local.dao

import androidx.room.*
import com.mealplanner.data.local.entity.PendingJobEntity

@Dao
interface PendingJobDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: PendingJobEntity)

    @Query("DELETE FROM pending_jobs WHERE jobId = :jobId")
    suspend fun delete(jobId: String)

    @Query("SELECT * FROM pending_jobs WHERE jobType = :jobType LIMIT 1")
    suspend fun getByType(jobType: String): PendingJobEntity?

    @Query("SELECT * FROM pending_jobs")
    suspend fun getAll(): List<PendingJobEntity>

    @Query("DELETE FROM pending_jobs WHERE startedAt < :cutoffTime")
    suspend fun deleteStaleJobs(cutoffTime: Long)
}
