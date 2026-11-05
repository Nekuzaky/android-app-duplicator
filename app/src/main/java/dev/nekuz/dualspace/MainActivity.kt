package dev.nekuz.dualspace

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/**
 * Le même APK tourne dans deux contextes :
 *  - profil principal : propose de créer l'espace 2 (profil de travail) ;
 *  - profil de travail (nous en sommes "profile owner") : propose de cloner des apps dedans.
 */
class MainActivity : Activity() {

    private val dpm by lazy { getSystemService(DevicePolicyManager::class.java) }
    private val admin by lazy { ComponentName(this, AdminReceiver::class.java) }

    override fun onResume() {
        super.onResume()
        if (dpm.isProfileOwnerApp(packageName)) showProfileScreen() else showMainScreen()
    }

    // ---------- Écran du profil principal ----------

    private fun showMainScreen() {
        val root = column()
        root.addView(title("DualSpace"))
        val hasProfile = getSystemService(UserManager::class.java).userProfiles.size > 1

        if (hasProfile) {
            root.addView(text(
                "L'espace 2 existe déjà.\n\n" +
                "Ouvre l'icône DualSpace avec la petite mallette (dans ton tiroir d'apps) " +
                "pour choisir les applications à dupliquer."
            ))
        } else {
            root.addView(text(
                "Crée un second espace isolé sur ton téléphone. Les applications que tu y " +
                "dupliques ont leur propre compte et leurs propres données.\n\n" +
                "Tout reste sur ton téléphone : cette app n'a aucun accès Internet."
            ))
            root.addView(button("Créer l'espace 2") { startProvisioning() })
        }
        setContentView(root)
    }

    private fun startProvisioning() {
        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, admin)
        }
        if (Build.VERSION.SDK_INT >= 31 &&
            !dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
        ) {
            Toast.makeText(this, "Ce téléphone refuse la création d'un profil de travail.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_PROVISION)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Profil de travail non supporté sur ce téléphone.", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PROVISION && resultCode != RESULT_OK) {
            Toast.makeText(this, "Création annulée.", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Écran du profil de travail ----------

    private fun showProfileScreen() {
        val pm = packageManager
        @Suppress("DEPRECATION")
        val candidates = pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .filter {
                it.flags and ApplicationInfo.FLAG_SYSTEM == 0 &&
                    it.flags and ApplicationInfo.FLAG_INSTALLED == 0 &&
                    it.packageName != packageName
            }
            .map { Pair(pm.getApplicationLabel(it).toString(), it.packageName) }
            .sortedBy { it.first.lowercase() }

        val root = column()
        root.addView(title("Espace 2"))
        root.addView(text("Touche une application pour la dupliquer ici."))

        val list = ListView(this)
        list.adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_2, android.R.id.text1,
            candidates.map { "${it.first}\n${it.second}" }
        )
        list.setOnItemClickListener { _, _, pos, _ -> confirmClone(candidates[pos]) }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(button("Supprimer l'espace 2 et ses données") { confirmWipe() })
        setContentView(root)
    }

    private fun confirmClone(app: Pair<String, String>) {
        AlertDialog.Builder(this)
            .setTitle("Dupliquer ${app.first} ?")
            .setMessage("Une seconde copie sera installée dans l'espace 2, avec ses propres données.")
            .setPositiveButton("Dupliquer") { _, _ ->
                val ok = try {
                    dpm.installExistingPackage(admin, app.second)
                } catch (e: Exception) {
                    false
                }
                Toast.makeText(
                    this,
                    if (ok) "${app.first} dupliquée dans l'espace 2." else "Échec de la duplication.",
                    Toast.LENGTH_LONG
                ).show()
                if (ok) showProfileScreen()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun confirmWipe() {
        AlertDialog.Builder(this)
            .setTitle("Supprimer l'espace 2 ?")
            .setMessage("Toutes les apps dupliquées et leurs données seront définitivement supprimées.")
            .setPositiveButton("Supprimer") { _, _ -> dpm.wipeData(0) }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // ---------- Petits utilitaires d'interface ----------

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(48, 48, 48, 48)
    }

    private fun title(s: String) = TextView(this).apply {
        text = s
        textSize = 28f
        setPadding(0, 0, 0, 24)
    }

    private fun text(s: String) = TextView(this).apply {
        text = s
        textSize = 16f
        setPadding(0, 0, 0, 32)
    }

    private fun button(s: String, onClick: () -> Unit) = Button(this).apply {
        text = s
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }

    private companion object {
        const val REQ_PROVISION = 1
    }
}
