package com.steve.pste

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*

class SettingsActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        val p = ProfilStore.charger(this)

        val l = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (30 * d).toInt(), (20 * d).toInt(), (20 * d).toInt())
        }
        l.addView(TextView(this).apply { text = "Paramètres"; textSize = 24f })

        l.addView(TextView(this).apply {
            text = "Mes informations (ce que l'IA sait sur moi)"
            textSize = 15f
            setPadding(0, (16 * d).toInt(), 0, (6 * d).toInt())
        })
        val infos = EditText(this).apply {
            setText(p.infos); minLines = 4; gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        l.addView(infos)

        l.addView(TextView(this).apply {
            text = "Consignes de réponse de l'IA"
            textSize = 15f
            setPadding(0, (16 * d).toInt(), 0, (6 * d).toInt())
        })
        val consignes = EditText(this).apply {
            setText(p.consignes); minLines = 6; gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        l.addView(consignes)

        val status = TextView(this).apply { setPadding(0, (10 * d).toInt(), 0, 0) }

        l.addView(Button(this).apply {
            text = "Enregistrer"
            setOnClickListener {
                ProfilStore.sauver(this@SettingsActivity, infos.text.toString(), consignes.text.toString())
                status.text = "Enregistré ✓"
            }
        })
        l.addView(Button(this).apply {
            text = "Remettre les valeurs par défaut"
            setOnClickListener {
                infos.setText(ProfilStore.INFOS_DEFAUT)
                consignes.setText(ProfilStore.CONSIGNES_DEFAUT)
                status.text = "Valeurs par défaut rétablies (pensez à enregistrer)"
            }
        })
        l.addView(status)

        setContentView(ScrollView(this).apply { addView(l) })
    }
}
