package com.example

import android.app.Application
import com.example.data.local.NetBlockDatabase
import com.example.data.repository.BlockedAppRepository

class NetBlockApp : Application() {
    val database by lazy { NetBlockDatabase.getDatabase(this) }
    val repository by lazy { BlockedAppRepository(database.blockedAppDao()) }

    override fun onCreate() {
        super.onCreate()
    }
}
