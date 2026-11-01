package com.vaak.app.feature.onboarding

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import com.vaak.app.R

/**
 * OEMs whose battery management blocks reminders (US-033). Detected by `Build.MANUFACTURER`; the guide is shown once
 * after the reminder is scheduled and never on stock Android (S06 state 5).
 */
enum class Oem(
    @param:StringRes val lead: Int,
    @param:StringRes val step1: Int,
    @param:StringRes val step2: Int,
    private val autostart: List<ComponentName>,
) {
    XIAOMI(
        R.string.oem_lead_xiaomi, R.string.oem_xiaomi_step1, R.string.oem_xiaomi_step2,
        listOf(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
    ),
    OPPO(
        R.string.oem_lead_oppo, R.string.oem_oppo_step1, R.string.oem_oppo_step2,
        listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ),
    ),
    REALME(
        R.string.oem_lead_realme, R.string.oem_oppo_step1, R.string.oem_oppo_step2,
        listOf(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
    ),
    VIVO(
        R.string.oem_lead_vivo, R.string.oem_vivo_step1, R.string.oem_vivo_step2,
        listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        ),
    );

    /**
     * Opens the OEM autostart screen, or the app's system details page when no OEM screen starts. Tried with
     * startActivity rather than resolved first, so no `<queries>` entry is needed (package visibility).
     */
    fun openSettings(context: Context) {
        val candidates = autostart.map { Intent().setComponent(it) } +
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                continue
            } catch (_: SecurityException) {
                continue
            }
        }
    }

    companion object {
        fun fromManufacturer(manufacturer: String): Oem? = when (manufacturer.lowercase()) {
            "xiaomi", "redmi", "poco" -> XIAOMI
            "oppo" -> OPPO
            "realme" -> REALME
            "vivo", "iqoo" -> VIVO
            else -> null
        }
    }
}
