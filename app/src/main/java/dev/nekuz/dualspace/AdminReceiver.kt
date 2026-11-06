package dev.nekuz.dualspace

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Space.restoreInstallRestriction(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Space.setUp(context)
    }
}
