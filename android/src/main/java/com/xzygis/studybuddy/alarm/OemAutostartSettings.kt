package com.xzygis.studybuddy.alarm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.Locale

object OemAutostartSettings {
    private val manufacturer: String
        get() = Build.MANUFACTURER.lowercase(Locale.ROOT)

    fun isRelevant(): Boolean = manufacturer in supportedManufacturers

    fun bestIntent(context: Context): Intent {
        val candidates = componentsFor(manufacturer).map { component ->
            Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return candidates.firstOrNull {
            it.resolveActivity(context.packageManager) != null
        } ?: Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )
    }

    internal fun componentsFor(manufacturer: String): List<ComponentName> = when (manufacturer) {
        "xiaomi", "redmi", "poco" -> listOf(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
        )
        "huawei" -> listOf(
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            ),
        )
        "honor" -> listOf(
            ComponentName(
                "com.hihonor.systemmanager",
                "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            ),
        )
        "oppo", "realme", "oneplus" -> listOf(
            ComponentName(
                "com.oplus.safecenter",
                "com.oplus.safecenter.startupapp.StartupAppListActivity",
            ),
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity",
            ),
            ComponentName(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
            ),
        )
        "vivo", "iqoo" -> listOf(
            ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            ),
            ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
            ),
        )
        "meizu" -> listOf(
            ComponentName(
                "com.meizu.safe",
                "com.meizu.safe.permission.SmartBGActivity",
            ),
        )
        "asus" -> listOf(
            ComponentName(
                "com.asus.mobilemanager",
                "com.asus.mobilemanager.entry.FunctionActivity",
            ),
        )
        else -> emptyList()
    }

    private val supportedManufacturers = setOf(
        "xiaomi",
        "redmi",
        "poco",
        "huawei",
        "honor",
        "oppo",
        "realme",
        "oneplus",
        "vivo",
        "iqoo",
        "meizu",
        "asus",
    )
}
