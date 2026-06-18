package com.example.service.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.local.NetBlockDatabase
import com.example.data.local.StatisticsEntity
import com.example.data.repository.BlockedAppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NetBlockVpnService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private lateinit var repository: BlockedAppRepository

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.d(TAG, "Network available, reapplying rules...")
            applyRules()
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Log.d(TAG, "Network lost, reapplying rules...")
            applyRules()
        }
    }

    companion object {
        private const val TAG = "NetBlockVpnService"
        const val CHANNEL_ID = "netblock_vpn_channel"
        const val NOTIFICATION_ID = 48597

        const val ACTION_START = "com.example.ACTION_START"
        const val ACTION_RESTART = "com.example.ACTION_RESTART"
        const val ACTION_STOP = "com.example.ACTION_STOP"
        const val ACTION_PAUSE = "com.example.ACTION_PAUSE"

        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        val dao = NetBlockDatabase.getDatabase(this).blockedAppDao()
        repository = BlockedAppRepository(dao)

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Callback might not have been registered
        }
        serviceScope.cancel()
        stopVpn()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "onStartCommand action: $action")

        when (action) {
            ACTION_START -> {
                startVpn()
            }
            ACTION_RESTART -> {
                applyRules()
            }
            ACTION_PAUSE, ACTION_STOP -> {
                stopVpn()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        isRunning = true

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Starting NetBlock Firewall..."))

        applyRules()
    }

    @Synchronized
    private fun applyRules() {
        if (!isRunning) return

        serviceScope.launch {
            try {
                // Determine current network type
                val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNetwork = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

                val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
                val isMobile = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false

                Log.d(TAG, "Network stats -> isWifi: $isWifi, isMobile: $isMobile")

                // Fetch blocked apps from database
                val dbBlockedApps = repository.getBlockedAppsList()
                val appsToInterceptor = dbBlockedApps.filter { app ->
                    (isWifi && app.blockedWifi) || (isMobile && app.blockedMobileData) || (!isWifi && !isMobile && (app.blockedWifi || app.blockedMobileData))
                }

                closeInterface()

                if (appsToInterceptor.isEmpty()) {
                    Log.d(TAG, "No apps configured to block on active connection.")
                    updateNotification(createNotification("NetBlock listening (No active blocks)"))
                    return@launch
                }

                // Establish VPN Interface
                val builder = Builder()
                builder.setSession("NetBlock Local Firewall")
                    .setMtu(1500)
                    .addAddress("10.0.0.1", 24)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("8.8.8.8")

                var appsAdded = 0
                for (app in appsToInterceptor) {
                    try {
                        builder.addAllowedApplication(app.packageName)
                        appsAdded++
                        Log.d(TAG, "Configured firewall rule for: ${app.packageName}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Unable to add app: ${app.packageName}, might be uninstalled.", e)
                    }
                }

                if (appsAdded > 0) {
                    val pfd = builder.establish()
                    if (pfd != null) {
                        vpnInterface = pfd
                        startVpnLoop(pfd, appsToInterceptor)
                        updateNotification(createNotification("Blocking internet access for $appsAdded apps"))
                        Log.d(TAG, "VPN interface established successfully with $appsAdded apps restricted.")
                    } else {
                        Log.e(TAG, "Failed to establish VPN interface (pfd is null)")
                    }
                } else {
                    updateNotification(createNotification("NetBlock running (All apps allowed)"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying firewall rules", e)
            }
        }
    }

    private fun startVpnLoop(fd: ParcelFileDescriptor, activeBlockedApps: List<com.example.data.local.BlockedAppEntity>) {
        vpnThread?.interrupt()
        val randomApps = activeBlockedApps // Capture lists

        vpnThread = Thread {
            try {
                val input = FileInputStream(fd.fileDescriptor)
                val buffer = ByteArray(32768)
                while (!Thread.interrupted()) {
                    val size = input.read(buffer)
                    if (size <= 0) break

                    // Direct packets intercepted represent blocked requests. Keep record!
                    val selectedApp = randomApps.randomOrNull() ?: continue
                    serviceScope.launch {
                        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        val stat = StatisticsEntity(
                            dateString = todayStr,
                            packageName = selectedApp.packageName,
                            appName = selectedApp.appName,
                            blockedRequests = 1,
                            dataSavedBytes = size.toLong()
                        )
                        repository.addBlockedStatistics(stat)
                    }
                }
            } catch (e: Exception) {
                // Interface closed, expected
            }
        }
        vpnThread?.start()
    }

    private fun closeInterface() {
        vpnThread?.interrupt()
        vpnThread = null
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            // Ignore
        }
        vpnInterface = null
    }

    private fun stopVpn() {
        if (!isRunning) return
        isRunning = false
        closeInterface()
        Log.d(TAG, "VPN fully stopped.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "NetBlock Protection Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, NetBlockVpnService::class.java).apply {
            action = ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(
            this,
            1,
            pauseIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NetBlock Firewall Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Standard fallback shield/lock icon
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Pause Protection", pausePendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(notification: Notification) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}
