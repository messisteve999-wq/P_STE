package com.steve.pste

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Server {
    const val URL_REPLY = "http://127.0.0.1:3000/reply"
    const val RULES_DEFAUT = "Consignes : ne parle pas d'échecs sauf si la personne en parle d'abord. " +
        "Montre un vrai intérêt pour ses passe-temps et cherche à connaître sa personnalité " +
        "(une seule question à la fois, naturelle). Réponds en une ou deux phrases."

    lateinit var appCtx: Context

    // Les consignes viennent du profil modifiable ; sinon, les consignes par défaut
    fun regles(): String =
        if (::appCtx.isInitialized)
            ProfilStore.construirePrompt(appCtx) + "\nRéponds en une ou deux phrases."
        else RULES_DEFAUT

    fun ask(app: String, contact: String, hist: List<Msg>, avoid: String?, cb: (String?) -> Unit) {
        val rules = regles()
        val h = hist.takeLast(8)
        val conv = h.joinToString("\n") { (if (it.mine) "Moi" else contact) + " : " + it.text }
        val last = hist.lastOrNull { !it.mine }?.text ?: ""
        val prompt = rules + "\n" + (if (avoid != null) "Propose une réponse différente de : « $avoid ».\n" else "") +
            "Conversation avec $contact sur $app :\n$conv\nRéponds au dernier message de $contact."
        Thread {
            var r: String? = null
            try {
                val c = URL(URL_REPLY).openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.connectTimeout = 5000; c.readTimeout = 90000; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                val hj = JSONArray(); h.forEach { hj.put(JSONObject().put("from", if (it.mine) "me" else "them").put("text", it.text)) }
                val body = JSONObject().put("message", prompt).put("text", last).put("contact", contact).put("app", app)
                    .put("instructions", rules).put("history", hj).toString()
                c.outputStream.use { it.write(body.toByteArray()) }
                val s = c.inputStream.bufferedReader().readText()
                r = try { val o = JSONObject(s); o.optString("reply", o.optString("text", s)) } catch (e: Exception) { s }
            } catch (e: Exception) {}
            val out = r?.trim()?.ifEmpty { null }
            Handler(Looper.getMainLooper()).post { cb(out) }
        }.start()
    }
}
