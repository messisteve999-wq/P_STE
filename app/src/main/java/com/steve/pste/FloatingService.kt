package com.steve.pste

import android.app.*
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.os.IBinder
import android.text.TextUtils
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class FloatingService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var win: View? = null
    private var built = false
    private var isOpen = false
    private var cur: Contact? = null
    private var tab = "com.whatsapp"
    private lateinit var title: TextView
    private lateinit var back: TextView
    private lateinit var modeBtn: TextView
    private lateinit var listV: LinearLayout
    private lateinit var chatV: LinearLayout
    private lateinit var tabsRow: LinearLayout
    private lateinit var rows: LinearLayout
    private lateinit var countTv: TextView
    private lateinit var msgBox: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sugg: TextView
    private val green get() = c("#075E54")

    override fun onBind(i: Intent?): IBinder? = null

    private fun c(s: String) = Color.parseColor(s)
    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)
    private fun bg(col: Int, r: Int = 0, oval: Boolean = false) = GradientDrawable().apply {
        setColor(col); if (oval) shape = GradientDrawable.OVAL else cornerRadius = dp(r).toFloat()
    }
    private fun tv(t: String, sp: Float, col: Int = Color.BLACK) = TextView(this).apply { text = t; textSize = sp; setTextColor(col) }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        Store.init(applicationContext)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("pste", "P_STE", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, "pste").setSmallIcon(R.drawable.ic_demon).setContentTitle("P_STE actif").build())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        Store.onChange = { if (isOpen) { fillList(); if (cur != null) fillChat() } }
        Store.onSound = {
            try { RingtoneManager.getRingtone(applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)).play() } catch (e: Exception) {}
        }
        if (bubble == null) addBubble()
        return START_STICKY
    }

    override fun onDestroy() {
        Store.onChange = null; Store.onSound = null; Store.openKey = null
        try { if (isOpen) wm.removeView(win) } catch (e: Exception) {}
        try { bubble?.let { wm.removeView(it) } } catch (e: Exception) {}
        super.onDestroy()
    }

    private fun addBubble() {
        val iv = ImageView(this).apply { setImageResource(R.drawable.ic_demon) }
        val p = WindowManager.LayoutParams(dp(56), dp(56), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.START; p.x = 0; p.y = dp(200)
        iv.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY; moved = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) moved = true
                        p.x = sx + dx; p.y = sy + dy; wm.updateViewLayout(v, p)
                    }
                    MotionEvent.ACTION_UP -> if (!moved) { if (isOpen) closeWin() else openWin() }
                }
                return true
            }
        })
        wm.addView(iv, p); bubble = iv
    }

    private fun openWin() {
        if (!built) { win = build(); built = true }
        val dm = resources.displayMetrics
        val p = WindowManager.LayoutParams((dm.widthPixels * 0.94).toInt(), (dm.heightPixels * 0.62).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; p.y = dp(60)
        p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        wm.addView(win, p); isOpen = true
        Store.openKey = cur?.let { Store.key(it) }
        fillList(); updMode(); if (cur != null) fillChat()
    }

    private fun closeWin() { try { wm.removeView(win) } catch (e: Exception) {}; isOpen = false; Store.openKey = null }

    private fun updMode() { modeBtn.text = if (Store.auto) "🤖 Auto" else "💡 Suggestion" }

    private fun build(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = bg(Color.WHITE, 16); clipToOutline = true }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(green); setPadding(dp(12), dp(10), dp(12), dp(10)) }
        back = tv("←", 22f, Color.WHITE).apply { visibility = View.GONE; setPadding(0, 0, dp(12), 0); setOnClickListener { goBack() } }
        title = tv("P_STE", 18f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD }
        modeBtn = tv("", 12f, Color.WHITE).apply {
            setPadding(dp(8), dp(4), dp(8), dp(4)); background = bg(0x33FFFFFF, 12)
            setOnClickListener { Store.auto = !Store.auto; Store.save(); updMode() }
        }
        val close = tv("✕", 18f, Color.WHITE).apply { setPadding(dp(12), 0, 0, 0); setOnClickListener { closeWin() } }
        head.addView(back); head.addView(title, lp(0, -2, 1f)); head.addView(modeBtn); head.addView(close)

        // Liste de contacts (écran d'accueil façon messagerie)
        listV = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabsRow = LinearLayout(this)
        countTv = tv("", 13f, green)
        val lim = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(4)) }
        lim.addView(tv("Max par appli :", 12f, Color.GRAY))
        lim.addView(tv("  −  ", 20f, green).apply { setOnClickListener { if (Store.max > 1) { Store.max--; Store.save(); fillList() } } })
        lim.addView(countTv)
        lim.addView(tv("  +  ", 20f, green).apply { setOnClickListener { Store.max++; Store.save(); fillList() } })
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sv = ScrollView(this).apply { addView(rows) }
        val add = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(c("#F0F0F0")); setPadding(dp(8), dp(4), dp(8), dp(4)) }
        val nameIn = EditText(this).apply { hint = "Nom du contact"; textSize = 14f; setSingleLine() }
        val plus = tv("+", 28f, green).apply {
            setPadding(dp(14), 0, dp(8), 0)
            setOnClickListener {
                val nm = nameIn.text.toString().trim()
                if (nm.isEmpty()) return@setOnClickListener
                if (Store.contacts.count { it.app == tab } >= Store.max) { Toast.makeText(context, "Maximum atteint (voir − / +)", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                if (Store.contacts.none { it.app == tab && it.name.equals(nm, true) }) { Store.contacts.add(Contact(tab, nm)); Store.save() }
                nameIn.setText(""); fillList()
            }
        }
        add.addView(nameIn, lp(0, -2, 1f)); add.addView(plus)
        listV.addView(tabsRow); listV.addView(lim); listV.addView(sv, lp(-1, 0, 1f)); listV.addView(add)

        // Conversation
        chatV = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setBackgroundColor(c("#ECE5DD")) }
        msgBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        scroll = ScrollView(this).apply { addView(msgBox) }
        sugg = tv("", 13f, c("#5D4037")).apply {
            background = bg(c("#FFF3C4"), 10); setPadding(dp(10), dp(8), dp(10), dp(8)); visibility = View.GONE
            setOnClickListener {
                val k = cur?.let { Store.key(it) } ?: return@setOnClickListener
                input.setText(Store.suggestion.remove(k) ?: ""); input.setSelection(input.text.length); fillChat()
            }
        }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(6), dp(6), dp(6)) }
        input = EditText(this).apply { hint = "Message"; textSize = 15f; background = bg(Color.WHITE, 22); setPadding(dp(14), dp(8), dp(14), dp(8)); maxLines = 4 }
        val send = tv("➤", 20f, Color.WHITE).apply { gravity = Gravity.CENTER; background = bg(green, 0, true); setOnClickListener { doSend() } }
        bar.addView(input, lp(0, -2, 1f)); bar.addView(send, lp(dp(44), dp(44)).apply { leftMargin = dp(6) })
        chatV.addView(scroll, lp(-1, 0, 1f))
        chatV.addView(sugg, lp(-1, -2).apply { setMargins(dp(8), 0, dp(8), dp(4)) })
        chatV.addView(bar)

        root.addView(head); root.addView(listV, lp(-1, 0, 1f)); root.addView(chatV, lp(-1, 0, 1f))
        return root
    }

    private fun fillList() {
        tabsRow.removeAllViews()
        Store.APPS.forEach { (pkg, name) ->
            val sel = pkg == tab
            tabsRow.addView(tv(if (pkg == "com.whatsapp.w4b") "WA Business" else name, 12f, if (sel) green else Color.GRAY).apply {
                gravity = Gravity.CENTER; setPadding(dp(4), dp(10), dp(4), dp(10))
                if (sel) { typeface = Typeface.DEFAULT_BOLD; setBackgroundColor(c("#E0F2F1")) }
                setOnClickListener { tab = pkg; fillList() }
            }, lp(0, -2, 1f))
        }
        rows.removeAllViews()
        val cs = Store.contacts.filter { it.app == tab }
        cs.forEach { rows.addView(row(it)) }
        if (cs.isEmpty()) rows.addView(tv("Aucun contact. Ajoutez en bas le nom exact affiché dans la notification. Appui long sur un contact = supprimer.", 13f, Color.GRAY).apply { setPadding(dp(14), dp(14), dp(14), dp(14)) })
        countTv.text = "${cs.size}/${Store.max}"
    }

    private fun row(ct: Contact): View {
        val k = Store.key(ct)
        val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
        r.addView(tv(ct.name.take(1).uppercase(), 18f, Color.WHITE).apply { gravity = Gravity.CENTER; background = bg(c("#128C7E"), 0, true) }, lp(dp(44), dp(44)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(8), 0) }
        col.addView(tv(ct.name, 15f).apply { typeface = Typeface.DEFAULT_BOLD })
        col.addView(tv(Store.msgs[k]?.lastOrNull()?.text ?: "Aucun message", 12f, Color.GRAY).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        r.addView(col, lp(0, -2, 1f))
        val u = Store.unread[k] ?: 0
        if (u > 0) r.addView(tv("$u", 11f, Color.WHITE).apply { gravity = Gravity.CENTER; setPadding(dp(7), dp(2), dp(7), dp(2)); background = bg(c("#25D366"), 10) })
        r.setOnClickListener { openChat(ct) }
        r.setOnLongClickListener { Store.contacts.remove(ct); Store.msgs.remove(k); Store.save(); fillList(); true }
        return r
    }

    private fun openChat(ct: Contact) {
        cur = ct; Store.openKey = Store.key(ct); Store.unread[Store.key(ct)] = 0
        title.text = ct.name; back.visibility = View.VISIBLE
        listV.visibility = View.GONE; chatV.visibility = View.VISIBLE
        fillChat()
    }

    private fun goBack() {
        cur = null; Store.openKey = null; title.text = "P_STE"; back.visibility = View.GONE
        chatV.visibility = View.GONE; listV.visibility = View.VISIBLE; fillList()
    }

    private fun fillChat() {
        val ct = cur ?: return; val k = Store.key(ct)
        msgBox.removeAllViews()
        Store.msgs[k]?.forEach { msgBox.addView(bubbleView(it)) }
        val s = Store.suggestion[k]
        sugg.visibility = if (s != null) View.VISIBLE else View.GONE
        sugg.text = "💡 " + (s ?: "") + "  (toucher pour utiliser)"
        scroll.post { scroll.scrollTo(0, msgBox.bottom) }
    }

    private fun bubbleView(m: Msg): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(6), dp(10), dp(4))
            background = bg(c(if (m.mine) "#DCF8C6" else "#FFFFFF"), 10)
        }
        box.addView(tv(m.text, 15f).apply { maxWidth = (resources.displayMetrics.widthPixels * 0.6).toInt() })
        box.addView(tv(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.time)), 10f, Color.GRAY).apply { gravity = Gravity.END })
        return LinearLayout(this).apply { gravity = if (m.mine) Gravity.END else Gravity.START; setPadding(0, dp(3), 0, dp(3)); addView(box) }
    }

    private fun doSend() {
        val ct = cur ?: return; val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText(""); Store.send(ct, t, this)
    }
}
