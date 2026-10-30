package com.steve.pste

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("pste", Context.MODE_PRIVATE)

    fun mode(c: Context): String = sp(c).getString("mode", "suggest") ?: "suggest"
    fun setMode(c: Context, m: String) { sp(c).edit().putString("mode", m).apply() }

    const val DEFAULT_URL = "http://127.0.0.1:3000/reply"
    fun url(c: Context): String = sp(c).getString("url", DEFAULT_URL) ?: DEFAULT_URL
    fun setUrl(c: Context, u: String) { sp(c).edit().putString("url", u).apply() }

    fun autoContacts(c: Context): Set<String> = sp(c).getStringSet("auto", emptySet()) ?: emptySet()
    fun setAuto(c: Context, name: String, on: Boolean) {
        val s = HashSet(autoContacts(c))
        if (on) s.add(name) else s.remove(name)
        sp(c).edit().putStringSet("auto", s).apply()
    }
}
