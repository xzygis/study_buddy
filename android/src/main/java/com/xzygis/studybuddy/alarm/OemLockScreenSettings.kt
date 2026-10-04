package com.xzygis.studybuddy.alarm

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.util.Locale

object OemLockScreenSettings {
    enum class PermissionState { GRANTED, DENIED, UNKNOWN }

    private const val HUAWEI_POPUP_BACKGROUND_WINDOW = 100000
    private const val XIAOMI_SHOW_ON_LOCK_SCREEN = 10020
    private const val XIAOMI_START_FROM_BACKGROUND = 10021

    private val manufacturer: String
        get() = Build.MANUFACTURER.lowercase(Locale.ROOT)

    fun isRelevant(): Boolean = isRelevant(manufacturer)

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun popupPermissionState(context: Context): PermissionState = when (manufacturer) {
        "huawei" -> huaweiPopupPermissionState(context)
        "xiaomi", "redmi", "poco" -> xiaomiPopupPermissionState(context)
        else -> PermissionState.GRANTED
    }

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )

    fun popupSettingsIntent(context: Context): Intent {
        val candidates = when (manufacturer) {
            "huawei" -> popupComponentsFor(manufacturer).map {
                Intent().setComponent(it)
            }
            "xiaomi", "redmi", "poco" -> listOf(
                Intent("miui.intent.action.APP_PERM_EDITOR")
                    .setPackage("com.miui.securitycenter")
                    .putExtra("extra_pkgname", context.packageName),
            ) + popupComponentsFor(manufacturer).map {
                Intent("miui.intent.action.APP_PERM_EDITOR")
                    .setComponent(it)
                    .putExtra("extra_pkgname", context.packageName)
            }
            else -> emptyList()
        }
        return candidates.firstOrNull {
            it.resolveActivity(context.packageManager) != null
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            )
    }

    internal fun isRelevant(manufacturer: String): Boolean =
        manufacturer.lowercase(Locale.ROOT) in supportedManufacturers

    internal fun popupComponentsFor(manufacturer: String): List<ComponentName> =
        when (manufacturer.lowercase(Locale.ROOT)) {
            "huawei" -> listOf(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.securitycenter.permission.ui.activity.MainActivity",
                ),
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.permissionmanager.ui.MainActivity",
                ),
            )
            "xiaomi", "redmi", "poco" -> listOf(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity",
                ),
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
                ),
            )
            else -> emptyList()
        }

    private fun huaweiPopupPermissionState(context: Context): PermissionState =
        runCatching {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            val type = Class.forName("com.huawei.android.app.AppOpsManagerEx")
            val method = type.getDeclaredMethod(
                "checkHwOpNoThrow",
                AppOpsManager::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            )
            val instance = type.getDeclaredConstructor().newInstance()
            val mode = (method.invoke(
                instance,
                appOps,
                HUAWEI_POPUP_BACKGROUND_WINDOW,
                Process.myUid(),
                context.packageName,
            ) as Number).toInt()
            permissionState(mode)
        }.getOrDefault(PermissionState.UNKNOWN)

    private fun xiaomiPopupPermissionState(context: Context): PermissionState =
        runCatching {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            val showOnLockScreen = checkAppOp(
                appOps,
                XIAOMI_SHOW_ON_LOCK_SCREEN,
                Process.myUid(),
                context.packageName,
            )
            val startFromBackground = checkAppOp(
                appOps,
                XIAOMI_START_FROM_BACKGROUND,
                Process.myUid(),
                context.packageName,
            )
            if (
                showOnLockScreen == AppOpsManager.MODE_ALLOWED &&
                startFromBackground == AppOpsManager.MODE_ALLOWED
            ) {
                PermissionState.GRANTED
            } else {
                PermissionState.DENIED
            }
        }.getOrDefault(PermissionState.UNKNOWN)

    private fun permissionState(mode: Int): PermissionState =
        if (mode == AppOpsManager.MODE_ALLOWED) {
            PermissionState.GRANTED
        } else {
            PermissionState.DENIED
        }

    private fun checkAppOp(
        appOps: AppOpsManager,
        operation: Int,
        uid: Int,
        packageName: String,
    ): Int {
        val method = appOps.javaClass.getMethod(
            "checkOpNoThrow",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java,
        )
        return (method.invoke(appOps, operation, uid, packageName) as Number).toInt()
    }

    private val supportedManufacturers = setOf("huawei", "xiaomi", "redmi", "poco")
}
