package com.mqttmobile.app.util

import com.mqttmobile.app.data.model.BrokerProfile
import com.mqttmobile.app.data.model.MqttVersion
import com.mqttmobile.app.data.model.Subscription
import org.json.JSONArray
import org.json.JSONObject

data class ImportedProfileConfig(
    val profile: BrokerProfile,
    val subscriptions: List<Subscription>
)

object ProfileTransferFormatter {
    fun export(profiles: List<BrokerProfile>, subscriptions: List<Subscription>): String {
        val subscriptionsByProfile = subscriptions.groupBy { it.profileId }
        return JSONObject().apply {
            put("version", 1)
            put("profiles", JSONArray().apply {
                profiles.forEach { profile ->
                    put(profileJson(profile).apply {
                        put("subscriptions", JSONArray().apply {
                            subscriptionsByProfile[profile.id].orEmpty().forEach { subscription ->
                                put(subscriptionJson(subscription))
                            }
                        })
                    })
                }
            })
            put("credentialsIncluded", false)
        }.toString(2)
    }

    fun parse(raw: String): List<ImportedProfileConfig> {
        val root = JSONObject(raw)
        val profiles = root.optJSONArray("profiles") ?: JSONArray()
        return buildList(profiles.length()) {
            for (index in 0 until profiles.length()) {
                val item = profiles.getJSONObject(index)
                val profile = BrokerProfile(
                    name = item.optString("name", "导入配置"),
                    host = item.getString("host"),
                    port = item.optInt("port", 1883),
                    mqttVersion = runCatching {
                        MqttVersion.valueOf(item.optString("mqttVersion", MqttVersion.MQTT_5.name))
                    }.getOrDefault(MqttVersion.MQTT_5),
                    clientId = item.optString("clientId").ifBlank { com.mqttmobile.app.data.model.newClientId() },
                    username = item.optString("username").ifBlank { null },
                    tlsEnabled = item.optBoolean("tlsEnabled", false),
                    caCertificatePem = item.optString("caCertificatePem").ifBlank { null },
                    clientCertificatePem = item.optString("clientCertificatePem").ifBlank { null },
                    keepAliveSeconds = item.optInt("keepAliveSeconds", 60),
                    cleanStart = item.optBoolean("cleanStart", true),
                    autoReconnect = item.optBoolean("autoReconnect", true)
                )
                val subscriptionArray = item.optJSONArray("subscriptions") ?: JSONArray()
                val subscriptions = buildList(subscriptionArray.length()) {
                    for (subscriptionIndex in 0 until subscriptionArray.length()) {
                        val subscription = subscriptionArray.getJSONObject(subscriptionIndex)
                        add(
                            Subscription(
                                profileId = profile.id,
                                topicFilter = subscription.getString("topicFilter"),
                                qos = subscription.optInt("qos", 0),
                                noLocal = subscription.optBoolean("noLocal", false),
                                retainAsPublished = subscription.optBoolean("retainAsPublished", false),
                                retainHandling = subscription.optInt("retainHandling", 0).coerceIn(0, 2)
                            )
                        )
                    }
                }
                add(ImportedProfileConfig(profile, subscriptions))
            }
        }
    }

    private fun profileJson(profile: BrokerProfile) = JSONObject().apply {
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
    }

    private fun subscriptionJson(subscription: Subscription) = JSONObject().apply {
        put("topicFilter", subscription.topicFilter)
        put("qos", subscription.qos)
        put("noLocal", subscription.noLocal)
        put("retainAsPublished", subscription.retainAsPublished)
        put("retainHandling", subscription.retainHandling)
    }
}
