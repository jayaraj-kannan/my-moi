package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gift_records")
data class GiftRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val personName: String,
    val eventType: String,
    val giftType: String, // "Cash" or "Gift"
    val giftDescription: String,
    val amount: Double,
    val isReceived: Boolean, // true = Received, false = Given
    val date: Long, // timestamp
    val notes: String = "",
    val customFields: String = "" // Serialized format "key1::value1||key2::value2"
) {
    companion object {
        fun parseCustomFields(serialized: String): Map<String, String> {
            if (serialized.isBlank()) return emptyMap()
            val map = mutableMapOf<String, String>()
            serialized.split("||").forEach { pair ->
                val parts = pair.split("::", limit = 2)
                if (parts.size == 2) {
                    map[parts[0]] = parts[1]
                }
            }
            return map
        }

        fun formatCustomFields(map: Map<String, String>): String {
            return map.entries.joinToString("||") { "${it.key}::${it.value}" }
        }
    }
}
