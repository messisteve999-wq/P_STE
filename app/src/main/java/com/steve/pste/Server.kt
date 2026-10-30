package com.steve.pste

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Server {
    fun ask(app: String, contact: String, text: String, cb: (String?) -> Unit) {
        Thread {
            var r: String? = null
            try {
                val c = URL("http://127.0.0.1:3000/reply").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.connectTimeout = 5000; c.readTimeout = 60000; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                val body = JSONObject().put("message", text).put("text", text).put("contact", contact).put("app", app).toString()
                c.outputStream.use { it.write(body.toByteArray()) }
                val s = c.inputStream.bufferedReader().readText()
                r = try { val o = JSONObject(s); o.optString("reply", o.optString("text", s)) } catch (e: Exception) { s }
            } catch (e: Exception) {}
            val out = r?.trim()?.ifEmpty { null }
            Handler(Looper.getMainLooper()).post { cb(out) }
        }.start()
    }
}
