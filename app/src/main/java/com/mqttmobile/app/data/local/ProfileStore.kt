package com.mqttmobile.app.data.local

import android.content.Context
import com.mqttmobile.app.data.model.BrokerProfile
import com.mqttmobile.app.data.model.MqttVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class ProfileStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<BrokerProfile>> = _profiles.asStateFlow()

    fun upsert(profile: BrokerProfile) {
        val next = _profiles.value.filterNot { it.id == profile.id } + profile.copy(updatedAt = System.currentTimeMillis())
        _profiles.value = next
        persist(next)
    }

    fun delete(profileId: String) {
        val next = _profiles.value.filterNot { it.id == profileId }
        _profiles.value = next
        persist(next)
    }

    private fun load(): List<BrokerProfile> {
        val raw = preferences.getString(PROFILES_KEY, null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            buildList(json.length()) {
                for (index in 0 until json.length()) {
                    val item = json.getJSONObject(index)
                    add(
                        BrokerProfile(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            host = item.getString("host"),
                            port = item.getInt("port"),
                            mqttVersion = MqttVersion.valueOf(item.optString("mqttVersion", MqttVersion.MQTT_5.name)),
                            clientId = item.getString("clientId"),
                            username = item.optString("username").ifBlank { null },
                            tlsEnabled = item.optBoolean("tlsEnabled", false),
                            caCertificatePem = item.optString("caCertificatePem").ifBlank { null },
                            clientCertificatePem = item.optString("clientCertificatePem").ifBlank { null },
                            keepAliveSeconds = item.optInt("keepAliveSeconds", 60),
                            cleanStart = item.optBoolean("cleanStart", true),
                            autoReconnect = item.optBoolean("autoReconnect", true),
                            updatedAt = item.optLong("updatedAt", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(profiles: List<BrokerProfile>) {
        val json = JSONArray()
        profiles.forEach { profile ->
            json.put(
                JSONObject().apply {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("host", profile.host)
                    put("port", profile.port)
                    put("mqttVersion", profile.mqttVersion.name)
                    put("clientId", profile.clientId)
                    put("username", profile.username ?: "")
                    put("tlsEnabled", profile.tlsEnabled)
                    put("caCertificatePem", profile.caCertificatePem ?: "")
                    put("clientCertificatePem", profile.clientCertificatePem ?: "")
                    put("keepAliveSeconds", profile.keepAliveSeconds)
                    put("cleanStart", profile.cleanStart)
                    put("autoReconnect", profile.autoReconnect)
                    put("updatedAt", profile.updatedAt)
                }
            )
        }
        preferences.edit().putString(PROFILES_KEY, json.toString()).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "mqtt_profiles"
        const val PROFILES_KEY = "profiles"
    }
}
