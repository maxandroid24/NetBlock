package com.example.service.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("netblock_prefs", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("auto_start_firewall", true)

            if (autoStart) {
                val serviceIntent = Intent(context, NetBlockVpnService::class.java).apply {
                    action = NetBlockVpnService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
