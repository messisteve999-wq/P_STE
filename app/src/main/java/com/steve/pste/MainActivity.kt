package com.steve.pste

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private lateinit var st: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Server.appCtx = applicationContext
        val d = resources.displayMetrics.density
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding((20 * d).toInt(), (40 * d).toInt(), (20 * d).toInt(), (20 * d).toInt()) }
        st = TextView(this)
        fun btn(t: String, a: () -> Unit) { l.addView(Button(this).apply { text = t; setOnClickListener { a() } }) }
        l.addView(TextView(this).apply { text = "P_STE v5"; textSize = 26f })
        l.addView(st)
        btn("1. Autoriser l'affichage par-dessus") { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        btn("2. Débloquer « paramètres restreints » (⋮ en haut à droite)") { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        btn("3. Accès aux notifications") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        btn("4. Démarrer la bulle") {
            if (!Settings.canDrawOverlays(this)) Toast.makeText(this, "Étape 1 d'abord", Toast.LENGTH_SHORT).show()
            else {
                if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
                startForegroundService(Intent(this, FloatingService::class.java))
            }
        }
        btn("Arrêter la bulle") { stopService(Intent(this, FloatingService::class.java)) }
        btn("Tester le serveur Termux") {
            Server.ask("test", "test", listOf(Msg("Salut !", false, System.currentTimeMillis())), null) { r ->
                Toast.makeText(this, r ?: "Serveur injoignable (node server.js lancé ?)", Toast.LENGTH_LONG).show()
            }
        }
        btn("Paramètres (mes infos et consignes de l'IA)") { startActivity(Intent(this, SettingsActivity::class.java)) }
        setContentView(ScrollView(this).apply { addView(l) })
    }

    override fun onResume() {
        super.onResume()
        val n = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
        st.text = "Superposition : ${if (Settings.canDrawOverlays(this)) "✓" else "✗"}\nNotifications : ${if (n) "✓" else "✗"}\n"
    }
}
