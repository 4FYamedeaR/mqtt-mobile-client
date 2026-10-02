package com.mqttmobile.app.data.model

import java.util.UUID

enum class MqttVersion(val label: String) {
    MQTT_3_1_1("MQTT 3.1.1"),
    MQTT_5("MQTT 5.0")
}

enum class ConnectionStatus(val label: String) {
    DISCONNECTED("未连接"),
    CONNECTING("连接中"),
    CONNECTED("已连接"),
    RECONNECTING("重连中"),
    FAILED("连接失败"),
    USER_DISCONNECTED("已断开")
}

enum class MessageDirection(val label: String) {
    RECEIVED("已接收"),
    PUBLISHED("已发布")
}

enum class PayloadFormat(val label: String) {
    JSON("JSON"),
    TEXT("Plain Text"),
    HEX("Hex"),
    BASE64("Base64"),
    BINARY("Binary")
}

data class BrokerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int,
    val mqttVersion: MqttVersion = MqttVersion.MQTT_5,
    val clientId: String = newClientId(),
    val username: String? = null,
    val tlsEnabled: Boolean = false,
    val caCertificatePem: String? = null,
    val clientCertificatePem: String? = null,
    val clientPrivateKeyPem: String? = null,
    val keepAliveSeconds: Int = 60,
    val cleanStart: Boolean = true,
    val autoReconnect: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

fun BrokerProfile.hasCustomTlsMaterial(): Boolean =
    !caCertificatePem.isNullOrBlank() ||
        (!clientCertificatePem.isNullOrBlank() && !clientPrivateKeyPem.isNullOrBlank())

data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val topicFilter: String,
    val qos: Int = 0,
    val noLocal: Boolean = false,
    val retainAsPublished: Boolean = false,
    val retainHandling: Int = 0
)

data class MqttMessageRecord(
    val id: Long = System.nanoTime(),
    val profileId: String,
    val direction: MessageDirection,
    val topic: String,
    val payload: ByteArray,
    val qos: Int,
    val retain: Boolean,
    val duplicate: Boolean,
    val receivedAt: Long = System.currentTimeMillis(),
    val properties: Map<String, String> = emptyMap()
)

data class PublishRequest(
    val profileId: String,
    val topic: String,
    val payload: ByteArray,
    val qos: Int,
    val retain: Boolean,
    val format: PayloadFormat,
    val messageExpiryIntervalSeconds: Long? = null,
    val contentType: String? = null,
    val responseTopic: String? = null
)

data class ConnectionLog(
    val id: Long = System.nanoTime(),
    val profileId: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO,
    val createdAt: Long = System.currentTimeMillis()
)

enum class LogLevel(val label: String) {
    INFO("信息"),
    SUCCESS("成功"),
    WARNING("警告"),
    ERROR("错误")
}

fun newClientId(): String = "mqtt-mobile-${UUID.randomUUID().toString().take(8)}"
