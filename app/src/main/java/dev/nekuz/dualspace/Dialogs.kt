package dev.nekuz.dualspace

import android.app.Activity
import android.app.AlertDialog

fun Activity.showPrivacyDialog() {
    AlertDialog.Builder(this)
        .setTitle(R.string.privacy)
        .setMessage(R.string.privacy_message)
        .setPositiveButton(R.string.close, null)
        .setIcon(R.drawable.ic_shield)
        .show()
}
