package dev.nekuz.dualspace

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore

object Space {

    const val ACTION_CLONE = "dev.nekuz.dualspace.action.CLONE"
    const val PERMISSION_CLONE = "dev.nekuz.dualspace.permission.CLONE"
    const val EXTRA_PACKAGE = "dev.nekuz.dualspace.extra.PACKAGE"
    const val EXTRA_LABEL = "dev.nekuz.dualspace.extra.LABEL"
    const val EXTRA_BASE_APK = "dev.nekuz.dualspace.extra.BASE_APK"
    const val EXTRA_SPLIT_APKS = "dev.nekuz.dualspace.extra.SPLIT_APKS"
    const val EXTRA_IS_SYSTEM = "dev.nekuz.dualspace.extra.IS_SYSTEM"

    fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)

    fun admin(context: Context) = ComponentName(context, AdminReceiver::class.java)

    fun isInsideSpace(context: Context): Boolean =
        dpm(context).isProfileOwnerApp(context.packageName)

    fun workProfile(context: Context): UserHandle? =
        context.getSystemService(UserManager::class.java).userProfiles
            .firstOrNull { it != Process.myUserHandle() }

    fun allowInstalls(context: Context) {
        dpm(context).clearUserRestriction(admin(context), UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
    }

    fun restrictInstalls(context: Context) {
        dpm(context).addUserRestriction(admin(context), UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
    }

    fun setUp(context: Context) {
        if (!isInsideSpace(context)) return
        val dpm = dpm(context)
        val admin = admin(context)
        dpm.setProfileName(admin, context.getString(R.string.space_name))
        dpm.setProfileEnabled(admin)
        restrictInstalls(context)

        dpm.clearCrossProfileIntentFilters(admin)
        dpm.addCrossProfileIntentFilter(
            admin,
            IntentFilter(ACTION_CLONE),
            DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT,
        )

        val browser = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val camera = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        for (intent in listOf(browser, camera)) {
            runCatching { dpm.enableSystemApp(admin, intent) }
        }
        runCatching { dpm.enableSystemApp(admin, "com.android.vending") }
    }
}
