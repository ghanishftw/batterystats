package com.gnsh.batterystats

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import com.gnsh.batterystats.service.MonitorService
import com.gnsh.batterystats.ui.AppRoot
import com.gnsh.batterystats.ui.MainViewModel
import com.gnsh.batterystats.ui.theme.BatteryStatsTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startMonitorIfEnabled() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            BatteryStatsTheme(dynamicColor = settings.dynamicColor) {
                AppRoot(vm)
            }
        }
    }

    private fun startMonitorIfEnabled() {
        lifecycleScope.launch {
            if (app.settings.settings.first().monitorEnabled) MonitorService.start(this@MainActivity)
        }
    }
}
