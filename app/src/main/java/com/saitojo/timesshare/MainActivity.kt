package com.saitojo.timesshare

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    data class Destination(val name: String, val webhook: String, val threadId: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_SEND) handleShare(intent) else showSettings()
    }

    private fun destinations(): MutableList<Destination> {
        val raw = prefs.getString("destinations", null)
        if (raw.isNullOrBlank()) {
            val webhook = prefs.getString("webhook_url", "").orEmpty()
            if (webhook.isBlank()) return mutableListOf()
            return mutableListOf(Destination("Times", webhook, prefs.getString("thread_id", "").orEmpty()))
        }
        val result = mutableListOf<Destination>()
        runCatching {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                result += Destination(item.optString("name"), item.optString("webhook"), item.optString("threadId"))
            }
        }
        return result
    }

    private fun saveDestinations(items: List<Destination>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().put("name", item.name).put("webhook", item.webhook).put("threadId", item.threadId))
        }
        prefs.edit().putString("destinations", array.toString()).remove("webhook_url").remove("thread_id").apply()
    }

    private fun showSettings() {
        val items = destinations()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }
        layout.addView(TextView(this).apply { text = "Times Share 設定"; textSize = 24f })
        layout.addView(TextView(this).apply {
            text = "Discord / Slackの送信先を複数登録できます。共有時に送信先を選択します。Webhook URLは端末内だけに保存されます。"
            setPadding(0, 24, 0, 24)
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(list)
        layout.addView(Button(this).apply {
            text = "送信先を追加"
            setOnClickListener { showDestinationEditor(items) { refreshDestinationList(list, items) } }
        })
        setContentView(layout)
        refreshDestinationList(list, items)
    }

    private fun refreshDestinationList(list: LinearLayout, items: MutableList<Destination>) {
        list.removeAllViews()
        items.forEachIndexed { index, item ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(this).apply {
                text = "${item.name}\n${if (item.threadId.isBlank()) "スレッド指定なし" else "既存スレッド"}"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(0, 12, 8, 12)
            })
            row.addView(Button(this).apply {
                text = "削除"
                setOnClickListener {
                    items.removeAt(index)
                    saveDestinations(items)
                    refreshDestinationList(list, items)
                }
            })
            list.addView(row)
        }
    }

    private fun showDestinationEditor(items: MutableList<Destination>, onSaved: () -> Unit) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 0, 32, 0)
        }
        val name = EditText(this).apply { hint = "名前（例: Times）" }
        val webhook = EditText(this).apply {
            hint = "DiscordまたはSlackのWebhook URL"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val threadId = EditText(this).apply {
            hint = "既存スレッドID（任意）"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        form.addView(name)
        form.addView(webhook)
        form.addView(threadId)
        AlertDialog.Builder(this)
            .setTitle("送信先を追加")
            .setView(form)
            .setNegativeButton("キャンセル", null)
            .setPositiveButton("保存") { _, _ ->
                val url = webhook.text.toString().trim()
                val isDiscord = url.startsWith("https://discord.com/api/webhooks/") || url.startsWith("https://discordapp.com/api/webhooks/")
                val isSlack = url.startsWith("https://hooks.slack.com/services/")
                if (!isDiscord && !isSlack) {
                    Toast.makeText(this, "DiscordまたはSlackのWebhook URLを入力してください", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                items += Destination(name.text.toString().trim().ifBlank { "送信先${items.size + 1}" }, url, threadId.text.toString().trim())
                saveDestinations(items)
                onSaved()
                Toast.makeText(this, "送信先を保存しました", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun handleShare(intent: Intent) {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (sharedText.isBlank()) {
            Toast.makeText(this, "共有テキストを取得できませんでした", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val items = destinations()
        if (items.isEmpty()) {
            Toast.makeText(this, "先にアプリを開いて送信先を設定してください", Toast.LENGTH_LONG).show()
            showSettings()
            return
        }
        if (items.size == 1) send(items[0], sharedText)
        else AlertDialog.Builder(this)
            .setTitle("送信先を選択")
            .setItems(items.map { it.name }.toTypedArray()) { _, which -> send(items[which], sharedText) }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun send(item: Destination, content: String) {
        thread {
            val ok = runCatching { postWebhook(item.webhook, item.threadId, content) }.getOrDefault(false)
            runOnUiThread {
                Toast.makeText(this, if (ok) "${item.name}に保存しました ✓" else "送信に失敗しました", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun postWebhook(webhook: String, threadId: String, content: String): Boolean {
        val isSlack = webhook.startsWith("https://hooks.slack.com/services/")
        val separator = if (webhook.contains('?')) '&' else '?'
        val target = if (isSlack || threadId.isBlank()) webhook else "$webhook${separator}thread_id=$threadId"
        val connection = (URL(target).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        val payloadObject = if (isSlack) {
            JSONObject().put("text", content.take(3000)).apply {
                if (threadId.isNotBlank()) put("thread_ts", threadId)
            }
        } else {
            JSONObject().put("content", content.take(2000))
        }
        val payload = payloadObject.toString()
        connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        return connection.responseCode in 200..299
    }
}
