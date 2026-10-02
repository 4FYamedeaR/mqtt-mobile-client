package com.mqttmobile.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.NetworkCapabilities
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mqttmobile.app.MainActivity
import com.mqttmobile.app.MqttApplication
import com.mqttmobile.app.R
import com.mqttmobile.app.data.local.CredentialStore
import com.mqttmobile.app.data.local.ProfileStore
import com.mqttmobile.app.data.local.SubscriptionStore
import com.mqttmobile.app.data.model.MessageDirection
import com.mqttmobile.app.data.model.ConnectionStatus
import com.mqttmobile.app.data.mqtt.MqttEvent
import com.mqttmobile.app.util.PayloadFormatter
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap

class MqttKeepAliveService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val credentialStore by lazy { CredentialStore(applicationContext) }
    private val runtimePreferences by lazy { getSharedPreferences(RUNTIME_PREFERENCES, MODE_PRIVATE) }
    private val repository by lazy { (application as MqttApplication).mqttRepository }
    private val connectJobs = ConcurrentHashMap<String, Job>()
    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java) }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            serviceScope.launch {
                activeProfileIds().forEach { profileId ->
                    val status = repository.connectionStatuses.value[profileId]
                    if (status == null || status == com.mqttmobile.app.data.model.ConnectionStatus.FAILED || status == com.mqttmobile.app.data.model.ConnectionStatus.DISCONNECTED) {
                        connectJobs[profileId]?.cancel()
                        connectJobs[profileId] = serviceScope.launch { connectProfile(profileId) }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat()
        runCatching {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                networkCallback
            )
        }
        serviceScope.launch {
            repository.events.collect { event ->
                when (event) {
                    is MqttEvent.Message -> if (event.message.direction == MessageDirection.RECEIVED) {
                        showMessageNotification(event.message.topic, PayloadFormatter.summary(event.message.payload))
                    }
                    is MqttEvent.State -> startForegroundCompat()
                    else -> Unit
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                if (!profileId.isNullOrBlank()) {
                    addActiveProfile(profileId)
                    startForegroundCompat()
                    connectJobs[profileId]?.cancel()
                    connectJobs[profileId] = serviceScope.launch { connectProfile(profileId) }
                }
            }

            ACTION_STOP -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                if (profileId.isNullOrBlank()) clearActiveProfiles() else removeActiveProfile(profileId)
                serviceScope.launch {
                    if (profileId.isNullOrBlank()) {
                        connectJobs.values.forEach(Job::cancel)
                        connectJobs.clear()
                        repository.disconnect()
                    } else {
                        connectJobs.remove(profileId)?.cancel()
                        repository.disconnect(profileId)
                    }
                    if (activeProfileIds().isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    }
                }
            }

            null -> {
                activeProfileIds().forEach { profileId ->
                    connectJobs[profileId]?.cancel()
                    connectJobs[profileId] = serviceScope.launch { connectProfile(profileId) }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        connectJobs.values.forEach(Job::cancel)
        connectJobs.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (activeProfileIds().isNotEmpty()) {
            ContextCompat.startForegroundService(this, Intent(this, MqttKeepAliveService::class.java))
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun connectProfile(profileId: String) {
        val profile = ProfileStore(applicationContext).profiles.value.firstOrNull { it.id == profileId } ?: run {
            removeActiveProfile(profileId)
            return
        }
        repository.connect(
            profile = profile.copy(clientPrivateKeyPem = credentialStore.getPrivateKey(profile.id)),
            password = credentialStore.get(profile.id),
            subscriptions = SubscriptionStore(applicationContext).forProfile(profile.id)
        )
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(connectionNotificationText())
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun connectionNotificationText(): String {
        val statuses = repository.connectionStatuses.value.values
        val connected = statuses.count { it == ConnectionStatus.CONNECTED }
        val reconnecting = statuses.count { it == ConnectionStatus.RECONNECTING || it == ConnectionStatus.CONNECTING }
        return when {
            connected > 0 && reconnecting > 0 -> "已连接 ${connected} 个，${reconnecting} 个正在重连"
            connected > 0 -> "已连接 ${connected} 个配置"
            reconnecting > 0 -> "${reconnecting} 个配置正在连接或重连"
            else -> "MQTT 后台连接中（${activeProfileIds().size} 个配置）"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL,
                "MQTT 后台连接",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持 MQTT 连接并接收后台消息"
                setShowBadge(false)
            }
            val messageChannel = NotificationChannel(
                MESSAGE_NOTIFICATION_CHANNEL,
                "MQTT 消息通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "收到 MQTT 消息时提醒"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(serviceChannel, messageChannel))
        }
    }

    private fun showMessageNotification(topic: String, summary: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val openIntent = PendingIntent.getActivity(
            this,
            topic.hashCode(),
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, MESSAGE_NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("收到 MQTT 消息")
            .setContentText("$topic · $summary")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(this).notify(MESSAGE_NOTIFICATION_ID + topic.hashCode().and(0x0FFF), notification)
        }
    }

    companion object {
        private const val NOTIFICATION_CHANNEL = "mqtt_keep_alive"
        private const val MESSAGE_NOTIFICATION_CHANNEL = "mqtt_messages"
        private const val NOTIFICATION_ID = 4101
        private const val MESSAGE_NOTIFICATION_ID = 5100
        private const val RUNTIME_PREFERENCES = "mqtt_runtime"
        private const val KEY_PROFILE_ID = "active_profile_id"
        private const val KEY_PROFILE_IDS = "active_profile_ids"
        const val ACTION_CONNECT = "com.mqttmobile.app.action.CONNECT"
        const val ACTION_STOP = "com.mqttmobile.app.action.STOP"
        const val EXTRA_PROFILE_ID = "profile_id"

        fun activeProfileId(context: Context): String? = activeProfileIds(context).firstOrNull()

        fun activeProfileIds(context: Context): Set<String> {
            val preferences = context.getSharedPreferences(RUNTIME_PREFERENCES, MODE_PRIVATE)
            val current = preferences.getStringSet(KEY_PROFILE_IDS, null)
            if (current != null) return current
            return preferences.getString(KEY_PROFILE_ID, null)?.let(::setOf).orEmpty()
        }

        @SuppressLint("BatteryLife")
        fun openBatteryOptimizationSettings(context: Context) {
            // The direct Android confirmation dialog follows the device language. Open the
            // settings list instead so the app can explain this step in Chinese first.
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        fun start(context: Context, profileId: String) {
            val intent = Intent(context, MqttKeepAliveService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_PROFILE_ID, profileId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context, profileId: String? = null) {
            val intent = Intent(context, MqttKeepAliveService::class.java).setAction(ACTION_STOP)
            if (!profileId.isNullOrBlank()) intent.putExtra(EXTRA_PROFILE_ID, profileId)
            context.startService(intent)
        }
    }

    private fun activeProfileIds(): Set<String> = Companion.activeProfileIds(this)

    private fun addActiveProfile(profileId: String) {
        val ids = activeProfileIds().toMutableSet().apply { add(profileId) }
        runtimePreferences.edit().putStringSet(KEY_PROFILE_IDS, ids).remove(KEY_PROFILE_ID).apply()
    }

    private fun removeActiveProfile(profileId: String) {
        val ids = activeProfileIds().toMutableSet().apply { remove(profileId) }
        runtimePreferences.edit().putStringSet(KEY_PROFILE_IDS, ids).remove(KEY_PROFILE_ID).apply()
    }

    private fun clearActiveProfiles() {
        runtimePreferences.edit().remove(KEY_PROFILE_IDS).remove(KEY_PROFILE_ID).apply()
    }
}
