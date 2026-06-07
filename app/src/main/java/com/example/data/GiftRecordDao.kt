package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GiftRecordDao {
    @Query("SELECT * FROM gift_records ORDER BY date DESC")
    fun getAllRecords(): Flow<List<GiftRecord>>

    @Query("SELECT * FROM gift_records WHERE personName LIKE '%' || :query || '%' OR eventType LIKE '%' || :query || '%' OR giftDescription LIKE '%' || :query || '%' ORDER BY date DESC")
    fun searchRecords(query: String): Flow<List<GiftRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: GiftRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<GiftRecord>)

    @Update
    suspend fun updateRecord(record: GiftRecord)

    @Delete
    suspend fun deleteRecord(record: GiftRecord)

    @Query("DELETE FROM gift_records WHERE id = :id")
    suspend fun deleteRecordById(id: Int)

    @Query("DELETE FROM gift_records")
    suspend fun deleteAllRecords()
}
