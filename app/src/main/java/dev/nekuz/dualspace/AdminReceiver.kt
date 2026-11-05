package dev.nekuz.dualspace

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Space.setUp(context)
    }
}
