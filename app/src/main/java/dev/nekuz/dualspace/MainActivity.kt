package dev.nekuz.dualspace

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private class Clone(val info: LauncherActivityInfo, val label: String, val icon: android.graphics.drawable.Drawable)

    private lateinit var heroTitle: TextView
    private lateinit var gridCard: View
    private lateinit var subtitle: TextView
    private lateinit var setupCard: LinearLayout
    private lateinit var notice: TextView
    private lateinit var grid: GridView
    private lateinit var emptyClones: TextView
    private lateinit var spacer: View
    private lateinit var addButton: Button
    private lateinit var manageButton: Button

    private var clones: List<Clone> = emptyList()
    private var loadGeneration = 0
    private var animated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Space.isInsideSpace(this)) {
            Space.restoreInstallRestriction(this)
            startActivity(Intent(this, ManageActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_home)
        findViewById<View>(R.id.heroCard).clipToOutline = true
        heroTitle = findViewById(R.id.heroTitle)
        gridCard = findViewById(R.id.gridCard)
        subtitle = findViewById(R.id.subtitle)
        setupCard = findViewById(R.id.setupCard)
        notice = findViewById(R.id.notice)
        grid = findViewById(R.id.grid)
        emptyClones = findViewById(R.id.emptyClones)
        spacer = findViewById(R.id.spacer)
        addButton = findViewById(R.id.addButton)

        grid.adapter = CloneAdapter()
        grid.setOnItemClickListener { _, _, position, _ -> openClone(clones[position]) }
        findViewById<Button>(R.id.createButton).setOnClickListener { startProvisioning() }
        findViewById<View>(R.id.privacyButton).setOnClickListener { showPrivacyDialog() }
        manageButton = findViewById(R.id.manageButton)
        addButton.setOnClickListener { startActivity(Intent(this, AddActivity::class.java)) }
        manageButton.setOnClickListener { openManageScreen() }
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing) render()
    }

    private fun render() {
        val profile = Space.workProfile(this)
        val launcher = getSystemService(LauncherApps::class.java)
        when {
            profile == null -> showState(State.SETUP)
            getSystemService(UserManager::class.java).isQuietModeEnabled(profile) -> showState(State.PAUSED)
            launcher.getActivityList(packageName, profile).isEmpty() -> showState(State.FOREIGN)
            else -> {
                showState(State.READY)
                loadClones(profile, launcher)
            }
        }
    }

    private enum class State { SETUP, PAUSED, FOREIGN, READY }

    private fun showState(state: State) {
        if (state == State.SETUP) {
            heroTitle.setText(R.string.hero_setup_title)
            subtitle.setText(R.string.home_subtitle_setup)
        } else if (state != State.READY) {
            heroTitle.setText(R.string.space_name)
            subtitle.text = null
        }
        setupCard.visibility = if (state == State.SETUP) View.VISIBLE else View.GONE
        notice.visibility = if (state == State.PAUSED || state == State.FOREIGN) View.VISIBLE else View.GONE
        if (state == State.PAUSED) notice.setText(R.string.paused_message)
        if (state == State.FOREIGN) notice.setText(R.string.foreign_profile)
        addButton.visibility = if (state == State.READY) View.VISIBLE else View.GONE
        manageButton.visibility = addButton.visibility
        gridCard.visibility = if (state == State.READY) View.VISIBLE else View.GONE
        spacer.visibility = if (state == State.READY) View.GONE else View.VISIBLE
        emptyClones.visibility = View.GONE
    }

    private fun loadClones(profile: UserHandle, launcher: LauncherApps) {
        val generation = ++loadGeneration
        thread {
            val loaded = launcher.getActivityList(null, profile)
                .filter { it.applicationInfo.packageName != packageName }
                .mapNotNull { info ->
                    try {
                        Clone(
                            info,
                            safeLabel(info.applicationInfo.packageName) { info.label },
                            AppIcons.load(this) { info.getBadgedIcon(0) },
                        )
                    } catch (t: Throwable) {
                        null
                    }
                }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                if (isDestroyed || generation != loadGeneration) return@runOnUiThread
                clones = loaded
                heroTitle.text = loaded.size.toString()
                subtitle.text = resources.getQuantityText(R.plurals.space_app_count, loaded.size)
                (grid.adapter as BaseAdapter).notifyDataSetChanged()
                if (!animated) {
                    animated = true
                    grid.scheduleLayoutAnimation()
                }
                grid.visibility = if (loaded.isEmpty()) View.GONE else View.VISIBLE
                emptyClones.visibility = if (loaded.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun openClone(clone: Clone) {
        try {
            getSystemService(LauncherApps::class.java)
                .startMainActivity(clone.info.componentName, clone.info.user, null, null)
        } catch (e: RuntimeException) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun openManageScreen() {
        val profile = Space.workProfile(this) ?: return
        try {
            getSystemService(LauncherApps::class.java).startMainActivity(
                ComponentName(packageName, MainActivity::class.java.name), profile, null, null
            )
        } catch (e: RuntimeException) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun startProvisioning() {
        val action = DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE
        if (Build.VERSION.SDK_INT >= 31 && !Space.dpm(this).isProvisioningAllowed(action)) {
            Toast.makeText(this, R.string.provisioning_refused, Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(action).putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, Space.admin(this)
        )
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_PROVISION)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.provisioning_unsupported, Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PROVISION && resultCode != RESULT_OK) {
            Toast.makeText(this, R.string.provisioning_cancelled, Toast.LENGTH_SHORT).show()
        }
    }

    private inner class CloneAdapter : BaseAdapter() {
        override fun getCount() = clones.size
        override fun getItem(position: Int) = clones[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.grid_app, parent, false)
            view.findViewById<ImageView>(R.id.icon).setImageDrawable(clones[position].icon)
            view.findViewById<TextView>(R.id.label).text = clones[position].label
            return view
        }
    }

    private companion object {
        const val REQ_PROVISION = 1
    }
}
