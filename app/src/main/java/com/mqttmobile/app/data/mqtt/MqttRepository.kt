package com.mqttmobile.app.data.mqtt

import android.content.Context
import android.util.Base64
import com.mqttmobile.app.data.local.MessageStore
import com.mqttmobile.app.data.model.BrokerProfile
import com.mqttmobile.app.data.model.ConnectionLog
import com.mqttmobile.app.data.model.ConnectionStatus
import com.mqttmobile.app.data.model.MessageDirection
import com.mqttmobile.app.data.model.MqttMessageRecord
import com.mqttmobile.app.data.model.PublishRequest
import com.mqttmobile.app.data.model.Subscription
import com.mqttmobile.app.data.model.hasCustomTlsMaterial
import com.hivemq.client.mqtt.MqttClientSslConfig
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.exceptions.Mqtt3ConnAckException
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5ConnAckException
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5RetainHandling
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.io.ByteArrayInputStream
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Optional
import java.util.OptionalDouble
import java.util.OptionalInt
import java.util.OptionalLong
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManagerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed interface MqttEvent {
    data class State(val profileId: String, val status: ConnectionStatus) : MqttEvent
    data class Message(val message: MqttMessageRecord) : MqttEvent
    data class Log(val log: ConnectionLog) : MqttEvent
}

interface MqttRepository {
    val events: Flow<MqttEvent>
    val connectionStatus: StateFlow<ConnectionStatus>
    val connectionStatuses: StateFlow<Map<String, ConnectionStatus>>
    fun messageLimit(): Int
    fun setMessageLimit(limit: Int)
    fun historyForProfile(profileId: String): List<MqttMessageRecord>
    fun clearHistory(profileId: String)
    suspend fun connect(profile: BrokerProfile, password: String?, subscriptions: List<Subscription> = emptyList())
    suspend fun disconnect(profileId: String? = null)
    suspend fun subscribe(subscription: Subscription)
    suspend fun unsubscribe(subscription: Subscription)
    suspend fun publish(request: PublishRequest)
}

class HiveMqttRepository(context: Context) : MqttRepository {
    private val messageStore = MessageStore(context)
    private val _events = MutableSharedFlow<MqttEvent>(extraBufferCapacity = 128)
    override val events: Flow<MqttEvent> = _events.asSharedFlow()
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    private val _connectionStatuses = MutableStateFlow<Map<String, ConnectionStatus>>(emptyMap())
    override val connectionStatuses: StateFlow<Map<String, ConnectionStatus>> = _connectionStatuses.asStateFlow()

    override fun historyForProfile(profileId: String): List<MqttMessageRecord> = messageStore.forProfile(profileId)

    override fun clearHistory(profileId: String) = messageStore.clear(profileId)

    override fun messageLimit(): Int = messageStore.maxPerProfile()

    override fun setMessageLimit(limit: Int) = messageStore.setMaxPerProfile(limit)

    private val profiles = ConcurrentHashMap<String, BrokerProfile>()
    private val mqtt3Clients = ConcurrentHashMap<String, Mqtt3AsyncClient>()
    private val mqtt5Clients = ConcurrentHashMap<String, Mqtt5AsyncClient>()
    private val connectionGenerations = ConcurrentHashMap<String, Long>()
    private val manualDisconnects = ConcurrentHashMap.newKeySet<String>()
    private val activeSubscriptions = ConcurrentHashMap<String, MutableMap<String, Subscription>>()

    override suspend fun connect(profile: BrokerProfile, password: String?, subscriptions: List<Subscription>) {
        manualDisconnects.add(profile.id)
        val generation = (connectionGenerations[profile.id] ?: 0L) + 1L
        connectionGenerations[profile.id] = generation
        profiles[profile.id] = profile
        activeSubscriptions[profile.id] = subscriptions.associateBy { it.id }.toMutableMap()
        mqtt3Clients.remove(profile.id)?.disconnect()
        mqtt5Clients.remove(profile.id)?.disconnect()
        manualDisconnects.remove(profile.id)
        emitState(profile, ConnectionStatus.CONNECTING)
        emitLog(
            profile,
            "开始连接 | 地址=${profile.host}:${profile.port} | 协议=${profile.mqttVersion.label} | " +
                "TLS=${if (profile.tlsEnabled) "开启" else "关闭"} | Client ID=${profile.clientId} | " +
                "用户名=${if (profile.username.isNullOrBlank()) "未配置" else "已配置"} | " +
                "Keep Alive=${profile.keepAliveSeconds}s | Clean Start=${profile.cleanStart} | " +
                "自动重连=${if (profile.autoReconnect) "开启" else "关闭"} | 待恢复订阅=${subscriptions.size}"
        )
        runCatching {
            when (profile.mqttVersion) {
                com.mqttmobile.app.data.model.MqttVersion.MQTT_3_1_1 -> connectMqtt3(profile, password, generation)
                com.mqttmobile.app.data.model.MqttVersion.MQTT_5 -> connectMqtt5(profile, password, generation)
            }
        }.onFailure { error ->
            if (!isCurrentGeneration(profile.id, generation)) return@onFailure
            emitState(profile, ConnectionStatus.FAILED)
            emitLog(profile, "连接初始化失败 | 原因=${error.connectionDescription()}", com.mqttmobile.app.data.model.LogLevel.ERROR)
        }
    }

    override suspend fun disconnect(profileId: String?) {
        val ids = profileId?.let(::listOf) ?: profiles.keys.toList()
        ids.forEach { id ->
            connectionGenerations[id] = (connectionGenerations[id] ?: 0L) + 1L
            manualDisconnects.add(id)
            val profile = profiles[id]
            emitState(id, ConnectionStatus.USER_DISCONNECTED)
            if (profile != null) emitLog(profile, "用户主动断开连接 | 自动重连已停止")
            mqtt3Clients.remove(id)?.disconnect()
            mqtt5Clients.remove(id)?.disconnect()
        }
    }

    override suspend fun subscribe(subscription: Subscription) {
        val profile = profiles[subscription.profileId] ?: return
        activeSubscriptions.getOrPut(profile.id) { ConcurrentHashMap() }[subscription.id] = subscription
        subscribeNow(profile, subscription)
    }

    private fun subscribeNow(profile: BrokerProfile, subscription: Subscription) {
        val qos = subscription.qos.toMqttQos()
        emitLog(profile, "发送订阅请求 | Topic Filter=${subscription.topicFilter} | QoS=${subscription.qos}")
        when {
            mqtt3Clients[profile.id] != null -> mqtt3Clients[profile.id]!!.subscribeWith()
                .topicFilter(subscription.topicFilter)
                .qos(qos)
                .callback { publish -> onIncoming(profile, publish) }
                .send()
                .whenComplete { result, error ->
                    if (error == null) emitLog(profile, "订阅成功 | Topic Filter=${subscription.topicFilter} | Broker 响应=${result.ackDescription()}", com.mqttmobile.app.data.model.LogLevel.SUCCESS)
                    else emitLog(profile, "订阅失败 | Topic Filter=${subscription.topicFilter} | 原因=${error.connectionDescription()}", com.mqttmobile.app.data.model.LogLevel.ERROR)
                }

            mqtt5Clients[profile.id] != null -> mqtt5Clients[profile.id]!!.subscribeWith()
                .topicFilter(subscription.topicFilter)
                .qos(qos)
                .noLocal(subscription.noLocal)
                .retainAsPublished(subscription.retainAsPublished)
                .retainHandling(Mqtt5RetainHandling.fromCode(subscription.retainHandling.coerceIn(0, 2)) ?: Mqtt5RetainHandling.SEND)
                .callback { publish -> onIncoming(profile, publish) }
                .send()
                .whenComplete { result, error ->
                    if (error == null) emitLog(profile, "订阅成功 | Topic Filter=${subscription.topicFilter} | Broker 响应=${result.ackDescription()}", com.mqttmobile.app.data.model.LogLevel.SUCCESS)
                    else emitLog(profile, "订阅失败 | Topic Filter=${subscription.topicFilter} | 原因=${error.connectionDescription()}", com.mqttmobile.app.data.model.LogLevel.ERROR)
                }

            else -> emitLog(profile, "订阅已暂存 | 当前未连接，连接成功后发送 | Topic Filter=${subscription.topicFilter}", com.mqttmobile.app.data.model.LogLevel.WARNING)
        }
    }

    override suspend fun unsubscribe(subscription: Subscription) {
        val profile = profiles[subscription.profileId] ?: return
        activeSubscriptions[profile.id]?.remove(subscription.id)
        when {
            mqtt3Clients[profile.id] != null -> mqtt3Clients[profile.id]!!.unsubscribeWith().topicFilter(subscription.topicFilter).send()
                .whenComplete { result, error -> emitLog(profile, if (error == null) "取消订阅成功 | Topic Filter=${subscription.topicFilter} | Broker 响应=${result.ackDescription()}" else "取消订阅失败 | Topic Filter=${subscription.topicFilter} | 原因=${error.connectionDescription()}", if (error == null) com.mqttmobile.app.data.model.LogLevel.INFO else com.mqttmobile.app.data.model.LogLevel.ERROR) }
            mqtt5Clients[profile.id] != null -> mqtt5Clients[profile.id]!!.unsubscribeWith().topicFilter(subscription.topicFilter).send()
                .whenComplete { result, error -> emitLog(profile, if (error == null) "取消订阅成功 | Topic Filter=${subscription.topicFilter} | Broker 响应=${result.ackDescription()}" else "取消订阅失败 | Topic Filter=${subscription.topicFilter} | 原因=${error.connectionDescription()}", if (error == null) com.mqttmobile.app.data.model.LogLevel.INFO else com.mqttmobile.app.data.model.LogLevel.ERROR) }
            else -> emitLog(profile, "取消订阅已记录 | 当前未连接 | Topic Filter=${subscription.topicFilter}")
        }
    }

    override suspend fun publish(request: PublishRequest) {
        val profile = profiles[request.profileId] ?: return
        val qos = request.qos.toMqttQos()
        when {
            mqtt3Clients[profile.id] != null -> mqtt3Clients[profile.id]!!.publishWith()
                .topic(request.topic)
                .payload(request.payload)
                .qos(qos)
                .retain(request.retain)
                .send()
                .whenComplete { result, error -> onPublishResult(request, result, error) }
            mqtt5Clients[profile.id] != null -> {
                val publishBuilder = mqtt5Clients[profile.id]!!.publishWith()
                    .topic(request.topic)
                    .payload(request.payload)
                    .qos(qos)
                    .retain(request.retain)
                request.messageExpiryIntervalSeconds
                    ?.coerceIn(0L, 4_294_967_295L)
                    ?.let(publishBuilder::messageExpiryInterval)
                request.contentType?.trim()?.takeIf(String::isNotEmpty)?.let(publishBuilder::contentType)
                request.responseTopic?.trim()?.takeIf(String::isNotEmpty)?.let(publishBuilder::responseTopic)
                if (request.format == com.mqttmobile.app.data.model.PayloadFormat.JSON || request.format == com.mqttmobile.app.data.model.PayloadFormat.TEXT) {
                    publishBuilder.payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
                }
                publishBuilder.send().whenComplete { result, error -> onPublishResult(request, result, error) }
            }
            else -> emitLog(profile, "发布失败：当前未连接", com.mqttmobile.app.data.model.LogLevel.ERROR)
        }
    }

    private fun connectMqtt3(profile: BrokerProfile, password: String?, generation: Long) {
        var builder = MqttClient.builder()
            .useMqttVersion3()
            .identifier(profile.clientId)
            .serverHost(profile.host)
            .serverPort(profile.port)
            .addConnectedListener { context -> onConnected(profile, generation, context) }
            .addDisconnectedListener { context -> onDisconnected(profile, generation, context) }
        if (profile.tlsEnabled) builder = if (profile.hasCustomTlsMaterial()) {
            builder.useSsl(buildSslConfig(profile))
        } else {
            builder.useSslWithDefaultConfig()
        }
        if (profile.autoReconnect) builder = builder.automaticReconnectWithDefaultConfig()
        val client = builder.buildAsync()
        mqtt3Clients[profile.id] = client
        var connectBuilder = client.connectWith()
            .keepAlive(profile.keepAliveSeconds)
            .cleanSession(profile.cleanStart)
        val future = if (!profile.username.isNullOrBlank()) {
            connectBuilder.simpleAuth()
                .username(profile.username!!)
                .password((password ?: "").toByteArray())
                .applySimpleAuth()
                .send()
        } else {
            connectBuilder.send()
        }
        future.whenComplete { _, error -> if (error != null) onConnectionAttemptFailed(profile, generation, error) }
    }

    private fun connectMqtt5(profile: BrokerProfile, password: String?, generation: Long) {
        var builder = MqttClient.builder()
            .useMqttVersion5()
            .identifier(profile.clientId)
            .serverHost(profile.host)
            .serverPort(profile.port)
            .addConnectedListener { context -> onConnected(profile, generation, context) }
            .addDisconnectedListener { context -> onDisconnected(profile, generation, context) }
        if (profile.tlsEnabled) builder = if (profile.hasCustomTlsMaterial()) {
            builder.useSsl(buildSslConfig(profile))
        } else {
            builder.useSslWithDefaultConfig()
        }
        if (profile.autoReconnect) builder = builder.automaticReconnectWithDefaultConfig()
        val client = builder.buildAsync()
        mqtt5Clients[profile.id] = client
        var connectBuilder = client.connectWith()
            .keepAlive(profile.keepAliveSeconds)
            .cleanStart(profile.cleanStart)
        val future = if (!profile.username.isNullOrBlank()) {
            connectBuilder.simpleAuth()
                .username(profile.username!!)
                .password((password ?: "").toByteArray())
                .applySimpleAuth()
                .send()
        } else {
            connectBuilder.send()
        }
        future.whenComplete { _, error -> if (error != null) onConnectionAttemptFailed(profile, generation, error) }
    }

    private fun onConnected(profile: BrokerProfile, generation: Long, context: Any) {
        if (!isCurrentGeneration(profile.id, generation)) return
        emitState(profile, ConnectionStatus.CONNECTED)
        emitLog(
            profile,
            "连接成功 | CONNACK=${context.readProperty("getConnAck").ackDescription()} | " +
                "Client ID=${profile.clientId} | 恢复订阅=${activeSubscriptions[profile.id]?.size ?: 0}",
            com.mqttmobile.app.data.model.LogLevel.SUCCESS
        )
        activeSubscriptions[profile.id]?.takeIf { it.isNotEmpty() }?.let { subscriptions ->
            emitLog(profile, "开始恢复 ${subscriptions.size} 个订阅")
            subscriptions.values.forEach { subscribeNow(profile, it) }
        }
    }

    private fun onConnectionAttemptFailed(profile: BrokerProfile, generation: Long, error: Throwable) {
        if (!isCurrentGeneration(profile.id, generation)) return
        val terminal = error.isNonRetryableMqttFailure()
        val status = if (profile.autoReconnect && !manualDisconnects.contains(profile.id) && !terminal) ConnectionStatus.RECONNECTING else ConnectionStatus.FAILED
        emitState(profile, status)
        emitLog(
            profile,
            "连接尝试失败 | 地址=${profile.host}:${profile.port} | 原因=${error.connectionDescription()} | " +
                "结果状态=${status.label} | 自动重连=${if (profile.autoReconnect && !terminal) "开启" else "停止"}",
            com.mqttmobile.app.data.model.LogLevel.ERROR
        )
    }

    private fun onDisconnected(profile: BrokerProfile, generation: Long, context: MqttClientDisconnectedContext) {
        if (!isCurrentGeneration(profile.id, generation)) return
        if (manualDisconnects.contains(profile.id)) return
        val cause = context.cause
        val terminal = cause.isNonRetryableMqttFailure() || cause.isClientIdTakenOver()
        if (terminal) context.reconnector.reconnect(false)
        val reconnecting = profile.autoReconnect && !terminal
        emitState(profile, if (reconnecting) ConnectionStatus.RECONNECTING else ConnectionStatus.FAILED)
        val reconnectDetails = if (reconnecting) {
            "自动重连=开启 | 尝试次数=${context.reconnector.attempts} | 下次延迟=${context.reconnector.getDelay(TimeUnit.SECONDS)}s"
        } else {
            "自动重连=停止"
        }
        emitLog(
            profile,
            "连接断开 | 来源=${context.source.logLabel()} | 原因=${cause.connectionDescription()} | $reconnectDetails",
            if (terminal) com.mqttmobile.app.data.model.LogLevel.ERROR else com.mqttmobile.app.data.model.LogLevel.WARNING
        )
    }

    private fun isCurrentGeneration(profileId: String, generation: Long): Boolean =
        connectionGenerations[profileId] == generation

    private fun onPublishResult(request: PublishRequest, result: Any?, error: Throwable?) {
        val profile = profiles[request.profileId] ?: return
        if (error != null) {
            emitLog(profile, "发布失败 | Topic=${request.topic} | Payload=${request.payload.size} bytes | 原因=${error.connectionDescription()}", com.mqttmobile.app.data.model.LogLevel.ERROR)
            return
        }
        emitLog(
            profile,
            "发布成功 | Topic=${request.topic} | Payload=${request.payload.size} bytes | QoS=${request.qos} | Retain=${request.retain} | " +
                "Expiry=${request.messageExpiryIntervalSeconds?.let { "${it}s" } ?: "未设置"} | " +
                "Content-Type=${request.contentType ?: "未设置"} | Response Topic=${request.responseTopic ?: "未设置"} | " +
                "Broker 响应=${result.ackDescription()}",
            com.mqttmobile.app.data.model.LogLevel.SUCCESS
        )
        emitMessage(
            MqttMessageRecord(
                profileId = request.profileId,
                direction = MessageDirection.PUBLISHED,
                topic = request.topic,
                payload = request.payload,
                qos = request.qos,
                retain = request.retain,
                duplicate = false,
                properties = buildMap {
                    request.messageExpiryIntervalSeconds?.let { put("Message Expiry Interval", "${it}s") }
                    request.contentType?.takeIf(String::isNotBlank)?.let { put("Content Type", it) }
                    request.responseTopic?.takeIf(String::isNotBlank)?.let { put("Response Topic", it) }
                    if (request.format == com.mqttmobile.app.data.model.PayloadFormat.JSON || request.format == com.mqttmobile.app.data.model.PayloadFormat.TEXT) {
                        put("Payload Format Indicator", "UTF-8")
                    }
                }
            )
        )
    }

    private fun onIncoming(profile: BrokerProfile, publish: Any) {
        val topic = publish.readProperty("getTopic", "topic")?.toString() ?: return
        val payload = publish.readProperty("getPayloadAsBytes", "payloadAsBytes", "getPayload")?.toByteArrayValue() ?: ByteArray(0)
        val qosName = publish.readProperty("getQos", "qos")?.toString().orEmpty()
        val qos = when {
            qosName.contains("EXACTLY") -> 2
            qosName.contains("LEAST") -> 1
            else -> 0
        }
        val retain = publish.readProperty("isRetain", "getRetain", "retain") as? Boolean ?: false
        val duplicate = publish.readProperty("isDuplicate", "getDuplicate", "duplicate") as? Boolean ?: false
        val properties = extractPublishProperties(publish)
        emitLog(profile, "收到消息 | Topic=$topic | Payload=${payload.size} bytes | QoS=$qos | Retain=$retain | Duplicate=$duplicate")
        emitMessage(
            MqttMessageRecord(
                profileId = profile.id,
                direction = MessageDirection.RECEIVED,
                topic = topic,
                payload = payload,
                qos = qos,
                retain = retain,
                duplicate = duplicate,
                properties = properties
            )
        )
    }

    private fun emitState(profile: BrokerProfile, status: ConnectionStatus) = emitState(profile.id, status)

    private fun emitState(profileId: String, status: ConnectionStatus) {
        _connectionStatus.value = status
        _connectionStatuses.value = _connectionStatuses.value.toMutableMap().apply { put(profileId, status) }
        _events.tryEmit(MqttEvent.State(profileId, status))
    }

    private fun emitMessage(message: MqttMessageRecord) {
        messageStore.append(message)
        _events.tryEmit(MqttEvent.Message(message))
    }

    private fun emitLog(profile: BrokerProfile, message: String, level: com.mqttmobile.app.data.model.LogLevel = com.mqttmobile.app.data.model.LogLevel.INFO) {
        _events.tryEmit(MqttEvent.Log(ConnectionLog(profileId = profile.id, message = message, level = level)))
    }

    private fun extractPublishProperties(publish: Any): Map<String, String> {
        val properties = linkedMapOf<String, String>()
        fun add(label: String, vararg names: String) {
            val value = publish.readProperty(*names)?.unwrapOptional() ?: return
            val text = when (value) {
                is ByteArray -> Base64.encodeToString(value, Base64.NO_WRAP)
                is ByteBuffer -> Base64.encodeToString(value.toByteArrayValue(), Base64.NO_WRAP)
                else -> value.toString()
            }
            if (text.isNotBlank() && text != "null") properties[label] = text
        }
        add("Payload Format Indicator", "getPayloadFormatIndicator", "payloadFormatIndicator")
        add("Message Expiry Interval", "getMessageExpiryInterval", "messageExpiryInterval")
        add("Topic Alias", "getTopicAlias", "topicAlias")
        add("Response Topic", "getResponseTopic", "responseTopic")
        add("Correlation Data (Base64)", "getCorrelationData", "correlationData")
        add("Content Type", "getContentType", "contentType")
        add("Subscription Identifiers", "getSubscriptionIdentifiers", "subscriptionIdentifiers")
        add("User Properties", "getUserProperties", "userProperties")
        return properties
    }

    private fun Any.unwrapOptional(): Any? = when (this) {
        is Optional<*> -> orElse(null)
        is OptionalLong -> if (isPresent) asLong else null
        is OptionalInt -> if (isPresent) asInt else null
        is OptionalDouble -> if (isPresent) asDouble else null
        else -> this
    }

    private fun buildSslConfig(profile: BrokerProfile): MqttClientSslConfig {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val trustManagerFactory = profile.caCertificatePem?.takeIf(String::isNotBlank)?.let { pem ->
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
            keyStore.setCertificateEntry("custom-ca", certificateFactory.generateCertificate(pem.toByteArray().inputStream()))
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keyStore) }
        }
        val keyManagerFactory = if (!profile.clientCertificatePem.isNullOrBlank() && !profile.clientPrivateKeyPem.isNullOrBlank()) {
            val privateKey = parsePrivateKey(profile.clientPrivateKeyPem)
            val certificate = certificateFactory.generateCertificate(profile.clientCertificatePem.toByteArray().inputStream())
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setKeyEntry("mqtt-client", privateKey, CharArray(0), arrayOf(certificate))
            }
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, CharArray(0)) }
        } else {
            null
        }
        return MqttClientSslConfig.builder().apply {
            trustManagerFactory?.let(::trustManagerFactory)
            keyManagerFactory?.let(::keyManagerFactory)
        }.build()
    }

    private fun parsePrivateKey(pem: String): PrivateKey {
        val encoded = pem
            .replace(Regex("-----BEGIN (RSA )?PRIVATE KEY-----"), "")
            .replace(Regex("-----END (RSA )?PRIVATE KEY-----"), "")
            .replace(Regex("\\s"), "")
        val keyBytes = Base64.decode(encoded, Base64.DEFAULT)
        return runCatching {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        }.recoverCatching {
            KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        }.getOrThrow()
    }
}

private fun Int.toMqttQos(): MqttQos = when (this.coerceIn(0, 2)) {
    1 -> MqttQos.AT_LEAST_ONCE
    2 -> MqttQos.EXACTLY_ONCE
    else -> MqttQos.AT_MOST_ONCE
}

private fun Throwable.connectionDescription(): String {
    if (isClientIdTakenOver()) return "Client ID 重复：另一个客户端已使用相同 Client ID，当前连接被 Broker 踢下线"
    val connAck = generateSequence(this) { it.cause }
        .firstOrNull { it is Mqtt5ConnAckException || it is Mqtt3ConnAckException }
    return when (connAck) {
        is Mqtt5ConnAckException -> {
            val message = connAck.mqttMessage
            "MQTT 5 CONNACK ${message.reasonCode}${message.reasonString.map { ": ${it}" }.orElse("")}"
        }
        is Mqtt3ConnAckException -> "MQTT 3 CONNACK ${connAck.mqttMessage.returnCode}"
        else -> {
            val cause = generateSequence(this) { it.cause }.firstOrNull {
                it is UnknownHostException ||
                    it is ConnectException ||
                    it is SocketTimeoutException ||
                    it is SSLPeerUnverifiedException ||
                    it is SSLHandshakeException ||
                    it is java.security.cert.CertificateException ||
                    it is SocketException ||
                    it is SecurityException
            }
            when (cause) {
                is UnknownHostException -> "DNS 解析失败：无法找到 Broker 主机"
                is ConnectException -> "TCP 连接失败：Broker 未监听或网络不可达"
                is SocketTimeoutException -> "网络连接超时：请检查地址、防火墙和网络"
                is SSLPeerUnverifiedException -> "TLS 主机名校验失败：证书与 Broker 地址不匹配${cause.diagnosticSuffix()}"
                is SSLHandshakeException -> "TLS 握手失败：请检查 CA、证书有效期和协议配置${cause.diagnosticSuffix()}"
                is java.security.cert.CertificateException -> "TLS 证书解析失败：请确认导入的是 PEM/X.509 证书${cause.diagnosticSuffix()}"
                is SocketException -> "网络连接异常：${cause.message ?: "连接被关闭"}"
                is SecurityException -> "系统安全策略阻止了网络或证书操作"
                else -> generateSequence(this) { it.cause }
                    .mapNotNull { it.message?.takeIf(String::isNotBlank) }
                    .firstOrNull()
                    ?: javaClass.simpleName
            }
        }
    }
}

private fun Throwable.isClientIdTakenOver(): Boolean = generateSequence(this) { it.cause }
    .flatMap { throwable ->
        sequenceOf(
            throwable.javaClass.simpleName,
            throwable.message.orEmpty(),
            throwable.toString()
        ).asSequence()
    }
    .any { text ->
        text.contains("SESSION_TAKEN_OVER", ignoreCase = true) ||
            text.contains("same client identifier", ignoreCase = true) ||
            text.contains("client identifier has connected", ignoreCase = true)
    }

private fun Throwable.isNonRetryableMqttFailure(): Boolean {
    val connAck = generateSequence(this) { it.cause }
        .firstOrNull { it is Mqtt5ConnAckException || it is Mqtt3ConnAckException }
    return when (connAck) {
        is Mqtt5ConnAckException -> connAck.mqttMessage.reasonCode in setOf(
            Mqtt5ConnAckReasonCode.BAD_USER_NAME_OR_PASSWORD,
            Mqtt5ConnAckReasonCode.NOT_AUTHORIZED,
            Mqtt5ConnAckReasonCode.CLIENT_IDENTIFIER_NOT_VALID,
            Mqtt5ConnAckReasonCode.UNSUPPORTED_PROTOCOL_VERSION,
            Mqtt5ConnAckReasonCode.BAD_AUTHENTICATION_METHOD,
            Mqtt5ConnAckReasonCode.PROTOCOL_ERROR,
            Mqtt5ConnAckReasonCode.MALFORMED_PACKET
        )
        is Mqtt3ConnAckException -> connAck.mqttMessage.returnCode in setOf(
            Mqtt3ConnAckReturnCode.BAD_USER_NAME_OR_PASSWORD,
            Mqtt3ConnAckReturnCode.NOT_AUTHORIZED,
            Mqtt3ConnAckReturnCode.IDENTIFIER_REJECTED,
            Mqtt3ConnAckReturnCode.UNSUPPORTED_PROTOCOL_VERSION
        )
        else -> false
    }
}

private fun Throwable.safeMessage(): String = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

private fun Throwable.diagnosticSuffix(): String = message
    ?.replace(Regex("\\s+"), " ")
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?.take(180)
    ?.let { "（$it）" }
    .orEmpty()

private fun Any?.ackDescription(): String {
    if (this == null) return "无 ACK（QoS 0）"
    val ack = readProperty("getPubAck", "getSubAck", "getUnsubAck") ?: this
    val reasonCodes = ack.readProperty("getReasonCodes", "getReturnCodes")
    val reasonCode = ack.readProperty("getReasonCode", "getReturnCode")
    val response = reasonCodes?.let { "返回码=$it" } ?: reasonCode?.let { "返回码=$it" } ?: "结果已返回"
    val sessionPresent = ack.readProperty("isSessionPresent")?.let { " | 会话存在=$it" }.orEmpty()
    return "${ack.javaClass.simpleName} | $response$sessionPresent"
}

private fun MqttDisconnectSource.logLabel(): String = when (this) {
    MqttDisconnectSource.USER -> "用户"
    MqttDisconnectSource.CLIENT -> "客户端"
    MqttDisconnectSource.SERVER -> "Broker"
}

private fun Any.readProperty(vararg names: String): Any? {
    for (name in names) {
        runCatching {
            javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }?.invoke(this)
        }.getOrNull()?.let { return it }
    }
    return null
}

private fun Any.toByteArrayValue(): ByteArray = when (this) {
    is ByteArray -> this
    is ByteBuffer -> duplicate().let { buffer -> ByteArray(buffer.remaining()).also(buffer::get) }
    else -> toString().toByteArray()
}
