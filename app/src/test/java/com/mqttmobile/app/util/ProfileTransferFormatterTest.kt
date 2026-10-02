package com.mqttmobile.app.util

import com.mqttmobile.app.data.model.BrokerProfile
import com.mqttmobile.app.data.model.MqttVersion
import com.mqttmobile.app.data.model.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTransferFormatterTest {
    @Test
    fun exportsAndParsesProfilesWithoutCredentials() {
        val profile = BrokerProfile(
            name = "现场 Broker",
            host = "broker.example.com",
            port = 8883,
            mqttVersion = MqttVersion.MQTT_5,
            username = "operator",
            clientPrivateKeyPem = "PRIVATE-KEY-MUST-NOT-EXPORT"
        )
        val subscription = Subscription(
            profileId = profile.id,
            topicFilter = "device/+/report",
            qos = 1,
            noLocal = true,
            retainAsPublished = true,
            retainHandling = 2
        )

        val raw = ProfileTransferFormatter.export(listOf(profile), listOf(subscription))
        assertTrue(raw.contains("\"credentialsIncluded\": false"))
        assertFalse(raw.contains("PRIVATE-KEY-MUST-NOT-EXPORT"))

        val imported = ProfileTransferFormatter.parse(raw).single()
        assertEquals(profile.name, imported.profile.name)
        assertEquals(profile.host, imported.profile.host)
        assertEquals(profile.mqttVersion, imported.profile.mqttVersion)
        assertEquals(1, imported.subscriptions.size)
        assertEquals(subscription.topicFilter, imported.subscriptions.single().topicFilter)
        assertEquals(subscription.retainHandling, imported.subscriptions.single().retainHandling)
        assertEquals(imported.profile.id, imported.subscriptions.single().profileId)
    }
}
