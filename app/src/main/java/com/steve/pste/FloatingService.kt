package com.steve.pste

import android.app.*
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.os.IBinder
import android.text.*
import android.text.style.ForegroundColorSpan
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

private val BG = Color.parseColor("#0B141A")
private val SURF = Color.parseColor("#1F2C34")
private val SURF2 = Color.parseColor("#233138")
private val BORDER = Color.parseColor("#2A3942")
private val TXT = Color.parseColor("#E9EDEF")
private val GRAY = Color.parseColor("#8696A0")
private val GREEN = Color.parseColor("#25D366")
private val SEND = Color.parseColor("#00A884")
private val BLUE = Color.parseColor("#53BDEB")

class FloatingService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var win: View? = null
    private var built = false
    private var isOpen = false
    private var cur: Contact? = null
    private var filter = "all"
    private var query = ""
    private var addApp = "com.whatsapp"
    private var editIdx = -1
    private lateinit var root: FrameLayout
    private lateinit var listV: LinearLayout
    private lateinit var chatV: LinearLayout
    private lateinit var chipsRow: LinearLayout
    private lateinit var rows: LinearLayout
    private lateinit var addPanel: LinearLayout
    private lateinit var addApps: LinearLayout
    private lateinit var nameIn: EditText
    private lateinit var fab: TextView
    private lateinit var scrim: FrameLayout
    private lateinit var menuBox: LinearLayout
    private lateinit var cName: TextView
    private lateinit var cSub: TextView
    private lateinit var cAvatar: TextView
    private lateinit var autoBtn: TextView
    private lateinit var msgBox: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sugg: TextView
    private lateinit var editBar: TextView

    override fun onBind(i: Intent?): IBinder? = null

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)
    private fun bg(col: Int, r: Int = 0, oval: Boolean = false, stroke: Int = 0) = GradientDrawable().apply {
        setColor(col); if (oval) shape = GradientDrawable.OVAL else cornerRadius = dp(r).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }
    private fun tv(t: CharSequence, sp: Float, col: Int = Color.WHITE) = TextView(this).apply { text = t; textSize = sp; setTextColor(col) }
    private fun mi(t: String, a: () -> Unit) = Pair(t, a)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun chip(t: CharSequence, sel: Boolean, a: () -> Unit) = tv(t, 13f, if (sel) TXT else GRAY).apply {
        background = if (sel) bg(SURF, 16, false, BORDER) else bg(Color.TRANSPARENT, 16, false, BORDER)
        setPadding(dp(12), dp(6), dp(12), dp(6)); setOnClickListener { a() }
    }
    private fun dotted(col: Int, label: String) = SpannableString("● $label").apply { setSpan(ForegroundColorSpan(col), 0, 1, 0) }
    private fun avatarBg(name: String) = bg(Color.parseColor(listOf("#6A4B34", "#0F4C5C", "#2F5D50", "#5B3A6B", "#7A3B3B")[(name.hashCode() and 0x7fffffff) % 5]), 0, true)

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
        val iv = ImageView(this).apply { setImageResource(R.drawable.ic_dragon) }
        val p = WindowManager.LayoutParams(dp(64), dp(64), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
        val p = WindowManager.LayoutParams((dm.widthPixels * 0.95).toInt(), (dm.heightPixels * 0.74).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; p.y = dp(40)
        p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        wm.addView(win, p); isOpen = true
        Store.prune(); Store.openKey = cur?.let { Store.key(it) }
        fillList(); if (cur != null) fillChat()
    }

    private fun closeWin() { try { wm.removeView(win) } catch (e: Exception) {}; isOpen = false; Store.openKey = null; hideMenu() }

    // ---------------------------------------------------------------- construction
    private fun build(): View {
        root = FrameLayout(this).apply { background = bg(BG, 18); clipToOutline = true }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Liste des discussions
        listV = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(14), dp(12), dp(8)) }
        top.addView(tv("P_STE", 24f, TXT).apply { typeface = Typeface.DEFAULT_BOLD }, lp(0, -2, 1f))
        top.addView(tv("⋮", 24f, TXT).apply { setPadding(dp(10), 0, dp(6), 0); setOnClickListener { showMenu(listMenu()) } })
        top.addView(tv("✕", 18f, GRAY).apply { setPadding(dp(10), 0, dp(4), 0); setOnClickListener { closeWin() } })
        val search = EditText(this).apply {
            hint = "🔍  Rechercher…"; setHintTextColor(GRAY); setTextColor(TXT); textSize = 15f; setSingleLine()
            background = bg(SURF, 26); setPadding(dp(18), dp(10), dp(18), dp(10))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { query = s.toString().trim().lowercase(); fillList() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
            })
        }
        chipsRow = LinearLayout(this)
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; setPadding(dp(12), dp(10), dp(12), dp(6)); addView(chipsRow) }
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sv = ScrollView(this).apply { addView(rows) }
        addPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(SURF); setPadding(dp(10), dp(8), dp(10), dp(8)); visibility = View.GONE }
        addApps = LinearLayout(this)
        nameIn = EditText(this).apply { hint = "Nom exact dans la notification"; setHintTextColor(GRAY); setTextColor(TXT); textSize = 14f; setSingleLine() }
        val r2 = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        r2.addView(nameIn, lp(0, -2, 1f))
        r2.addView(tv("Ajouter", 14f, BG).apply { gravity = Gravity.CENTER; background = bg(GREEN, 18); setPadding(dp(14), dp(8), dp(14), dp(8)); setOnClickListener { addContact() } })
        r2.addView(tv("✕", 16f, GRAY).apply { setPadding(dp(12), 0, dp(4), 0); setOnClickListener { toggleAdd(false) } })
        addPanel.addView(addApps); addPanel.addView(r2)
        listV.addView(top)
        listV.addView(search, lp(-1, -2).apply { setMargins(dp(12), 0, dp(12), 0) })
        listV.addView(hs); listV.addView(sv, lp(-1, 0, 1f)); listV.addView(addPanel)

        // Page de conversation
        chatV = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setBackgroundColor(BG) }
        val ch = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(SURF); setPadding(dp(8), dp(8), dp(12), dp(8)) }
        ch.addView(tv("←", 24f, TXT).apply { setPadding(dp(8), 0, dp(10), 0); setOnClickListener { goBack() } })
        cAvatar = tv("", 16f).apply { gravity = Gravity.CENTER }
        ch.addView(cAvatar, lp(dp(38), dp(38)))
        val nm = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        cName = tv("", 17f, TXT).apply { typeface = Typeface.DEFAULT_BOLD; maxLines = 1 }
        cSub = tv("", 12f, GRAY)
        nm.addView(cName); nm.addView(cSub)
        ch.addView(nm, lp(0, -2, 1f))
        ch.addView(tv("⋮", 24f, TXT).apply { setPadding(dp(10), 0, 0, 0); setOnClickListener { showMenu(chatMenu()) } })

        val opt = LinearLayout(this).apply { setPadding(dp(8), dp(6), dp(8), dp(6)) }
        opt.addView(chip("💡 Suggestion", false) { suggest(false) }, lp(-2, -2).apply { rightMargin = dp(6) })
        opt.addView(chip("🔄 Changer", false) { suggest(true) }, lp(-2, -2).apply { rightMargin = dp(6) })
        autoBtn = chip("", false) { toggleAuto() }
        opt.addView(autoBtn)

        msgBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        scroll = ScrollView(this).apply { addView(msgBox) }
        sugg = tv("", 13f, Color.parseColor("#FFE9A8")).apply {
            background = bg(Color.parseColor("#3B3418"), 10); setPadding(dp(10), dp(8), dp(10), dp(8)); visibility = View.GONE
            setOnClickListener {
                val k = cur?.let { Store.key(it) } ?: return@setOnClickListener
                Store.suggestion[k]?.let { s -> input.setText(s); input.setSelection(input.text.length) }
            }
        }
        editBar = tv("✏️ Modification d'un message — toucher pour annuler", 12f, TXT).apply {
            setBackgroundColor(SURF2); setPadding(dp(12), dp(6), dp(12), dp(6)); visibility = View.GONE
            setOnClickListener { editIdx = -1; input.setText(""); visibility = View.GONE }
        }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(6), dp(6), dp(6)) }
        input = EditText(this).apply { hint = "Message"; setHintTextColor(GRAY); setTextColor(TXT); textSize = 15f; background = bg(SURF, 24); setPadding(dp(16), dp(9), dp(16), dp(9)); maxLines = 4 }
        val send = tv("➤", 20f).apply { gravity = Gravity.CENTER; background = bg(SEND, 0, true); setOnClickListener { doSend() } }
        bar.addView(input, lp(0, -2, 1f)); bar.addView(send, lp(dp(46), dp(46)).apply { leftMargin = dp(6) })
        chatV.addView(ch); chatV.addView(opt); chatV.addView(scroll, lp(-1, 0, 1f))
        chatV.addView(sugg, lp(-1, -2).apply { setMargins(dp(8), 0, dp(8), dp(4)) })
        chatV.addView(editBar); chatV.addView(bar)

        col.addView(listV, lp(-1, 0, 1f)); col.addView(chatV, lp(-1, 0, 1f))
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        fab = tv("＋", 28f, BG).apply { gravity = Gravity.CENTER; background = bg(Color.WHITE, 16); setOnClickListener { toggleAdd(true) } }
        root.addView(fab, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(16), dp(16)) })
        menuBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = bg(SURF2, 12); setPadding(0, dp(6), 0, dp(6)) }
        scrim = FrameLayout(this).apply { setBackgroundColor(0x66000000); visibility = View.GONE; setOnClickListener { hideMenu() } }
        scrim.addView(menuBox, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(48), dp(12), 0) })
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        return root
    }

    // ---------------------------------------------------------------- menus
    private fun showMenu(items: List<Pair<String, () -> Unit>>) {
        menuBox.removeAllViews()
        items.forEach { (t, a) -> menuBox.addView(tv(t, 16f, TXT).apply { setPadding(dp(20), dp(13), dp(28), dp(13)); setOnClickListener { hideMenu(); a() } }) }
        scrim.visibility = View.VISIBLE
    }
    private fun hideMenu() { scrim.visibility = View.GONE }

    private fun ephLabel() = when (Store.eph) { 24 -> "24 heures"; 168 -> "7 jours"; else -> "Désactivée" }
    private fun cycleEph() {
        Store.eph = when (Store.eph) { 0 -> 24; 24 -> 168; else -> 0 }
        Store.save(); Store.prune(); fillList(); if (cur != null) fillChat()
    }
    private fun listMenu(): List<Pair<String, () -> Unit>> = listOf(
        mi("⏳ Messagerie éphémère : ${ephLabel()}") { cycleEph(); showMenu(listMenu()) },
        mi("➕ Max par appli : ${Store.max}") { Store.max++; Store.save(); showMenu(listMenu()) },
        mi("➖ Max par appli") { if (Store.max > 1) Store.max--; Store.save(); showMenu(listMenu()) },
        mi("⚙️ Paramètres (mes infos, consignes IA)") { startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    )
    private fun chatMenu(): List<Pair<String, () -> Unit>> = listOf(
        mi("⏳ Messagerie éphémère : ${ephLabel()}") { cycleEph(); showMenu(chatMenu()) },
        mi("🧹 Vider la discussion") { cur?.let { Store.msgs[Store.key(it)]?.clear(); Store.save(); fillChat() } }
    )

    // ---------------------------------------------------------------- liste
    private fun stamp(t: Long): String {
        val df = SimpleDateFormat("yyyyMMdd", Locale.getDefault()); val now = System.currentTimeMillis()
        return when (df.format(Date(t))) {
            df.format(Date(now)) -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))
            df.format(Date(now - 86400000L)) -> "Hier"
            else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(t))
        }
    }

    private fun fillChips() {
        chipsRow.removeAllViews()
        fun add(id: String, label: CharSequence) { chipsRow.addView(chip(label, filter == id) { filter = id; fillList() }, lp(-2, -2).apply { rightMargin = dp(8) }) }
        add("all", "Toutes"); add("unread", "Non lues"); add("auto", dotted(GREEN, "Réponses de l'IA"))
        Store.APPS.forEach { (pkg, n) -> add("app:$pkg", n) }
    }

    private fun fillList() {
        fillChips()
        rows.removeAllViews()
        var cs = Store.contacts.toList()
        val f = filter
        cs = when {
            f == "unread" -> cs.filter { (Store.unread[Store.key(it)] ?: 0) > 0 }
            f == "auto" -> cs.filter { Store.autoSet.contains(Store.key(it)) }
            f.startsWith("app:") -> cs.filter { it.app == f.substring(4) }
            else -> cs
        }
        if (query.isNotEmpty()) cs = cs.filter { it.name.lowercase().contains(query) }
        cs = cs.sortedByDescending { Store.msgs[Store.key(it)]?.lastOrNull()?.time ?: 0L }
        cs.forEach { rows.addView(row(it)) }
        if (cs.isEmpty()) rows.addView(tv("Aucune discussion. Touchez le bouton ＋ pour ajouter un contact (nom exact affiché dans la notification).", 14f, GRAY).apply { setPadding(dp(18), dp(18), dp(18), dp(18)) })
    }

    private fun row(ct: Contact): View {
        val k = Store.key(ct); val last = Store.msgs[k]?.lastOrNull(); val u = Store.unread[k] ?: 0
        val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(9), dp(14), dp(9)) }
        r.addView(tv(ct.name.take(1).uppercase(), 22f).apply { gravity = Gravity.CENTER; background = avatarBg(ct.name) }, lp(dp(56), dp(56)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        val l1 = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        l1.addView(tv(ct.name, 17f, TXT).apply { typeface = Typeface.DEFAULT_BOLD; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, lp(0, -2, 1f))
        if (last != null) l1.addView(tv(stamp(last.time), 12f, if (u > 0) GREEN else GRAY))
        val pv = SpannableString(when { last == null -> "Aucun message"; last.mine -> (if (last.ok) "✓✓ " else "✓ ") + last.text; else -> last.text })
        if (last != null && last.mine) pv.setSpan(ForegroundColorSpan(if (last.ok) BLUE else GRAY), 0, if (last.ok) 2 else 1, 0)
        val l2 = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        l2.addView(tv(pv, 15f, GRAY).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, lp(0, -2, 1f))
        if (Store.autoSet.contains(k)) l2.addView(tv("🤖", 12f).apply { setPadding(dp(4), 0, dp(4), 0) })
        if (u > 0) l2.addView(tv("$u", 12f).apply { gravity = Gravity.CENTER; minWidth = dp(20); setPadding(dp(6), dp(1), dp(6), dp(1)); background = bg(GREEN, 12) })
        col.addView(l1); col.addView(l2); r.addView(col, lp(0, -2, 1f))
        r.setOnClickListener { openChat(ct) }
        r.setOnLongClickListener { showMenu(listOf(mi("🗑 Supprimer le contact") { Store.contacts.remove(ct); Store.msgs.remove(k); Store.autoSet.remove(k); Store.save(); fillList() })); true }
        return r
    }

    private fun toggleAdd(show: Boolean) {
        addPanel.visibility = if (show) View.VISIBLE else View.GONE
        fab.visibility = if (show) View.GONE else View.VISIBLE
        if (show) fillAddApps()
    }
    private fun fillAddApps() {
        addApps.removeAllViews()
        Store.APPS.forEach { (pkg, n) -> addApps.addView(chip(n, addApp == pkg) { addApp = pkg; fillAddApps() }, lp(-2, -2).apply { rightMargin = dp(6) }) }
    }
    private fun addContact() {
        val nm = nameIn.text.toString().trim(); if (nm.isEmpty()) return
        if (Store.contacts.count { it.app == addApp } >= Store.max) { toast("Maximum atteint pour cette appli (⋮ → Max)"); return }
        if (Store.contacts.none { it.app == addApp && it.name.equals(nm, true) }) { Store.contacts.add(Contact(addApp, nm)); Store.save() }
        nameIn.setText(""); toggleAdd(false); fillList()
    }

    // ---------------------------------------------------------------- conversation
    private fun openChat(ct: Contact) {
        val k = Store.key(ct)
        cur = ct; Store.openKey = k; Store.unread[k] = 0; editIdx = -1; editBar.visibility = View.GONE
        cName.text = ct.name; cAvatar.text = ct.name.take(1).uppercase(); cAvatar.background = avatarBg(ct.name)
        listV.visibility = View.GONE; chatV.visibility = View.VISIBLE; fab.visibility = View.GONE
        updAuto(); fillChat()
    }

    private fun goBack() {
        cur = null; Store.openKey = null; editIdx = -1; input.setText("")
        chatV.visibility = View.GONE; listV.visibility = View.VISIBLE
        fab.visibility = if (addPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        fillList()
    }

    private fun updAuto() {
        val ct = cur ?: return; val on = Store.autoSet.contains(Store.key(ct))
        autoBtn.text = "🤖 Auto : " + (if (on) "ON" else "OFF")
        autoBtn.setTextColor(if (on) GREEN else GRAY)
        autoBtn.background = if (on) bg(Color.parseColor("#103629"), 16, false, GREEN) else bg(Color.TRANSPARENT, 16, false, BORDER)
        cSub.text = (Store.APPS[ct.app] ?: "") + (if (on) " · réponses automatiques" else "")
    }

    private fun toggleAuto() {
        val ct = cur ?: return; val k = Store.key(ct)
        if (!Store.autoSet.remove(k)) Store.autoSet.add(k)
        Store.save(); updAuto()
    }

    private fun suggest(change: Boolean) {
        val ct = cur ?: return; val k = Store.key(ct); val s = Store.suggestion[k]
        if (s != null && !change) { input.setText(s); input.setSelection(input.text.length); return }
        val hist = Store.msgs[k]?.toList() ?: emptyList()
        if (hist.none { !it.mine }) { toast("Aucun message reçu de ce contact"); return }
        Store.loading.add(k); fillChat()
        Server.ask(Store.APPS[ct.app] ?: "", ct.name, hist, s) { r ->
            Store.loading.remove(k)
            if (r != null) Store.suggestion[k] = r else toast("Serveur injoignable")
            fillChat()
        }
    }

    private fun fillChat() {
        val ct = cur ?: return; val k = Store.key(ct)
        msgBox.removeAllViews()
        val df = SimpleDateFormat("yyyyMMdd", Locale.getDefault()); var day = ""
        Store.msgs[k]?.forEachIndexed { i, m ->
            val d = df.format(Date(m.time))
            if (d != day) { day = d; msgBox.addView(dayPill(m.time)) }
            msgBox.addView(bubbleView(m, i, k))
        }
        val s = Store.suggestion[k]
        when {
            Store.loading.contains(k) -> { sugg.text = "💡 Réflexion…"; sugg.visibility = View.VISIBLE }
            s != null -> { sugg.text = "💡 $s  (toucher pour utiliser)"; sugg.visibility = View.VISIBLE }
            else -> sugg.visibility = View.GONE
        }
        scroll.post { scroll.scrollTo(0, msgBox.bottom) }
    }

    private fun dayPill(t: Long): View {
        val df = SimpleDateFormat("yyyyMMdd", Locale.getDefault()); val now = System.currentTimeMillis()
        val label = when (df.format(Date(t))) {
            df.format(Date(now)) -> "Aujourd'hui"
            df.format(Date(now - 86400000L)) -> "Hier"
            else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(t))
        }
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(6))
            addView(tv(label, 12f, GRAY).apply { background = bg(Color.parseColor("#182229"), 8); setPadding(dp(10), dp(4), dp(10), dp(4)) })
        }
    }

    private fun bubbleView(m: Msg, i: Int, k: String): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(6), dp(10), dp(4))
            background = bg(Color.parseColor(if (m.mine) "#005C4B" else "#202C33"), 10)
            setOnLongClickListener { showMenu(listOf(mi("✏️ Modifier") { startEdit(i) }, mi("🗑 Supprimer") { Store.delMsg(k, i) })); true }
        }
        box.addView(tv(m.text, 15f, TXT).apply { maxWidth = (resources.displayMetrics.widthPixels * 0.62).toInt() })
        val ts = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.time))
        val sp = SpannableString(if (m.mine) "$ts  " + (if (m.ok) "✓✓" else "✓") else ts)
        if (m.mine) sp.setSpan(ForegroundColorSpan(if (m.ok) BLUE else GRAY), ts.length + 2, sp.length, 0)
        box.addView(tv(sp, 11f, GRAY).apply { gravity = Gravity.END })
        return LinearLayout(this).apply { gravity = if (m.mine) Gravity.END else Gravity.START; setPadding(0, dp(2), 0, dp(2)); addView(box) }
    }

    private fun startEdit(i: Int) {
        val ct = cur ?: return; val m = Store.msgs[Store.key(ct)]?.getOrNull(i) ?: return
        editIdx = i; editBar.visibility = View.VISIBLE
        input.setText(m.text); input.setSelection(input.text.length); input.requestFocus()
    }

    private fun doSend() {
        val ct = cur ?: return; val t = input.text.toString().trim(); if (t.isEmpty()) return
        val k = Store.key(ct)
        if (editIdx >= 0) { Store.editMsg(k, editIdx, t); editIdx = -1; editBar.visibility = View.GONE } else Store.send(ct, t, this)
        input.setText("")
    }
}
