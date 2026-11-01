package com.steve.pste

import android.content.Context

class Profil(val infos: String, val consignes: String)

object ProfilStore {
    const val INFOS_DEFAUT = "Je m'appelle Steve. Je suis entrepreneur. J'ai 28 ans. " +
        "J'aime créer et étudier de nouvelles choses."

    const val CONSIGNES_DEFAUT = "Ne parle jamais de la famille de Steve, même si on te pose la question. " +
        "Ne parle pas trop de Steve ni de sa vie : contourne les questions personnelles en ramenant " +
        "la conversation vers la personne qui écrit. " +
        "Utilise des stickers (emojis) dans tes réponses. " +
        "Ne parle pas d'échecs sauf si la personne en parle d'abord. " +
        "Montre un vrai intérêt pour ses passe-temps et cherche à connaître sa personnalité, " +
        "avec une seule question à la fois, de façon naturelle."

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("p_ste_profil", Context.MODE_PRIVATE)

    fun charger(ctx: Context): Profil {
        val s = prefs(ctx)
        return Profil(
            s.getString("infos", null) ?: INFOS_DEFAUT,
            s.getString("consignes", null) ?: CONSIGNES_DEFAUT
        )
    }

    fun sauver(ctx: Context, infos: String, consignes: String) {
        prefs(ctx).edit().putString("infos", infos.trim()).putString("consignes", consignes.trim()).apply()
    }

    fun construirePrompt(ctx: Context): String {
        val p = charger(ctx)
        return "Tu écris des réponses pour Steve dans ses messages.\n" +
            "Ce que tu sais sur Steve : ${p.infos}\n" +
            "Consignes : ${p.consignes}"
    }
}
