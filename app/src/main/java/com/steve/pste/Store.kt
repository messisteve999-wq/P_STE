package com.steve.pste

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

data class Contact(val app: String, val name: String)
data class Msg(val text: String, val mine: Boolean, val time: Long, val ok: Boolean = true)

object Store {
    val APPS = linkedMapOf("com.whatsapp" to "WhatsApp", "com.whatsapp.w4b" to "WA Business", "com.snapchat.android" to "Snapchat")
    private lateinit var sp: SharedPreferences
    val contacts = mutableListOf<Contact>()
    val msgs = HashMap<String, MutableList<Msg>>()
    val unread = HashMap<String, Int>()
    val suggestion = HashMap<String, String>()
    val loading = HashSet<String>()
    val autoSet = HashSet<String>()
    val actions = HashMap<String, Notification.Action>()
    var max = 5
    var eph = 0 // messagerie éphémère : 0 = off, sinon durée en heures
    var openKey: String? = null
    var onChange: (() -> Unit)? = null
    var onSound: (() -> Unit)? = null

    fun key(c: Contact) = c.app + "|" + c.name.lowercase()

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("p_ste", 0)
        max = sp.getInt("max", 5); eph = sp.getInt("eph", 0)
        try {
            val a = JSONArray(sp.getString("c", "[]"))
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); contacts.add(Contact(o.getString("a"), o.getString("n"))) }
            val au = JSONArray(sp.getString("as", "[]"))
            for (i in 0 until au.length()) autoSet.add(au.getString(i))
            val m = JSONObject(sp.getString("m", "{}"))
            m.keys().forEach { k ->
                val l = m.getJSONArray(k); val list = mutableListOf<Msg>()
                for (i in 0 until l.length()) { val o = l.getJSONObject(i); list.add(Msg(o.getString("t"), o.getBoolean("m"), o.getLong("d"), o.optBoolean("k", true))) }
                msgs[k] = list
            }
        } catch (e: Exception) {}
        prune()
    }

    fun save() {
        val a = JSONArray(); contacts.forEach { a.put(JSONObject().put("a", it.app).put("n", it.name)) }
        val m = JSONObject()
        msgs.forEach { (k, l) ->
            val arr = JSONArray()
            l.forEach { arr.put(JSONObject().put("t", it.text).put("m", it.mine).put("d", it.time).put("k", it.ok)) }
            m.put(k, arr)
        }
        sp.edit().putString("c", a.toString()).putString("m", m.toString()).putString("as", JSONArray(autoSet.toList()).toString())
            .putInt("max", max).putInt("eph", eph).apply()
    }

    /** Messagerie éphémère : supprime les messages plus vieux que la durée choisie. */
    fun prune() {
        if (eph <= 0) return
        val lim = System.currentTimeMillis() - eph * 3600000L
        var ch = false
        msgs.values.forEach { if (it.removeAll { m -> m.time < lim }) ch = true }
        if (ch) save()
    }

    fun incoming(c: Contact, t: String): Boolean {
        prune()
        val k = key(c); val l = msgs.getOrPut(k) { mutableListOf() }
        val now = System.currentTimeMillis(); val last = l.lastOrNull()
        if (last != null && !last.mine && last.text == t && now - last.time < 10000) return false
        l.add(Msg(t, false, now))
        if (openKey != k) unread[k] = (unread[k] ?: 0) + 1
        save(); onSound?.invoke(); onChange?.invoke()
        return true
    }

    fun send(c: Contact, t: String, ctx: Context): Boolean {
        val k = key(c); val a = actions[k]; var ok = false
        if (a != null) try {
            val ri = a.remoteInputs; val i = Intent(); val b = Bundle()
            b.putCharSequence(ri[0].resultKey, t)
            RemoteInput.addResultsToIntent(ri, i, b)
            a.actionIntent.send(ctx, 0, i); ok = true
        } catch (e: Exception) {}
        msgs.getOrPut(k) { mutableListOf() }.add(Msg(t, true, System.currentTimeMillis(), ok))
        suggestion.remove(k); save(); onChange?.invoke()
        if (!ok) Toast.makeText(ctx, "Pas envoyé sur l'appli : attendez un nouveau message de ce contact", Toast.LENGTH_LONG).show()
        return ok
    }

    fun editMsg(k: String, i: Int, t: String) {
        msgs[k]?.let { l -> if (i in l.indices) l[i] = l[i].copy(text = t) }
        save(); onChange?.invoke()
    }

    fun delMsg(k: String, i: Int) {
        msgs[k]?.let { l -> if (i in l.indices) l.removeAt(i) }
        save(); onChange?.invoke()
    }

    fun onReply(c: Contact, r: String, ctx: Context) {
        val k = key(c)
        if (autoSet.contains(k) && actions[k] != null) send(c, r, ctx) else { suggestion[k] = r; onChange?.invoke() }
    }
}
