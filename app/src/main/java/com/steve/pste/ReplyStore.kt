package com.steve.pste

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

class Conv(val pkg: String, val name: String, var lastMsg: String) {
    var suggestion: String = ""
    var error: String = ""
    var sent: String = ""
    var action: Notification.Action? = null
    var time: Long = System.currentTimeMillis()
}

object ReplyStore {
    private val map = LinkedHashMap<String, Conv>()
    private val main = Handler(Looper.getMainLooper())

    var listener: (() -> Unit)? = null
    var onMessage: (() -> Unit)? = null

    fun notifyChanged() { main.post { listener?.invoke() } }
    fun notifyMessage() { main.post { onMessage?.invoke() } }

    @Synchronized
    fun upsert(pkg: String, name: String, msg: String, action: Notification.Action?): Conv {
        val k = "$pkg|$name"
        val c = map[k] ?: Conv(pkg, name, msg).also { map[k] = it }
        c.lastMsg = msg
        c.time = System.currentTimeMillis()
        c.suggestion = ""
        c.error = ""
        c.sent = ""
        if (action != null) c.action = action
        val same = map.values.filter { it.pkg == pkg }.sortedByDescending { it.time }
        same.drop(5).forEach { map.remove("${it.pkg}|${it.name}") }
        return c
    }

    @Synchronized
    fun snapshot(): List<Conv> =
        map.values.sortedWith(compareBy<Conv>({ it.pkg }).thenByDescending { it.time })

    fun generate(ctx: Context, c: Conv): Boolean {
        return try {
            c.suggestion = Gemini.ask(
                Prefs.url(ctx),
                "Message reçu de ${c.name} sur la messagerie : ${c.lastMsg}\nÉcris la réponse de Steve."
            )
            c.error = ""
            true
        } catch (e: Exception) {
            c.error = e.message ?: "erreur"
            false
        } finally {
            notifyChanged()
        }
    }

    fun send(ctx: Context, c: Conv, text: String): Boolean {
        val act = c.action ?: return false
        val ris = act.remoteInputs ?: return false
        return try {
            val intent = Intent()
            val b = Bundle()
            for (ri in ris) b.putCharSequence(ri.resultKey, text)
            RemoteInput.addResultsToIntent(ris, intent, b)
            act.actionIntent.send(ctx, 0, intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
