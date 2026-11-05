package dev.nekuz.dualspace

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.concurrent.thread

class CloneActivity : Activity() {

    private lateinit var title: TextView
    private lateinit var message: TextView
    private lateinit var progress: ProgressBar
    private lateinit var allowButton: Button

    private var targetPackage = ""
    private var targetLabel = ""
    private var installStarted = false
    private var receiverRegistered = false

    @Volatile
    private var sessionId = -1

    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onInstallStatus(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        setContentView(R.layout.activity_clone)
        title = findViewById(R.id.title)
        message = findViewById(R.id.message)
        progress = findViewById(R.id.progress)
        allowButton = findViewById(R.id.allowButton)

        targetPackage = intent.getStringExtra(Space.EXTRA_PACKAGE).orEmpty()
        targetLabel = intent.getStringExtra(Space.EXTRA_LABEL) ?: targetPackage
        title.text = getString(R.string.clone_progress_title, targetLabel)
        if (targetPackage.isEmpty() || !Space.isInsideSpace(this)) {
            finishWith(getString(R.string.clone_failed, targetLabel))
            return
        }

        val filter = IntentFilter(ACTION_INSTALL_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(resultReceiver, filter, Space.PERMISSION_CLONE, null, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(resultReceiver, filter, Space.PERMISSION_CLONE, null)
        }
        receiverRegistered = true

        allowButton.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            )
        }

        if (intent.getBooleanExtra(Space.EXTRA_IS_SYSTEM, false)) {
            enableSystemApp()
        } else {
            Space.allowInstalls(this)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        if (!installStarted) super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        if (installStarted || isFinishing || intent.getBooleanExtra(Space.EXTRA_IS_SYSTEM, false)) return
        if (packageManager.canRequestPackageInstalls()) {
            allowButton.visibility = View.GONE
            installStarted = true
            startCopy()
        } else {
            progress.visibility = View.GONE
            message.setText(R.string.clone_needs_permission)
            allowButton.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(resultReceiver)
        if (Space.isInsideSpace(this)) Space.restrictInstalls(this)
        super.onDestroy()
    }

    private fun enableSystemApp() {
        val done = runCatching {
            val dpm = Space.dpm(this)
            val admin = Space.admin(this)
            dpm.enableSystemApp(admin, targetPackage)
            dpm.setApplicationHidden(admin, targetPackage, false)
            true
        }.getOrDefault(false)
        finishWith(getString(if (done) R.string.clone_success else R.string.clone_failed, targetLabel))
    }

    private fun startCopy() {
        runCatching { Space.dpm(this).setApplicationHidden(Space.admin(this), targetPackage, false) }

        val paths = listOfNotNull(intent.getStringExtra(Space.EXTRA_BASE_APK)) +
            intent.getStringArrayExtra(Space.EXTRA_SPLIT_APKS).orEmpty()
        message.setText(R.string.clone_copying)
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = false
        progress.max = 100

        thread {
            val installer = packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(targetPackage)
            try {
                check(paths.isNotEmpty())
                val files = paths.map { File(it).canonicalFile }
                check(files.all(::isInstalledApk))
                val totalBytes = files.sumOf { it.length() }.coerceAtLeast(1)
                var copiedBytes = 0L
                sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    files.forEachIndexed { index, file ->
                        file.inputStream().use { input ->
                            session.openWrite("$index-${file.name}", 0, file.length()).use { output ->
                                val buffer = ByteArray(1024 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                    copiedBytes += read
                                    val percent = (copiedBytes * 100 / totalBytes).toInt()
                                    runOnUiThread { progress.progress = percent }
                                }
                                session.fsync(output)
                            }
                        }
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                    val callback = PendingIntent.getBroadcast(
                        this,
                        sessionId,
                        Intent(ACTION_INSTALL_STATUS).setPackage(packageName),
                        flags,
                    )
                    session.commit(callback.intentSender)
                }
                runOnUiThread {
                    progress.isIndeterminate = true
                    message.setText(R.string.clone_installing)
                }
            } catch (e: Exception) {
                if (sessionId != -1) runCatching { installer.abandonSession(sessionId) }
                runOnUiThread { finishWith(getString(R.string.clone_failed, targetLabel)) }
            }
        }
    }

    private fun isInstalledApk(file: File): Boolean =
        file.name.endsWith(".apk") && APK_ROOTS.any { file.path.startsWith(it) }

    private fun onInstallStatus(intent: Intent) {
        if (sessionId == -1 || intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != sessionId) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm == null) {
                    finishWith(getString(R.string.clone_failed, targetLabel))
                } else {
                    startActivity(confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> finishWith(getString(R.string.clone_success, targetLabel))
            PackageInstaller.STATUS_FAILURE_ABORTED -> finishWith(getString(R.string.clone_cancelled))
            else -> finishWith(getString(R.string.clone_failed, targetLabel))
        }
    }

    private fun finishWith(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        finish()
    }

    private companion object {
        const val ACTION_INSTALL_STATUS = "dev.nekuz.dualspace.action.INSTALL_STATUS"
        val APK_ROOTS = listOf("/data/app/", "/mnt/expand/")
    }
}
