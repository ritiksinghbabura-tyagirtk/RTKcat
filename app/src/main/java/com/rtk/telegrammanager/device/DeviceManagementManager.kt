package com.rtk.telegrammanager.device

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.rtk.telegrammanager.security.RTKDeviceAdminReceiver

data class DeviceManagementStatus(
    val isDeviceOwner: Boolean,
    val isAdminActive: Boolean,
    val uninstallBlocked: Boolean,
    val provisioningAllowed: Boolean
)

class DeviceManagementManager(
    private val context: Context
) {

    private val appContext = context.applicationContext

    private val devicePolicyManager =
        appContext.getSystemService(
            DevicePolicyManager::class.java
        )

    private val adminComponent =
        ComponentName(
            appContext,
            RTKDeviceAdminReceiver::class.java
        )

    fun isDeviceOwner(): Boolean {
        return runCatching {
            devicePolicyManager.isDeviceOwnerApp(
                appContext.packageName
            )
        }.getOrDefault(false)
    }

    fun isAdminActive(): Boolean {
        return runCatching {
            devicePolicyManager.isAdminActive(
                adminComponent
            )
        }.getOrDefault(false)
    }

    fun isProvisioningAllowed(): Boolean {
        return runCatching {
            devicePolicyManager.isProvisioningAllowed(
                DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE
            )
        }.getOrDefault(false)
    }

    fun isUninstallBlocked(): Boolean {
        if (!isDeviceOwner()) {
            return false
        }

        return runCatching {
            devicePolicyManager.isUninstallBlocked(
                adminComponent,
                appContext.packageName
            )
        }.getOrDefault(false)
    }

    fun applyUninstallProtection(): Boolean {
        if (!isDeviceOwner()) {
            return false
        }

        return runCatching {
            devicePolicyManager.setUninstallBlocked(
                adminComponent,
                appContext.packageName,
                true
            )

            true
        }.getOrDefault(false)
    }

    fun status(): DeviceManagementStatus {
        return DeviceManagementStatus(
            isDeviceOwner = isDeviceOwner(),
            isAdminActive = isAdminActive(),
            uninstallBlocked = isUninstallBlocked(),
            provisioningAllowed = isProvisioningAllowed()
        )
    }
}
