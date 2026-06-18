package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {

    // --- Blocked Apps Queries ---
    @Query("SELECT * FROM blocked_apps ORDER BY appName ASC")
    fun getAllBlockedAppsFlow(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps")
    suspend fun getBlockedAppsList(): List<BlockedAppEntity>

    @Query("SELECT * FROM blocked_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getBlockedAppByPackage(packageName: String): BlockedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBlockedApp(app: BlockedAppEntity)

    @Delete
    suspend fun deleteBlockedApp(app: BlockedAppEntity)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun deleteBlockedAppByPackage(packageName: String)

    @Query("DELETE FROM blocked_apps")
    suspend fun clearAllBlockedApps()

    // --- Firewall Rules Queries ---
    @Query("SELECT * FROM firewall_rules")
    fun getAllFirewallRulesFlow(): Flow<List<FirewallRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateFirewallRule(rule: FirewallRuleEntity)

    @Query("DELETE FROM firewall_rules WHERE packageName = :packageName")
    suspend fun deleteFirewallRulesByPackage(packageName: String)

    @Query("DELETE FROM firewall_rules")
    suspend fun clearAllFirewallRules()

}
