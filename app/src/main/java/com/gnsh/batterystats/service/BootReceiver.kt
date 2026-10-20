package com.gnsh.batterystats.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gnsh.batterystats.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (context.app.settings.settings.first().monitorEnabled) MonitorService.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}
