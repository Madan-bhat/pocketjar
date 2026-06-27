package com.mchost

import android.Manifest
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mchost.ui.navigation.MCHostAppNav
import com.mchost.ui.theme.Background
import com.mchost.ui.theme.MCHostTheme
import com.mchost.viewmodel.MCHostViewModel

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* optional for v1 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        setContent {
            MCHostTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Background) {
                    val vm: MCHostViewModel = viewModel(
                        factory = MCHostViewModelFactory(application),
                    )
                    MCHostAppNav(vm)
                }
            }
        }
    }
}

class MCHostApp : Application()

class MCHostViewModelFactory(private val app: Application) :
    androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MCHostViewModel::class.java)) {
            return MCHostViewModel(app) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
