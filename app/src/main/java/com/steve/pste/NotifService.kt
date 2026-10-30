package com.steve.pste

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val appName = Store.APPS[sbn.packageName] ?: return
        Store.init(applicationContext)
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        val c = Store.contacts.firstOrNull { it.app == sbn.packageName && it.name.equals(title, true) } ?: return
        n.actions?.firstOrNull { a -> a.remoteInputs?.isNotEmpty() == true }?.let { Store.actions[Store.key(c)] = it }
        Handler(Looper.getMainLooper()).post {
            if (Store.incoming(c, text)) Server.ask(appName, c.name, text) { r -> if (r != null) Store.onReply(c, r, applicationContext) }
        }
    }
}
