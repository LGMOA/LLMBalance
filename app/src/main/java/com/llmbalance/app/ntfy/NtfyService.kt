package com.llmbalance.app.ntfy

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.llmbalance.app.R
import com.llmbalance.app.data.SettingsStore
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NtfyService : Service() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // No read timeout for WS
        .build()

    private var webSocket: WebSocket? = null
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectDelay = 5_000L
    private var settingsStore: SettingsStore? = null

    companion object {
        const val CHANNEL_ID = "ntfy_notification_channel"
        const val NOTIFICATION_ID_FOREGROUND = 1001
        const val NOTIFICATION_ID_MESSAGE = 2001

        const val ACTION_START = "com.llmbalance.app.START_NTFY"
        const val ACTION_STOP = "com.llmbalance.app.STOP_NTFY"
        const val ACTION_SETTINGS_CHANGED = "com.llmbalance.app.SETTINGS_CHANGED"

        private const val MAX_RECONNECT_DELAY = 120_000L

        fun start(context: Context) {
            val intent = Intent(context, NtfyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NtfyService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SETTINGS_CHANGED -> {
                disconnect()
                connect()
            }
            else -> {
                // Initial start or restart — must call connect() to trigger startForeground()
                // within the ~5s timeout required by startForegroundService()
                connect()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Task Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Claude task completion notifications"
                enableVibration(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun connect() {
        val store = settingsStore ?: return
        val serverUrl = store.serverUrl
        val topic = store.topic

        if (serverUrl.isBlank() || topic.isBlank()) {
            stopSelf()
            return
        }

        // Build WebSocket URL from HTTP URL
        val wsBase = serverUrl
            .trimEnd('/')
            .replace("http://", "ws://")
            .replace("https://", "wss://")
        val wsUrl = "$wsBase/$topic/ws"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectDelay = 5_000L // Reset reconnect delay on success
                updateForegroundNotification("Connected", "Waiting for tasks...")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    val event = msg.optString("event", "")
                    if (event == "message") {
                        val title = msg.optString("title", "Notification")
                        val body = msg.optString("message", "")
                        val priority = msg.optInt("priority", 3)
                        val tags = msg.optJSONArray("tags")
                        showMessageNotification(title, body, priority)
                    }
                } catch (e: Exception) {
                    showMessageNotification("Claude Notification", text)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                updateForegroundNotification("Connecting...", "Will retry shortly")
                scheduleReconnect()
            }
        })

        updateForegroundNotification("Connecting...", "Establishing connection to server")
        startForeground(NOTIFICATION_ID_FOREGROUND, buildForegroundNotification("Connecting...", "Establishing connection..."))
    }

    private fun disconnect() {
        handler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "Service stopped")
        webSocket = null
    }

    private fun scheduleReconnect() {
        handler.postDelayed({
            if (webSocket == null || webSocket?.queueSize() ?: 0 >= 0) {
                connect()
            }
        }, reconnectDelay)
        reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY)
    }

    private fun buildForegroundNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.llmbalance.app.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateForegroundNotification(title: String, content: String) {
        val notification = buildForegroundNotification(title, content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_FOREGROUND, notification)
    }

    private fun showMessageNotification(title: String, body: String, priority: Int = 3) {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.llmbalance.app.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val importance = if (priority >= 4) {
            NotificationCompat.PRIORITY_HIGH
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(importance)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_MESSAGE + System.currentTimeMillis().toInt() % 1000, notification)
    }
}
