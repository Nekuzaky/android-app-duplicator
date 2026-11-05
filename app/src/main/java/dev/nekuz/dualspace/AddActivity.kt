package dev.nekuz.dualspace

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class AddActivity : Activity() {

    private class App(
        val label: String,
        val packageName: String,
        val icon: Drawable,
        val baseApk: String,
        val splitApks: Array<String>,
        val isSystem: Boolean,
    )

    private lateinit var list: ListView
    private lateinit var empty: TextView
    private lateinit var progress: ProgressBar
    private lateinit var search: EditText

    private var allApps: List<App> = emptyList()
    private var visibleApps: List<App> = emptyList()
    private var loaded = false
    private var loadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        progress = findViewById(R.id.progress)
        search = findViewById(R.id.search)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        list.adapter = AppAdapter()
        list.setOnItemClickListener { _, _, position, _ -> confirmClone(visibleApps[position]) }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = applyFilter()
        })
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val generation = ++loadGeneration
        if (!loaded) progress.visibility = View.VISIBLE
        thread {
            val apps = queryApps()
            runOnUiThread {
                if (isDestroyed || generation != loadGeneration) return@runOnUiThread
                allApps = apps
                val firstLoad = !loaded
                loaded = true
                progress.visibility = View.GONE
                applyFilter()
                if (firstLoad) list.scheduleLayoutAnimation()
            }
        }
    }

    private fun queryApps(): List<App> {
        val launcher = getSystemService(LauncherApps::class.java)
        val alreadyCloned = Space.workProfile(this)
            ?.let { launcher.getActivityList(null, it) }
            ?.map { it.applicationInfo.packageName }
            ?.toSet()
            .orEmpty()

        return launcher.getActivityList(null, Process.myUserHandle())
            .filter {
                it.applicationInfo.packageName != packageName &&
                    it.applicationInfo.packageName !in alreadyCloned
            }
            .distinctBy { it.applicationInfo.packageName }
            .map {
                val info = it.applicationInfo
                App(
                    label = info.loadLabel(packageManager).toString(),
                    packageName = info.packageName,
                    icon = info.loadIcon(packageManager),
                    baseApk = info.sourceDir,
                    splitApks = info.splitSourceDirs?.map { dir -> dir }?.toTypedArray() ?: emptyArray(),
                    isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun applyFilter() {
        val query = search.text.toString().trim()
        visibleApps = allApps.filter {
            query.isEmpty() ||
                it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
        (list.adapter as BaseAdapter).notifyDataSetChanged()
        empty.visibility = if (loaded && visibleApps.isEmpty()) View.VISIBLE else View.GONE
        empty.setText(if (query.isEmpty()) R.string.empty_add_list else R.string.empty_search)
    }

    private fun confirmClone(app: App) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.clone_title, app.label))
            .setMessage(R.string.clone_message)
            .setPositiveButton(R.string.clone_action) { _, _ -> clone(app) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clone(app: App) {
        val intent = Intent(Space.ACTION_CLONE)
            .putExtra(Space.EXTRA_PACKAGE, app.packageName)
            .putExtra(Space.EXTRA_LABEL, app.label)
            .putExtra(Space.EXTRA_BASE_APK, app.baseApk)
            .putExtra(Space.EXTRA_SPLIT_APKS, app.splitApks)
            .putExtra(Space.EXTRA_IS_SYSTEM, app.isSystem)

        val forwarder = packageManager.queryIntentActivities(intent, 0)
            .firstOrNull { it.activityInfo.packageName == SYSTEM_PACKAGE }
        if (forwarder == null) {
            prepareSpace()
            return
        }
        intent.setClassName(forwarder.activityInfo.packageName, forwarder.activityInfo.name)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.clone_failed, app.label), Toast.LENGTH_LONG).show()
        }
    }

    private fun prepareSpace() {
        val profile = Space.workProfile(this) ?: return
        Toast.makeText(this, R.string.space_preparing, Toast.LENGTH_LONG).show()
        runCatching {
            getSystemService(LauncherApps::class.java).startMainActivity(
                ComponentName(packageName, MainActivity::class.java.name), profile, null, null
            )
        }
    }

    private companion object {
        const val SYSTEM_PACKAGE = "android"
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = visibleApps.size
        override fun getItem(position: Int) = visibleApps[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.row_app, parent, false)
            val app = visibleApps[position]
            view.findViewById<ImageView>(R.id.icon).setImageDrawable(app.icon)
            view.findViewById<TextView>(R.id.label).text = app.label
            view.findViewById<TextView>(R.id.packageName).text = app.packageName
            view.findViewById<ImageView>(R.id.action).setImageResource(R.drawable.ic_plus_soft)
            return view
        }
    }
}
