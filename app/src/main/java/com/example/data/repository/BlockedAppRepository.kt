package com.example.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.data.local.BlockedAppDao
import com.example.data.local.BlockedAppEntity
import com.example.data.local.FirewallRuleEntity
import com.example.data.local.StatisticsEntity
import com.example.domain.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class BlockedAppRepository(private val dao: BlockedAppDao) {

    // Streams from Database
    val allBlockedAppsFlow: Flow<List<BlockedAppEntity>> = dao.getAllBlockedAppsFlow()
    val allFirewallRulesFlow: Flow<List<FirewallRuleEntity>> = dao.getAllFirewallRulesFlow()
    val allStatisticsFlow: Flow<List<StatisticsEntity>> = dao.getAllStatisticsFlow()

    suspend fun getBlockedAppsList(): List<BlockedAppEntity> {
        return dao.getBlockedAppsList()
    }

    suspend fun getBlockedAppByPackage(packageName: String): BlockedAppEntity? {
        return dao.getBlockedAppByPackage(packageName)
    }

    suspend fun saveBlockedApp(app: BlockedAppEntity) {
        dao.insertOrUpdateBlockedApp(app)

        // Keep firewall_rules entity in sync as well
        val wifiRule = FirewallRuleEntity(
            ruleId = "wifi_${app.packageName}",
            packageName = app.packageName,
            enabled = app.blockedWifi
        )
        val mobileRule = FirewallRuleEntity(
            ruleId = "mobile_${app.packageName}",
            packageName = app.packageName,
            enabled = app.blockedMobileData
        )
        dao.insertOrUpdateFirewallRule(wifiRule)
        dao.insertOrUpdateFirewallRule(mobileRule)
    }

    suspend fun removeBlockedApp(packageName: String) {
        dao.deleteBlockedAppByPackage(packageName)
        dao.deleteFirewallRulesByPackage(packageName)
    }

    suspend fun addBlockedStatistics(stat: StatisticsEntity) {
        dao.insertStatistics(stat)
    }

    suspend fun clearAllBlockedApps() {
        dao.clearAllBlockedApps()
        dao.clearAllFirewallRules()
    }

    suspend fun clearAllStatistics() {
        dao.clearAllStatistics()
    }

    /**
     * Fetches all installed apps, cross-referencing with our Room DB blocked settings.
     */
    suspend fun fetchInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val blockedAppsMap = dao.getBlockedAppsList().associateBy { it.packageName }

        installedApps.mapNotNull { appInfo ->
            // Skip NetBlock itself to prevent accidental lockouts
            if (appInfo.packageName == context.packageName) return@mapNotNull null

            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            var appName = appInfo.loadLabel(pm).toString()
            if (appName.isEmpty()) {
                appName = appInfo.packageName
            }

            val icon = try {
                appInfo.loadIcon(pm)
            } catch (e: Exception) {
                null
            }

            val installerInfo = try {
                val pInfo = pm.getPackageInfo(appInfo.packageName, 0)
                Pair(pInfo.firstInstallTime, File(appInfo.sourceDir).length())
            } catch (e: Exception) {
                Pair(0L, 0L)
            }

            val dbRecord = blockedAppsMap[appInfo.packageName]
            val blockedWifi = dbRecord?.blockedWifi ?: false
            val blockedMobileData = dbRecord?.blockedMobileData ?: false
            val isBlockedOverall = dbRecord?.isBlockedOverall ?: false

            // Mock data usage so the UI has visual stats
            val packageSeed = appInfo.packageName.hashCode().toLong()
            val dataUsageBytes = Math.abs(packageSeed % (250 * 1024 * 1024)) + 1024 * 1024

            AppInfo(
                packageName = appInfo.packageName,
                appName = appName,
                icon = icon,
                isSystemApp = isSystemApp,
                blockedWifi = blockedWifi,
                blockedMobileData = blockedMobileData,
                isBlockedOverall = isBlockedOverall,
                sizeOnDiskBytes = installerInfo.second,
                installDate = installerInfo.first,
                dataUsageBytes = dataUsageBytes
            )
        }
    }
}
