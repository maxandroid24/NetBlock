package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.presentation.ui.NetBlockMainScreen
import com.example.presentation.viewmodel.NetBlockViewModel
import com.example.presentation.viewmodel.NetBlockViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    
    private lateinit var viewModel: NetBlockViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Retrieve repository singleton from application context delegation
        val repository = (application as NetBlockApp).repository
        
        // Initialize viewmodel
        viewModel = ViewModelProvider(
            this, 
            NetBlockViewModelFactory(repository)
        )[NetBlockViewModel::class.java]

        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme {
                NetBlockMainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh apps list and status upon returning to active window activity
        if (::viewModel.isInitialized) {
            viewModel.loadApps(this)
            viewModel.syncVpnStatus()
        }
    }
}
