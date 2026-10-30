package com.steve.pste

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

class NotifService : NotificationListenerService() {
    private val seen = ConcurrentHashMap<String, String>()
    private val sentTexts = ConcurrentHashMap<String, String>()
    private val lastAuto = ConcurrentHashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b" && pkg != "com.snapchat.android") return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val mode = Prefs.mode(this)
        if (mode == "off") return

        val extras = n.extras
        if (extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)) return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        if (title == "WhatsApp" || title == "WhatsApp Business" || title == "Snapchat") return
        if (text.contains("nouveaux messages") || text.contains("new messages") ||
            text.contains("Vérification")) return

        val key = "$pkg|$title"
        if (seen[key] == text) return
        seen[key] = text
        val mine = sentTexts[key]
        if (mine != null && text.contains(mine)) return

        val action = n.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
        val conv = ReplyStore.upsert(pkg, title, text, action)
        ReplyStore.notifyChanged()
        ReplyStore.notifyMessage()

        val auto = mode == "auto" && Prefs.autoContacts(this).contains(title) && action != null
        val now = System.currentTimeMillis()
        val cooldown = now - (lastAuto[key] ?: 0L) < 20000

        Thread {
            if (auto && !cooldown) {
                Thread.sleep((2000L..8000L).random())
            }
            val ok = ReplyStore.generate(this, conv)
            if (auto && !cooldown && ok && conv.suggestion.isNotBlank()) {
                if (ReplyStore.send(this, conv, conv.suggestion)) {
                    sentTexts[key] = conv.suggestion
                    lastAuto[key] = System.currentTimeMillis()
                    conv.sent = conv.suggestion
                }
            }
            ReplyStore.notifyChanged()
        }.start()
    }
}
