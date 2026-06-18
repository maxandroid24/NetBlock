package com.example.domain.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    val blockedWifi: Boolean,
    val blockedMobileData: Boolean,
    val isBlockedOverall: Boolean,
    val sizeOnDiskBytes: Long = 0,
    val installDate: Long = 0,
    val dataUsageBytes: Long = 0 // Mocked usage statistics
)
