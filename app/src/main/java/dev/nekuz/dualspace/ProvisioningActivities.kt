package dev.nekuz.dualspace

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle

class GetProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(
            RESULT_OK,
            Intent().putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE
            )
        )
        finish()
    }
}

class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Space.setUp(this)
        setResult(RESULT_OK)
        finish()
    }
}
