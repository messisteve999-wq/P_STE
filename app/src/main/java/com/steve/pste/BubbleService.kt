package com.steve.pste

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class BubbleService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: ImageView? = null
    private var panel: LinearLayout? = null
    private var list: LinearLayout? = null
    private var shown = false
    private var editing = false
    private val ui = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startFg()
        addBubble()
        ReplyStore.listener = { refresh() }
        ReplyStore.onMessage = { ding() }
    }

    private fun startFg() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("pste", "P_STE", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "pste")
            .setContentTitle("P_STE actif")
            .setContentText("Touchez la bulle pour ouvrir la fenêtre IA")
            .setSmallIcon(R.drawable.ic_bubble)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun addBubble() {
        val size = dp(56)
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(8)
        lp.y = dp(200)
        val v = ImageView(this)
        v.setImageResource(R.drawable.ic_bubble)
        var sx = 0
        var sy = 0
        var tx = 0f
        var ty = 0f
        var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt()
                    val dy = (e.rawY - ty).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    lp.x = sx + dx
                    lp.y = sy + dy
                    wm.updateViewLayout(v, lp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel()
                    true
                }
                else -> false
            }
        }
        wm.addView(v, lp)
        bubble = v
    }

    private fun togglePanel() { if (shown) hidePanel() else showPanel() }

    private fun rounded(color: Int, r: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(r).toFloat()
        return g
    }

    private fun tv(t: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        v.setTextColor(color)
        if (bold) v.setTypeface(null, Typeface.BOLD)
        return v
    }

    private fun showPanel() {
        val dm = resources.displayMetrics
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL
        p.background = rounded(Color.parseColor("#F2111827"), 18)
        p.setPadding(dp(12), dp(12), dp(12), dp(12))

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        val title = tv("😈 P_STE — Fenêtre IA", 17f, Color.WHITE, true)
        head.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val close = tv("✕", 20f, Color.WHITE, true)
        close.setOnClickListener { hidePanel() }
        head.addView(close)
        p.addView(head)

        val sv = ScrollView(this)
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        sv.addView(l)
        p.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val lp = WindowManager.LayoutParams(
            (dm.widthPixels * 0.92).toInt(),
            (dm.heightPixels * 0.65).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = dp(90)
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        wm.addView(p, lp)
        panel = p
        list = l
        shown = true
        editing = false
        refresh()
    }

    private fun hidePanel() {
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        list = null
        shown = false
        editing = false
    }

    private fun refresh() {
        val l = list ?: return
        if (!shown || editing) return
        l.removeAllViews()
        val items = ReplyStore.snapshot()
        if (items.isEmpty()) {
            l.addView(tv("Aucun message pour l'instant…", 14f, Color.LTGRAY))
            return
        }
        var lastPkg = ""
        for (c in items) {
            if (c.pkg != lastPkg) {
                lastPkg = c.pkg
                val h = tv(if (c.pkg == "com.whatsapp.w4b") "WhatsApp Business" else "WhatsApp", 14f, Color.parseColor("#34D399"), true)
                h.setPadding(0, dp(10), 0, dp(4))
                l.addView(h)
            }
            l.addView(row(c))
        }
    }

    private fun row(c: Conv): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.VERTICAL
        r.background = rounded(Color.parseColor("#1F2937"), 12)
        r.setPadding(dp(10), dp(8), dp(10), dp(8))
        val rp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rp.topMargin = dp(6)
        r.layoutParams = rp

        r.addView(tv("👤 " + c.name, 15f, Color.WHITE, true))
        r.addView(tv(c.lastMsg, 13f, Color.LTGRAY))
        if (c.sent.isNotEmpty()) r.addView(tv("✅ Réponse auto envoyée : " + c.sent, 13f, Color.parseColor("#34D399")))
        if (c.error.isNotEmpty()) r.addView(tv("⚠ " + c.error, 12f, Color.parseColor("#FCA5A5")))

        val et = EditText(this)
        et.setText(c.suggestion)
        et.setTextColor(Color.WHITE)
        et.setHintTextColor(Color.GRAY)
        et.hint = "Suggestion IA…"
        et.textSize = 14f
        et.setOnFocusChangeListener { _, f -> editing = f }
        r.addView(et)

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL

        val send = Button(this)
        send.text = "Envoyer"
        send.isAllCaps = false
        send.setOnClickListener {
            val t = et.text.toString().trim()
            if (t.isNotEmpty()) {
                editing = false
                Thread {
                    val ok = ReplyStore.send(this@BubbleService, c, t)
                    ui.post {
                        Toast.makeText(
                            this@BubbleService,
                            if (ok) "Envoyé ✅" else "Impossible d'envoyer (notification expirée)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }.start()
            }
        }
        bar.addView(send)

        val regen = Button(this)
        regen.text = "🔄"
        regen.setOnClickListener {
            editing = false
            Thread { ReplyStore.generate(this@BubbleService, c) }.start()
        }
        bar.addView(regen)

        val cb = CheckBox(this)
        cb.text = "Auto"
        cb.setTextColor(Color.WHITE)
        cb.isChecked = Prefs.autoContacts(this).contains(c.name)
        cb.setOnCheckedChangeListener { _, on -> Prefs.setAuto(this@BubbleService, c.name, on) }
        bar.addView(cb)

        r.addView(bar)
        return r
    }

    private fun ding() {
        try {
            RingtoneManager.getRingtone(
                this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ).play()
        } catch (_: Exception) {}
        bubble?.animate()?.scaleX(1.35f)?.scaleY(1.35f)?.setDuration(200)?.withEndAction {
            bubble?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(200)?.start()
        }?.start()
    }

    override fun onDestroy() {
        ReplyStore.listener = null
        ReplyStore.onMessage = null
        hidePanel()
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubble = null
        super.onDestroy()
    }
}
