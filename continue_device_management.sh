#!/usr/bin/env bash
set -euo pipefail

PROJECT="/root/RTKTelegramManager"
cd "$PROJECT"

echo "=============================================="
echo " RTK DEVICE MANAGEMENT - CONTINUATION PATCH"
echo "=============================================="

# -------------------------------------------------
# 1. Safety checks
# -------------------------------------------------

echo
echo "[1/6] Safety checks..."

test -f app/src/main/AndroidManifest.xml
test -f app/src/main/java/com/rtk/telegrammanager/MainActivity.kt
test -f app/src/main/java/com/rtk/telegrammanager/security/RTKDeviceAdminReceiver.kt
test -f app/src/main/java/com/rtk/telegrammanager/device/DeviceManagementManager.kt
test -f app/src/main/res/xml/device_admin_policies.xml

echo "Required files: OK"

# -------------------------------------------------
# 2. Manifest
# -------------------------------------------------

echo
echo "[2/6] Updating AndroidManifest.xml..."

python3 - <<'PY'
from pathlib import Path

p = Path("app/src/main/AndroidManifest.xml")
s = p.read_text()

if ".security.RTKDeviceAdminReceiver" in s:
    print("DeviceAdmin receiver already present. No duplicate added.")
else:
    receiver = '''
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

    marker = "</application>"

    if marker not in s:
        raise SystemExit("ERROR: </application> not found")

    s = s.replace(
        marker,
        receiver + "\n    " + marker,
        1
    )

    p.write_text(s)
    print("DeviceAdmin receiver added.")

# Validate count.
count = s.count(".security.RTKDeviceAdminReceiver")

if count != 1:
    raise SystemExit(
        f"ERROR: Expected exactly 1 DeviceAdmin receiver, found {count}"
    )

print("Manifest receiver count: 1")
PY

# -------------------------------------------------
# 3. strings.xml
# -------------------------------------------------

echo
echo "[3/6] Checking strings.xml..."

if [ ! -f app/src/main/res/values/strings.xml ]; then

cat > app/src/main/res/values/strings.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <string name="device_admin_label">RTK Telegram Manager Device Management</string>

    <string name="device_admin_description">RTK Telegram Manager device management component for legitimately managed devices. Device management is enabled only through Android managed-device provisioning.</string>

</resources>
EOF

    echo "strings.xml created."

else

python3 - <<'PY'
from pathlib import Path

p = Path("app/src/main/res/values/strings.xml")
s = p.read_text()

if 'name="device_admin_label"' not in s:
    entry = '    <string name="device_admin_label">RTK Telegram Manager Device Management</string>\n'
    s = s.replace("</resources>", entry + "</resources>")

if 'name="device_admin_description"' not in s:
    entry = '    <string name="device_admin_description">RTK Telegram Manager device management component for legitimately managed devices. Device management is enabled only through Android managed-device provisioning.</string>\n'
    s = s.replace("</resources>", entry + "</resources>")

p.write_text(s)
PY

    echo "strings.xml updated."
fi

# -------------------------------------------------
# 4. MainActivity
# -------------------------------------------------

echo
echo "[4/6] Updating MainActivity.kt..."

python3 - <<'PY'
from pathlib import Path

p = Path("app/src/main/java/com/rtk/telegrammanager/MainActivity.kt")
s = p.read_text()

# Import
if "import com.rtk.telegrammanager.device.DeviceManagementManager" not in s:

    marker = "import com.rtk.telegrammanager.security.SecureStorage"

    if marker not in s:
        raise SystemExit(
            "ERROR: SecureStorage import marker not found"
        )

    s = s.replace(
        marker,
        marker + "\nimport com.rtk.telegrammanager.device.DeviceManagementManager",
        1
    )

# Manager
if "val deviceManagement = remember" not in s:

    marker = """    val storage = remember { SecureStorage(context.applicationContext) }

    var step by remember { mutableStateOf(1) }
"""

    replacement = """    val storage = remember { SecureStorage(context.applicationContext) }

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
"""

    if marker not in s:
        raise SystemExit(
            "ERROR: SetupScreen storage marker not found"
        )

    s = s.replace(marker, replacement, 1)

# Card
if "🛡️ DEVICE MANAGEMENT" not in s:

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

""" + marker

    if marker not in s:
        raise SystemExit(
            "ERROR: Notification access button marker not found"
        )

    s = s.replace(marker, card, 1)

p.write_text(s)

print("MainActivity device-management integration complete.")
PY

# -------------------------------------------------
# 5. Audit
# -------------------------------------------------

echo
echo "[5/6] Running audit..."

echo
echo "--- DEVICE MANAGEMENT FILES ---"

find \
  app/src/main/java/com/rtk/telegrammanager/device \
  app/src/main/java/com/rtk/telegrammanager/security \
  app/src/main/res/xml \
  -maxdepth 1 \
  -type f \
  | sort

echo
echo "--- MANIFEST RECEIVER ---"

grep -n -A18 -B2 \
  "RTKDeviceAdminReceiver" \
  app/src/main/AndroidManifest.xml

echo
echo "--- MAINACTIVITY REFERENCES ---"

grep -nE \
  "DeviceManagementManager|deviceManagementStatus|DEVICE MANAGEMENT|refreshDeviceManagementStatus" \
  app/src/main/java/com/rtk/telegrammanager/MainActivity.kt

echo
echo "--- DUPLICATE CHECK ---"

MANIFEST_COUNT="$(
    grep -c \
    'android:name=".security.RTKDeviceAdminReceiver"' \
    app/src/main/AndroidManifest.xml || true
)"

echo "Manifest DeviceAdminReceiver count: $MANIFEST_COUNT"

if [ "$MANIFEST_COUNT" -ne 1 ]; then
    echo "ERROR: Duplicate DeviceAdminReceiver detected."
    exit 1
fi

JAVA_COUNT="$(
    find app/src/main/java/com/rtk/telegrammanager \
      -name 'RTKDeviceAdminReceiver.kt' \
      -type f | wc -l
)"

echo "Receiver Kotlin file count: $JAVA_COUNT"

if [ "$JAVA_COUNT" -ne 1 ]; then
    echo "ERROR: Duplicate receiver Kotlin file detected."
    exit 1
fi

echo
echo "--- SMS FILE CHECK ---"

find app/src/main/java/com/rtk/telegrammanager/sms \
    -maxdepth 1 \
    -type f \
    | sort

echo
echo "AUDIT: PASS"

# -------------------------------------------------
# 6. Build
# -------------------------------------------------

echo
echo "[6/6] Building debug APK..."

./gradlew :app:assembleDebug --no-daemon

echo
echo "=============================================="
echo " DEVICE MANAGEMENT PATCH: COMPLETE"
echo " BUILD: SUCCESS"
echo "=============================================="

echo
echo "APK:"
ls -lh app/build/outputs/apk/debug/app-debug.apk
