package dev.nekuz.dualspace

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class ManageActivity : Activity() {

    private class App(
        val label: String,
        val packageName: String,
        val icon: Drawable,
        val paused: Boolean,
        val isSystem: Boolean,
    )

    private lateinit var list: ListView
    private lateinit var empty: TextView
    private lateinit var progress: ProgressBar

    private var apps: List<App> = emptyList()
    private var loaded = false
    private var loadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Space.isInsideSpace(this)) {
            finish()
            return
        }
        setContentView(R.layout.activity_manage)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        progress = findViewById(R.id.progress)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        list.adapter = AppAdapter()
        list.setOnItemClickListener { _, _, position, _ -> showCloneActions(apps[position]) }
        findViewById<View>(R.id.privacyButton).setOnClickListener { showPrivacyDialog() }
        val wipeButton = findViewById<Button>(R.id.wipeButton)
        wipeButton.filterTouchesWhenObscured = true
        wipeButton.setOnClickListener { confirmWipe() }

        thread { Space.setUp(applicationContext) }
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing) reload()
    }

    private fun reload() {
        val generation = ++loadGeneration
        if (!loaded) progress.visibility = View.VISIBLE
        thread {
            val result = queryApps()
            runOnUiThread {
                if (isDestroyed || generation != loadGeneration) return@runOnUiThread
                apps = result
                val firstLoad = !loaded
                loaded = true
                progress.visibility = View.GONE
                (list.adapter as BaseAdapter).notifyDataSetChanged()
                if (firstLoad) list.scheduleLayoutAnimation()
                empty.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun queryApps(): List<App> {
        val pm = packageManager
        val dpm = Space.dpm(this)
        val admin = Space.admin(this)
        return pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .mapNotNull { info ->
                try {
                    val isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    val isInstalled = info.flags and ApplicationInfo.FLAG_INSTALLED != 0
                    val paused = dpm.isApplicationHidden(admin, info.packageName)
                    val listed = info.packageName != packageName && (
                        (!isSystem && isInstalled) ||
                            paused ||
                            pm.getLaunchIntentForPackage(info.packageName) != null
                        )
                    if (!listed) {
                        null
                    } else {
                        App(
                            label = safeLabel(info.packageName) { pm.getApplicationLabel(info) },
                            packageName = info.packageName,
                            icon = AppIcons.load(this) { pm.getApplicationIcon(info) },
                            paused = paused,
                            isSystem = isSystem,
                        )
                    }
                } catch (t: Throwable) {
                    null
                }
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun showCloneActions(app: App) {
        val togglePause = getString(if (app.paused) R.string.action_resume else R.string.action_pause)
        val open = getString(R.string.action_open)
        val remove = getString(R.string.action_remove)
        val actions = listOfNotNull(
            open.takeUnless { app.paused },
            togglePause,
            remove.takeUnless { app.isSystem },
        ).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(actions) { _, which ->
                when (actions[which]) {
                    open -> openClone(app)
                    togglePause -> setPaused(app, !app.paused)
                    else -> removeClone(app)
                }
            }
            .show()
    }

    private fun openClone(app: App) {
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show()
        } else {
            startActivity(intent)
        }
    }

    private fun setPaused(app: App, paused: Boolean) {
        val done = runCatching {
            Space.dpm(this).setApplicationHidden(Space.admin(this), app.packageName, paused)
        }.getOrDefault(false)
        if (!done) Toast.makeText(this, R.string.action_failed, Toast.LENGTH_LONG).show()
        reload()
    }

    private fun removeClone(app: App) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, R.string.action_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmWipe() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.wipe_title)
            .setMessage(R.string.wipe_message)
            .setPositiveButton(R.string.wipe_action) { _, _ -> Space.dpm(this).wipeData(0) }
            .setNegativeButton(R.string.cancel, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).filterTouchesWhenObscured = true
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.row_app, parent, false)
            val app = apps[position]
            view.findViewById<ImageView>(R.id.icon).setImageDrawable(app.icon)
            view.findViewById<TextView>(R.id.label).text = app.label
            view.findViewById<TextView>(R.id.packageName).text = app.packageName
            view.findViewById<TextView>(R.id.tag).visibility = if (app.paused) View.VISIBLE else View.GONE
            return view
        }
    }
}
