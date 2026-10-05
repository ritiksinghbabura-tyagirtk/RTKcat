package com.rtk.telegrammanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.rtk.telegrammanager.data.AppConfig
import com.rtk.telegrammanager.service.BotForegroundService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        val config = AppConfig(context)

        if (!config.isSetupComplete()) {
            return
        }

        if (!config.isBotEnabled()) {
            return
        }

        /*
         * Android version/manufacturer restrictions can prevent
         * arbitrary foreground-service starts directly from boot.
         *
         * We attempt the supported recovery path.
         */
        runCatching {

            ContextCompat.startForegroundService(
                context,
                Intent(
                    context,
                    BotForegroundService::class.java
                )
            )
        }
    }
}
