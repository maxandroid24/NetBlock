package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "statistics")
data class StatisticsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateString: String, // e.g. "2026-06-18" or "Thu"
    val packageName: String,
    val appName: String,
    val blockedRequests: Int,
    val dataSavedBytes: Long,
    val timestamp: Long = System.currentTimeMillis()
)
