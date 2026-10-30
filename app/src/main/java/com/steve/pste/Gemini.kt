package com.steve.pste

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Gemini {
    fun ask(url: String, message: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 40000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().put("message", message).toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?: throw Exception("Pas de réponse du serveur (code $code)")
            val text = stream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val reply = json.optString("reply", "")
            if (reply.isNotBlank()) return reply
            val err = json.optJSONObject("error")?.optString("message")
            throw Exception(err ?: "Réponse vide")
        } finally {
            conn.disconnect()
        }
    }
}
