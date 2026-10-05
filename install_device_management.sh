#!/usr/bin/env bash
set -euo pipefail

PROJECT="/root/RTKTelegramManager"
cd "$PROJECT"

STAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP="$PROJECT/backup/device_management_$STAMP"

echo "=============================================="
echo " RTK DEVICE MANAGEMENT PATCH"
echo "=============================================="
echo "Project : $PROJECT"
echo "Backup  : $BACKUP"
echo

mkdir -p "$BACKUP"

echo "[1/8] Creating backup..."

cp -f \
  app/src/main/AndroidManifest.xml \
  "$BACKUP/AndroidManifest.xml"

cp -f \
  app/src/main/java/com/rtk/telegrammanager/MainActivity.kt \
  "$BACKUP/MainActivity.kt"

mkdir -p "$BACKUP/values"
mkdir -p "$BACKUP/xml"

if [ -f app/src/main/res/values/strings.xml ]; then
    cp -f app/src/main/res/values/strings.xml \
      "$BACKUP/values/strings.xml"
fi

if [ -f app/src/main/res/xml/device_admin_policies.xml ]; then
    cp -f app/src/main/res/xml/device_admin_policies.xml \
      "$BACKUP/xml/device_admin_policies.xml"
fi

echo "Backup created."
echo

echo "[2/8] Creating directories..."

mkdir -p \
  app/src/main/java/com/rtk/telegrammanager/device \
  app/src/main/java/com/rtk/telegrammanager/security \
  app/src/main/res/xml \
  app/src/main/res/values

echo

echo "[3/8] Creating DeviceAdmin receiver..."

cat > app/src/main/java/com/rtk/telegrammanager/security/RTKDeviceAdminReceiver.kt <<'EOF'
package com.rtk.telegrammanager.security

import android.app.admin.DeviceAdminReceiver

class RTKDeviceAdminReceiver : DeviceAdminReceiver()
EOF

echo "Created:"
echo "  security/RTKDeviceAdminReceiver.kt"
echo

echo "[4/8] Creating Device Management manager..."

cat > app/src/main/java/com/rtk/telegrammanager/device/DeviceManagementManager.kt <<'EOF'
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
EOF

echo "Created:"
echo "  device/DeviceManagementManager.kt"
echo

echo "[5/8] Creating device admin policy XML..."

cat > app/src/main/res/xml/device_admin_policies.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-policies>
        <force-lock />
    </uses-policies>

</device-admin>
EOF

echo "Created:"
echo "  res/xml/device_admin_policies.xml"
echo

echo "[6/8] Creating strings.xml..."

if [ ! -f app/src/main/res/values/strings.xml ]; then

cat > app/src/main/res/values/strings.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <string name="device_admin_label">RTK Telegram Manager Device Management</string>

    <string name="device_admin_description">
        RTK Telegram Manager device management component for legitimately managed devices.
        Device management is enabled only through Android managed-device provisioning.
    </string>

</resources>
EOF

else

python3 - <<'PY'
from pathlib import Path

p = Path("app/src/main/res/values/strings.xml")
s = p.read_text()

entries = """
    <string name="device_admin_label">RTK Telegram Manager Device Management</string>
    <string name="device_admin_description">RTK Telegram Manager device management component for legitimately managed devices. Device management is enabled only through Android managed-device provisioning.</string>
"""

if "name=\"device_admin_label\"" not in s:
    s = s.replace("</resources>", entries + "\n</resources>")

p.write_text(s)
PY

fi

echo "strings.xml ready."
echo

echo "[7/8] Updating AndroidManifest.xml..."

python3 - <<'PY'
from pathlib import Path

p = Path("app/src/main/AndroidManifest.xml")
s = p.read_text()

receiver = r'''
        <receiver
            android:name=".security.RTKDeviceAdminReceiver"
            android:label="@string/device_admin_label"
            android:description="@string/device_admin_description"
            android:permission="android.permission.BIND_DEVICE_ADMIN"
            android:exported="true">

            <meta-data
                android:name="android.app.device_admin"
                android:resource="@xml/device_admin_policies" />

            <intent-filter>
                <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
                <action android:name="android.app.action.DEVICE_ADMIN_DISABLED" />
                <action android:name="android.app.action.PROFILE_PROVISIONING_COMPLETE" />
            </intent-filter>

        </receiver>
'''

marker = '    </application>'

if ".security.RTKDeviceAdminReceiver" not in s:
    if marker not in s:
        raise SystemExit("ERROR: application closing tag not found")

    s = s.replace(marker, receiver + "\n" + marker, 1)

p.write_text(s)
PY

echo "Manifest receiver added."
echo

echo "[8/8] Updating MainActivity.kt..."

python3 - <<'PY'
from pathlib import Path

p = Path("app/src/main/java/com/rtk/telegrammanager/MainActivity.kt")
s = p.read_text()

# ---------------------------------------------------------
# Imports
# ---------------------------------------------------------

old_import = "import com.rtk.telegrammanager.security.SecureStorage\n"

new_import = """import com.rtk.telegrammanager.security.SecureStorage
import com.rtk.telegrammanager.device.DeviceManagementManager
"""

if "DeviceManagementManager" not in s:
    if old_import not in s:
        raise SystemExit("ERROR: SecureStorage import marker not found")
    s = s.replace(old_import, new_import, 1)

# ---------------------------------------------------------
# Manager
# ---------------------------------------------------------

old = """    val storage = remember { SecureStorage(context.applicationContext) }

    var step by remember { mutableStateOf(1) }
"""

new = """    val storage = remember { SecureStorage(context.applicationContext) }
    val deviceManagement = remember {
        DeviceManagementManager(context.applicationContext)
    }

    var step by remember { mutableStateOf(1) }
"""

if "val deviceManagement = remember" not in s:
    if old not in s:
        raise SystemExit("ERROR: SetupScreen storage block not found")
    s = s.replace(old, new, 1)

# ---------------------------------------------------------
# Device Management state
# ---------------------------------------------------------

old = """    var developerKey by remember { mutableStateOf("") }
    var developerError by remember { mutableStateOf<String?>(null) }

    val permissionsToRequest =
"""

new = """    var developerKey by remember { mutableStateOf("") }
    var developerError by remember { mutableStateOf<String?>(null) }

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

    val permissionsToRequest =
"""

if "var deviceManagementStatus by remember" not in s:
    if old not in s:
        raise SystemExit("ERROR: developer state marker not found")
    s = s.replace(old, new, 1)

# ---------------------------------------------------------
# Insert Device Management card before notification access
# ---------------------------------------------------------

marker = """                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                                )
                            )
                        }
                    },
"""

card = """                Card(
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
                                text = "This device is managed by RTK Telegram Manager. The uninstall policy is controlled by Android Device Policy."
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
                                text = "Device Owner cannot be silently activated by the app. Android managed-device provisioning is required, and the user must complete the system provisioning flow."
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

""" + marker

if "🛡️ DEVICE MANAGEMENT" not in s:
    if marker not in s:
        raise SystemExit("ERROR: notification button marker not found")
    s = s.replace(marker, card, 1)

p.write_text(s)
PY

echo "MainActivity updated."
echo

echo "=============================================="
echo " STATIC AUDIT"
echo "=============================================="

echo
echo "--- New files ---"
find app/src/main/java/com/rtk/telegrammanager/device \
     app/src/main/java/com/rtk/telegrammanager/security \
     app/src/main/res/xml \
     -maxdepth 1 -type f 2>/dev/null | sort

echo
echo "--- Device management references ---"
grep -RniE \
  'RTKDeviceAdminReceiver|DeviceManagementManager|isDeviceOwner|setUninstallBlocked|DEVICE_MANAGEMENT' \
  app/src/main 2>/dev/null || true

echo
echo "--- Duplicate receiver check ---"
COUNT="$(grep -Rhc 'RTKDeviceAdminReceiver' app/src/main/AndroidManifest.xml | awk '{s+=$1} END {print s+0}')"
echo "Manifest receiver reference count: $COUNT"

if [ "$COUNT" -ne 1 ]; then
    echo "ERROR: unexpected receiver count"
    exit 1
fi

echo
echo "--- Existing SMS files untouched ---"
find app/src/main/java/com/rtk/telegrammanager/sms \
    -maxdepth 1 -type f | sort

echo
echo "=============================================="
echo " PATCH COMPLETE"
echo "=============================================="
echo
echo "Backup:"
echo "  $BACKUP"
echo
echo "IMPORTANT:"
echo "  Device Owner is NOT forced or silently activated."
echo "  Uninstall blocking activates only after Android reports"
echo "  this package as the Device Owner."
echo
echo "Next build:"
echo "  ./gradlew :app:assembleDebug --no-daemon"
echo
