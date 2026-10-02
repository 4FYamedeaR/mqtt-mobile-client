package com.mqttmobile.app

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.mqttmobile.app.data.mqtt.HiveMqttRepository
import com.mqttmobile.app.data.mqtt.MqttRepository
import com.mqttmobile.app.service.MqttKeepAliveService

class MqttApplication : Application() {
    val mqttRepository: MqttRepository by lazy { HiveMqttRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // The foreground service uses START_STICKY, but a process crash can happen before
        // Android recreates it. Rehydrate the user's active profiles when the app process
        // comes back so a crash or force-stop followed by a normal launch can recover MQTT.
        if (MqttKeepAliveService.activeProfileIds(this).isNotEmpty()) {
            runCatching {
                ContextCompat.startForegroundService(this, Intent(this, MqttKeepAliveService::class.java))
            }
        }
    }
}
