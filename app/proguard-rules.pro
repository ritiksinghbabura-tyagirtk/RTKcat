# ============================================================
# RTK Telegram Manager - Release R8 Rules
# ============================================================

# Keep Android components that are instantiated by the framework.
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep Parcelable implementations.
-keep class * implements android.os.Parcelable {
    public static ** CREATOR;
}

# Keep enums.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep RuntimeVisibleAnnotations / signatures where libraries need them.
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Do NOT add broad "-keep class com.rtk.**".
# R8 should be allowed to shrink and obfuscate application code.
