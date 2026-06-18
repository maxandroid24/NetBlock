package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val blockedWifi: Boolean,
    val blockedMobileData: Boolean,
    val isBlockedOverall: Boolean = false
)
