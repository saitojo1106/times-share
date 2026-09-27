package com.saitojo.timesshare

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_SEND) handleShare(intent) else showSettings()
    }

    private fun showSettings() {
        val input = EditText(this).apply {
            hint = "https://discord.com/api/webhooks/..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.getString("webhook_url", ""))
        }
        val threadId = EditText(this).apply {
            hint = "既存スレッドID（任意）"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getString("thread_id", ""))
        }
        val save = Button(this).apply { text = "Webhook URLを保存" }
        val help = TextView(this).apply {
            text = "Discordの保存先チャンネルでWebhookを作り、そのURLをここだけに保存します。GitHubやAPKのソースには入りません。"
            setPadding(0, 24, 0, 24)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
            addView(TextView(this@MainActivity).apply { text = "Times Share 設定"; textSize = 24f })
            addView(help)
            addView(input)
            addView(threadId)
            addView(save)
        }
        setContentView(layout)
        save.setOnClickListener {
            val value = input.text.toString().trim()
            if (!value.startsWith("https://discord.com/api/webhooks/") && !value.startsWith("https://discordapp.com/api/webhooks/")) {
                Toast.makeText(this, "Discord Webhook URLを入力してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit()
                .putString("webhook_url", value)
                .putString("thread_id", threadId.text.toString().trim())
                .apply()
            Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleShare(intent: Intent) {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val webhook = prefs.getString("webhook_url", null)
        val threadId = prefs.getString("thread_id", "").orEmpty()
        if (webhook.isNullOrBlank()) {
            Toast.makeText(this, "先にアプリを開いてWebhook URLを設定してください", Toast.LENGTH_LONG).show()
            showSettings()
            return
        }
        if (sharedText.isBlank()) {
            Toast.makeText(this, "共有テキストを取得できませんでした", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        thread {
            val ok = runCatching { postDiscord(webhook, threadId, sharedText) }.getOrDefault(false)
            runOnUiThread {
                Toast.makeText(this, if (ok) "Timesに保存しました ✓" else "送信に失敗しました", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun postDiscord(webhook: String, threadId: String, content: String): Boolean {
        val separator = if (webhook.contains('?')) '&' else '?'
        val target = if (threadId.isBlank()) webhook else "$webhook${separator}thread_id=$threadId"
        val connection = (URL(target).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        val payload = JSONObject().put("content", content.take(2000)).toString()
        connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        return connection.responseCode in 200..299
    }
}
