package com.steve.pste

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var root: LinearLayout
    private var asked = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun add(v: android.view.View, top: Int = 8) {
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(top)
        root.addView(v, lp)
    }

    private fun tv(t: String, size: Float, color: Int = Color.WHITE): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        v.setTextColor(color)
        return v
    }

    private fun btn(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        return b
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(18), dp(24), dp(18), dp(32))

        val icon = ImageView(this)
        icon.setImageResource(R.drawable.ic_launcher)
        val ilp = LinearLayout.LayoutParams(dp(96), dp(96))
        ilp.gravity = android.view.Gravity.CENTER_HORIZONTAL
        root.addView(icon, ilp)

        val title = tv("P_STE", 26f)
        title.gravity = android.view.Gravity.CENTER
        add(title)

        status = tv("", 15f)
        add(status, 14)

        add(tv("ÉTAPE 1 — Débloquer les paramètres restreints", 14f, Color.parseColor("#93C5FD")), 18)
        add(tv("Ouvre la fiche de l'appli, puis menu ⋮ (en haut à droite) → « Autoriser les paramètres restreints ». Si ⋮ n'apparaît pas, essaie d'abord l'étape 2, puis reviens ici.", 12f, Color.LTGRAY), 4)
        add(btn("Ouvrir la fiche de l'appli") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        })

        add(tv("ÉTAPE 2 — Accès aux notifications", 14f, Color.parseColor("#93C5FD")), 18)
        add(btn("Autoriser l'accès aux notifications") { openNotifSettings() })

        add(tv("ÉTAPE 3 — Affichage par-dessus les autres applis", 14f, Color.parseColor("#93C5FD")), 18)
        add(btn("Autoriser la bulle flottante") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        })

        add(tv("MODE", 14f, Color.parseColor("#93C5FD")), 18)
        val rg = RadioGroup(this)
        val modes = listOf(
            "suggest" to "Suggestions (je valide)",
            "auto" to "Réponse automatique (contacts cochés « Auto »)",
            "off" to "Désactivé"
        )
        modes.forEachIndexed { i, pair ->
            val rb = RadioButton(this)
            rb.id = 100 + i
            rb.text = pair.second
            rb.setTextColor(Color.WHITE)
            rg.addView(rb)
            if (Prefs.mode(this) == pair.first) rb.isChecked = true
        }
        rg.setOnCheckedChangeListener { _, id ->
            Prefs.setMode(this@MainActivity, modes[id - 100].first)
        }
        add(rg, 4)

        add(tv("SERVEUR GEMINI (Termux)", 14f, Color.parseColor("#93C5FD")), 18)
        val urlEdit = EditText(this)
        urlEdit.setText(Prefs.url(this))
        urlEdit.setTextColor(Color.WHITE)
        add(urlEdit, 4)
        result = tv("", 13f, Color.LTGRAY)
        add(btn("Tester le serveur") {
            Prefs.setUrl(this@MainActivity, urlEdit.text.toString().trim())
            result.text = "Test en cours…"
            Thread {
                val r = try {
                    Gemini.ask(Prefs.url(this@MainActivity), "Salut, tu fais quoi ce soir ?")
                } catch (e: Exception) {
                    "Erreur : " + e.message
                }
                runOnUiThread { result.text = r }
            }.start()
        })
        add(result, 6)

        add(tv("BULLE", 14f, Color.parseColor("#93C5FD")), 18)
        add(btn("Démarrer la bulle") {
            if (!Settings.canDrawOverlays(this@MainActivity)) {
                Toast.makeText(this@MainActivity, "Autorise d'abord l'étape 3", Toast.LENGTH_LONG).show()
            } else {
                ContextCompat.startForegroundService(
                    this@MainActivity, Intent(this@MainActivity, BubbleService::class.java)
                )
            }
        })
        add(btn("Arrêter la bulle") {
            stopService(Intent(this@MainActivity, BubbleService::class.java))
        })

        val sv = ScrollView(this)
        sv.setBackgroundColor(Color.parseColor("#0B0F1A"))
        sv.addView(root)
        setContentView(sv)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun openNotifSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    override fun onResume() {
        super.onResume()
        val notifOk = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val overlayOk = Settings.canDrawOverlays(this)
        status.text = "Accès aux notifications : " + (if (notifOk) "✅" else "❌") +
            "\nBulle (affichage) : " + (if (overlayOk) "✅" else "❌")
        if (!notifOk && !asked) {
            asked = true
            AlertDialog.Builder(this)
                .setTitle("Autorisation requise")
                .setMessage("P_STE doit lire les notifications de WhatsApp et WhatsApp Business pour proposer ou envoyer des réponses. Active l'accès pour P_STE dans l'écran qui va s'ouvrir.")
                .setPositiveButton("Autoriser") { _, _ -> openNotifSettings() }
                .setNegativeButton("Plus tard", null)
                .show()
        }
    }
}
