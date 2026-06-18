package com.example.presentation.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.BlockedAppEntity

import com.example.data.repository.BlockedAppRepository
import com.example.domain.model.AppInfo
import com.example.service.vpn.NetBlockVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

sealed class Screen {
    object Onboarding : Screen()
    object VpnPermission : Screen()
    object Home : Screen()
    object AppDetails : Screen()
    object FilterSort : Screen()
    object Settings : Screen()
}

enum class SortType {
    NAME_A_Z,
    NAME_Z_A,
    DATA_USAGE_HIGH,
    DATA_USAGE_LOW,
    INSTALL_DATE
}

enum class FilterSelection {
    ALL,
    BLOCKED,
    ALLOWED,
    SYSTEM,
    USER
}

class NetBlockViewModel(private val repository: BlockedAppRepository) : ViewModel() {

    private val _activeScreen = MutableStateFlow<Screen>(Screen.Onboarding)
    val activeScreen: StateFlow<Screen> = _activeScreen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(FilterSelection.ALL)
    val selectedFilter: StateFlow<FilterSelection> = _selectedFilter.asStateFlow()

    private val _selectedSort = MutableStateFlow(SortType.NAME_A_Z)
    val selectedSort: StateFlow<SortType> = _selectedSort.asStateFlow()

    private val _selectedApp = MutableStateFlow<AppInfo?>(null)
    val selectedApp: StateFlow<AppInfo?> = _selectedApp.asStateFlow()

    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()

    private val _rawInstalledApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _isRefreshingApps = MutableStateFlow(false)
    val isRefreshingApps: StateFlow<Boolean> = _isRefreshingApps.asStateFlow()

    // Combined Apps flow which reactively filters and sorts based on user choices
    val appsList: StateFlow<List<AppInfo>> = combine(
        _rawInstalledApps,
        repository.allBlockedAppsFlow,
        _searchQuery,
        _selectedFilter,
        _selectedSort
    ) { rawApps, blockedDbApps, query, filter, sort ->
        val blockedMap = blockedDbApps.associateBy { it.packageName }
        
        // Merge room changes back into installed apps
        val mergedList = rawApps.map { app ->
            val dbApp = blockedMap[app.packageName]
            app.copy(
                blockedWifi = dbApp?.blockedWifi ?: false,
                blockedMobileData = dbApp?.blockedMobileData ?: false,
                isBlockedOverall = dbApp?.isBlockedOverall ?: false
            )
        }

        // Apply Search
        var filtered = if (query.isBlank()) {
            mergedList
        } else {
            mergedList.filter { it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
        }

        // Apply Filter chips
        filtered = when (filter) {
            FilterSelection.ALL -> filtered
            FilterSelection.BLOCKED -> filtered.filter { it.blockedWifi || it.blockedMobileData }
            FilterSelection.ALLOWED -> filtered.filter { !it.blockedWifi && !it.blockedMobileData }
            FilterSelection.SYSTEM -> filtered.filter { it.isSystemApp }
            FilterSelection.USER -> filtered.filter { !it.isSystemApp }
        }

        // Apply Sorting
        val sorted = when (sort) {
            SortType.NAME_A_Z -> filtered.sortedBy { it.appName.lowercase() }
            SortType.NAME_Z_A -> filtered.sortedByDescending { it.appName.lowercase() }
            SortType.DATA_USAGE_HIGH -> filtered.sortedByDescending { it.dataUsageBytes }
            SortType.DATA_USAGE_LOW -> filtered.sortedBy { it.dataUsageBytes }
            SortType.INSTALL_DATE -> filtered.sortedByDescending { it.installDate }
        }

        sorted
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalBlockedCount = repository.allBlockedAppsFlow.map { list ->
        list.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun navigateTo(screen: Screen) {
        _activeScreen.value = screen
    }

    fun completeOnboarding(context: Context) {
        val prefs = context.getSharedPreferences("netblock_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("has_completed_onboarding", true).apply()
        navigateTo(Screen.Home)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: FilterSelection) {
        _selectedFilter.value = filter
    }

    fun setSort(sort: SortType) {
        _selectedSort.value = sort
    }

    fun selectAppDetails(app: AppInfo?) {
        _selectedApp.value = app
    }

    fun syncVpnStatus() {
        _isVpnActive.value = NetBlockVpnService.isRunning
    }

    fun loadApps(context: Context) {
        viewModelScope.launch {
            _isRefreshingApps.value = true
            val apps = repository.fetchInstalledApps(context)
            _rawInstalledApps.value = apps
            _isRefreshingApps.value = false
            syncVpnStatus()
        }
    }

    fun toggleAppBlock(context: Context, app: AppInfo, blockWifi: Boolean, blockMobile: Boolean) {
        viewModelScope.launch {
            val isOverallBlocked = blockWifi || blockMobile
            val entity = BlockedAppEntity(
                packageName = app.packageName,
                appName = app.appName,
                blockedWifi = blockWifi,
                blockedMobileData = blockMobile,
                isBlockedOverall = isOverallBlocked
            )

            if (isOverallBlocked) {
                repository.saveBlockedApp(entity)
            } else {
                repository.removeBlockedApp(app.packageName)
            }

            // Reload raw app with updated mapping to ensure immediate UI sync
            _rawInstalledApps.value = _rawInstalledApps.value.map {
                if (it.packageName == app.packageName) {
                    it.copy(blockedWifi = blockWifi, blockedMobileData = blockMobile, isBlockedOverall = isOverallBlocked)
                } else it
            }

            // Sync with VPN Service
            if (NetBlockVpnService.isRunning) {
                val restartIntent = Intent(context, NetBlockVpnService::class.java).apply {
                    action = NetBlockVpnService.ACTION_RESTART
                }
                context.startService(restartIntent)
            }
        }
    }

    fun setMasterToggleState(context: Context, active: Boolean) {
        _isVpnActive.value = active
        val intent = Intent(context, NetBlockVpnService::class.java).apply {
            action = if (active) NetBlockVpnService.ACTION_START else NetBlockVpnService.ACTION_STOP
        }
        if (active) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.startService(intent)
        }
    }

    // --- Export / Import ---
    fun exportRules(context: Context, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = repository.getBlockedAppsList()
                val file = File(context.filesDir, "netblock_rules_backup.txt")
                file.printWriter().use { out ->
                    list.forEach { app ->
                        out.println("${app.packageName},${app.appName},${app.blockedWifi},${app.blockedMobileData}")
                    }
                }
                withContext(Dispatchers.Main) {
                    onResult(true, file.absolutePath)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, e.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    fun importRules(context: Context, backupPath: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(backupPath)
                if (!file.exists()) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Backup file not found.")
                    }
                    return@launch
                }
                
                file.forEachLine { line ->
                    val parts = line.split(",")
                    if (parts.size >= 4) {
                        val pkg = parts[0]
                        val name = parts[1]
                        val wifi = parts[2].toBoolean()
                        val mobile = parts[3].toBoolean()
                        
                        viewModelScope.launch {
                            val entity = BlockedAppEntity(pkg, name, wifi, mobile, wifi || mobile)
                            repository.saveBlockedApp(entity)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    onResult(true, "Rules successfully imported!")
                    loadApps(context)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, e.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

}

class NetBlockViewModelFactory(private val repository: BlockedAppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NetBlockViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NetBlockViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
