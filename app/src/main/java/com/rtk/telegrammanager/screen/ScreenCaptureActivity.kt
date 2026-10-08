package com.rtk.telegrammanager.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.core.content.ContextCompat

class ScreenCaptureActivity : Activity() {

    private val projectionManager: MediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
         * This screen is used ONLY during setup to obtain the user's
         * explicit MediaProjection consent.
         *
         * The resulting projection session is then kept alive by
         * ScreenCaptureService. /screenshot does NOT launch this
         * consent dialog again while the session is active.
         */
        val captureIntent = projectionManager.createScreenCaptureIntent()
        startActivityForResult(captureIntent, REQUEST_CODE)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (
            requestCode == REQUEST_CODE &&
            resultCode == RESULT_OK &&
            data != null
        ) {
            try {
                val serviceIntent = Intent(
                    this,
                    ScreenCaptureService::class.java
                ).apply {
                    action = ScreenCaptureService.ACTION_START_PROJECTION
                    putExtra(
                        ScreenCaptureService.EXTRA_RESULT_CODE,
                        resultCode
                    )
                    putExtra(
                        ScreenCaptureService.EXTRA_RESULT_DATA,
                        data
                    )
                }

                ContextCompat.startForegroundService(
                    this,
                    serviceIntent
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        finish()
    }

    companion object {
        private const val REQUEST_CODE = 9981
    }
}
