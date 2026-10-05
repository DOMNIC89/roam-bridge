package com.roambridge.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsLogDao {

    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int): List<SmsLogEntity>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE timestamp >= :sinceTimestamp")
    fun getMessageCountSinceFlow(sinceTimestamp: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SmsLogEntity): Long

    @Update
    suspend fun update(log: SmsLogEntity)

    @Query("DELETE FROM sms_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sms_logs")
    suspend fun clearAll()
}
