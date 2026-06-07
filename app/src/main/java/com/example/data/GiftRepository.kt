package com.example.data

import kotlinx.coroutines.flow.Flow

class GiftRepository(private val giftRecordDao: GiftRecordDao) {
    val allRecords: Flow<List<GiftRecord>> = giftRecordDao.getAllRecords()

    fun searchRecords(query: String): Flow<List<GiftRecord>> {
        return if (query.isBlank()) {
            giftRecordDao.getAllRecords()
        } else {
            giftRecordDao.searchRecords(query)
        }
    }

    suspend fun insertRecord(record: GiftRecord): Long {
        return giftRecordDao.insertRecord(record)
    }

    suspend fun insertRecords(records: List<GiftRecord>) {
        giftRecordDao.insertRecords(records)
    }

    suspend fun updateRecord(record: GiftRecord) {
        giftRecordDao.updateRecord(record)
    }

    suspend fun deleteRecord(record: GiftRecord) {
        giftRecordDao.deleteRecord(record)
    }

    suspend fun deleteRecordById(id: Int) {
        giftRecordDao.deleteRecordById(id)
    }

    suspend fun deleteAllRecords() {
        giftRecordDao.deleteAllRecords()
    }
}
