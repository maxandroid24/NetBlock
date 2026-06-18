package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "firewall_rules")
data class FirewallRuleEntity(
    @PrimaryKey val ruleId: String, // e.g. "wifi_com.example"
    val packageName: String,
    val enabled: Boolean
)
