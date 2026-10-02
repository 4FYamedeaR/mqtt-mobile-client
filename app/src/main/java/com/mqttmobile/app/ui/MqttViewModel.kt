package com.mqttmobile.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mqttmobile.app.MqttApplication
import com.mqttmobile.app.data.local.CredentialStore
import com.mqttmobile.app.data.local.AppSettingsStore
import com.mqttmobile.app.data.local.ProfileStore
import com.mqttmobile.app.data.local.SubscriptionStore
import com.mqttmobile.app.data.model.BrokerProfile
import com.mqttmobile.app.data.model.ConnectionLog
import com.mqttmobile.app.data.model.ConnectionStatus
import com.mqttmobile.app.data.model.MessageDirection
import com.mqttmobile.app.data.model.MqttMessageRecord
import com.mqttmobile.app.data.model.PublishRequest
import com.mqttmobile.app.data.model.Subscription
import com.mqttmobile.app.data.mqtt.MqttEvent
import com.mqttmobile.app.data.mqtt.MqttRepository
import com.mqttmobile.app.data.model.LogLevel
import com.mqttmobile.app.util.PayloadFormatter
import com.mqttmobile.app.util.ProfileTransferFormatter
import com.mqttmobile.app.util.TopicFilterMatcher
import com.mqttmobile.app.service.MqttKeepAliveService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MqttUiState(
    val profiles: List<BrokerProfile> = emptyList(),
    val activeProfileId: String? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectionStatuses: Map<String, ConnectionStatus> = emptyMap(),
    val messageLimit: Int = 1000,
    val logLimit: Int = 300,
    val defaultDisplayFormat: com.mqttmobile.app.data.model.PayloadFormat = com.mqttmobile.app.data.model.PayloadFormat.JSON,
    val messages: List<MqttMessageRecord> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val logs: List<ConnectionLog> = emptyList(),
    val searchQuery: String = "",
    val subscriptionTopicFilter: String? = null,
    val directionFilter: MessageDirection? = null,
    val jsonOnly: Boolean = false,
    val sortAscending: Boolean = false,
    val isPaused: Boolean = false,
    val snackbar: String? = null
) {
    val activeProfile: BrokerProfile? get() = profiles.firstOrNull { it.id == activeProfileId }
    val visibleMessages: List<MqttMessageRecord>
        get() = messages.filter { message ->
            (subscriptionTopicFilter?.let { TopicFilterMatcher.matches(message.topic, it) } ?: true) &&
                (searchQuery.isBlank() ||
                    message.topic.contains(searchQuery, ignoreCase = true) ||
                    message.payload.toString(Charsets.UTF_8).contains(searchQuery, ignoreCase = true)) &&
                (directionFilter == null || message.direction == directionFilter) &&
                (!jsonOnly || PayloadFormatter.prettyJson(message.payload) != null)
        }.let { filtered -> if (sortAscending) filtered.asReversed() else filtered }
}

class MqttViewModel(application: Application) : AndroidViewModel(application) {
    private val profileStore = ProfileStore(application)
    private val subscriptionStore = SubscriptionStore(application)
    private val credentialStore = CredentialStore(application)
    private val appSettingsStore = AppSettingsStore(application)
    private val repository: MqttRepository = (application as MqttApplication).mqttRepository

    private val runtimeProfileId = MqttKeepAliveService.activeProfileId(application)
    private val _state = MutableStateFlow(
        MqttUiState(
            activeProfileId = runtimeProfileId,
            connectionStatuses = repository.connectionStatuses.value,
            connectionStatus = runtimeProfileId?.let(repository.connectionStatuses.value::get) ?: ConnectionStatus.DISCONNECTED,
            messageLimit = repository.messageLimit(),
            logLimit = appSettingsStore.logLimit(),
            defaultDisplayFormat = appSettingsStore.defaultDisplayFormat(),
            subscriptions = runtimeProfileId?.let(subscriptionStore::forProfile).orEmpty(),
            messages = runtimeProfileId?.let(repository::historyForProfile).orEmpty()
        )
    )
    val state: StateFlow<MqttUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            profileStore.profiles.collect { profiles -> _state.update { it.copy(profiles = profiles) } }
        }
        viewModelScope.launch {
            repository.events.collect(::handleEvent)
        }
        viewModelScope.launch {
            repository.connectionStatuses.collect { statuses ->
                _state.update { current ->
                    current.copy(
                        connectionStatuses = statuses,
                        connectionStatus = current.activeProfileId?.let(statuses::get) ?: current.connectionStatus
                    )
                }
            }
        }
        viewModelScope.launch {
            appSettingsStore.defaultDisplayFormatFlow().collect { format ->
                _state.update { current -> current.copy(defaultDisplayFormat = format) }
            }
        }
        viewModelScope.launch {
            appSettingsStore.logLimitFlow().collect { limit ->
                _state.update { current -> current.copy(logLimit = limit, logs = current.logs.take(limit)) }
            }
        }
    }

    fun selectProfile(profileId: String) {
        _state.update {
            it.copy(
                activeProfileId = profileId,
                connectionStatus = repository.connectionStatuses.value[profileId] ?: ConnectionStatus.DISCONNECTED,
                connectionStatuses = repository.connectionStatuses.value,
                messages = repository.historyForProfile(profileId),
                subscriptions = subscriptionStore.forProfile(profileId),
                logs = emptyList(),
                searchQuery = "",
                subscriptionTopicFilter = null,
                directionFilter = null,
                jsonOnly = false,
                sortAscending = false,
                isPaused = false
            )
        }
    }

    fun saveProfile(profile: BrokerProfile, password: String, clientPrivateKeyPem: String = ""): Boolean {
        return try {
            if (password.isNotBlank()) credentialStore.put(profile.id, password)
            if (clientPrivateKeyPem.isNotBlank()) credentialStore.putPrivateKey(profile.id, clientPrivateKeyPem)
            profileStore.upsert(profile.copy(clientPrivateKeyPem = null))
            _state.update { it.copy(activeProfileId = profile.id, snackbar = "配置已保存") }
            true
        } catch (_: Exception) {
            showMessage("保存连接失败，请重试")
            false
        }
    }

    fun deleteProfile(profile: BrokerProfile) {
        if (_state.value.activeProfileId == profile.id) disconnect()
        credentialStore.delete(profile.id)
        credentialStore.deletePrivateKey(profile.id)
        subscriptionStore.deleteForProfile(profile.id)
        profileStore.delete(profile.id)
        _state.update { it.copy(activeProfileId = null, snackbar = "配置已删除") }
    }

    fun clearCredentials(profileId: String) {
        credentialStore.delete(profileId)
        credentialStore.deletePrivateKey(profileId)
        _state.update { it.copy(snackbar = "已清除该配置的密码和客户端私钥") }
    }

    fun exportProfiles(): String = ProfileTransferFormatter.export(
        profiles = profileStore.profiles.value,
        subscriptions = subscriptionStore.subscriptions.value
    )

    fun importProfiles(raw: String): Boolean {
        return runCatching {
            val imported = ProfileTransferFormatter.parse(raw)
            require(imported.isNotEmpty()) { "文件中没有连接配置" }
            imported.forEach { config ->
                profileStore.upsert(config.profile)
                config.subscriptions.forEach(subscriptionStore::upsert)
            }
            _state.update { it.copy(snackbar = "已导入 ${imported.size} 个连接配置，密码需重新填写") }
        }.onFailure {
            showMessage("导入失败：${it.message ?: "文件格式无效"}")
        }.isSuccess
    }

    fun connect(profile: BrokerProfile) {
        selectProfile(profile.id)
        val password = credentialStore.get(profile.id)
        if (!profile.username.isNullOrBlank() && password.isNullOrBlank()) {
            _state.update {
                it.copy(
                    connectionStatus = ConnectionStatus.FAILED,
                    snackbar = "此配置缺少密码，请编辑连接并重新保存密码"
                )
            }
            return
        }
        MqttKeepAliveService.start(getApplication(), profile.id)
    }

    fun disconnect() {
        _state.value.activeProfileId?.let { MqttKeepAliveService.stop(getApplication(), it) }
    }

    fun addSubscription(topicFilter: String, qos: Int, noLocal: Boolean = false, retainAsPublished: Boolean = false, retainHandling: Int = 0) {
        val profileId = _state.value.activeProfileId ?: return
        if (topicFilter.isBlank()) {
            showMessage("请输入 Topic Filter")
            return
        }
        TopicFilterMatcher.validate(topicFilter)?.let {
            showMessage(it)
            return
        }
        val subscription = Subscription(
            profileId = profileId,
            topicFilter = topicFilter.trim(),
            qos = qos,
            noLocal = noLocal,
            retainAsPublished = retainAsPublished,
            retainHandling = retainHandling.coerceIn(0, 2)
        )
        subscriptionStore.upsert(subscription)
        _state.update { it.copy(subscriptions = it.subscriptions + subscription) }
        if (subscription.topicFilter == "#" || subscription.topicFilter.endsWith("/#")) {
            showMessage("大范围订阅可能产生大量消息，请注意内存和网络流量")
        }
        viewModelScope.launch { repository.subscribe(subscription) }
    }

    fun removeSubscription(subscription: Subscription) {
        subscriptionStore.delete(subscription.id)
        _state.update { it.copy(subscriptions = it.subscriptions.filterNot { item -> item.id == subscription.id }) }
        viewModelScope.launch { repository.unsubscribe(subscription) }
    }

    fun updateSubscription(subscription: Subscription, topicFilter: String, qos: Int, noLocal: Boolean = false, retainAsPublished: Boolean = false, retainHandling: Int = 0) {
        val normalizedTopic = topicFilter.trim()
        if (normalizedTopic.isBlank()) {
            showMessage("请输入 Topic Filter")
            return
        }
        TopicFilterMatcher.validate(normalizedTopic)?.let {
            showMessage(it)
            return
        }
        val updated = subscription.copy(
            topicFilter = normalizedTopic,
            qos = qos.coerceIn(0, 2),
            noLocal = noLocal,
            retainAsPublished = retainAsPublished,
            retainHandling = retainHandling.coerceIn(0, 2)
        )
        subscriptionStore.upsert(updated)
        _state.update { current ->
            current.copy(subscriptions = current.subscriptions.map { item -> if (item.id == updated.id) updated else item })
        }
        viewModelScope.launch {
            repository.unsubscribe(subscription)
            repository.subscribe(updated)
        }
    }

    fun publish(
        topic: String,
        payloadInput: String,
        format: com.mqttmobile.app.data.model.PayloadFormat,
        qos: Int,
        retain: Boolean,
        messageExpiryIntervalSeconds: Long? = null,
        contentType: String? = null,
        responseTopic: String? = null
    ) {
        val profileId = _state.value.activeProfileId ?: return
        if (topic.isBlank()) {
            showMessage("请输入发布 Topic")
            return
        }
        val payload = PayloadFormatter.parse(payloadInput, format).getOrElse {
            showMessage(it.message ?: "Payload 格式无效")
            return
        }
        val expiry = messageExpiryIntervalSeconds?.coerceIn(0L, 4_294_967_295L)
        viewModelScope.launch {
            repository.publish(
                PublishRequest(
                    profileId = profileId,
                    topic = topic.trim(),
                    payload = payload,
                    qos = qos.coerceIn(0, 2),
                    retain = retain,
                    format = format,
                    messageExpiryIntervalSeconds = expiry,
                    contentType = contentType?.trim()?.ifBlank { null },
                    responseTopic = responseTopic?.trim()?.ifBlank { null }
                )
            )
        }
    }

    fun setSearchQuery(value: String) = _state.update { it.copy(searchQuery = value, subscriptionTopicFilter = null) }

    fun viewSubscriptionMessages(subscription: Subscription) {
        _state.update {
            it.copy(
                subscriptionTopicFilter = subscription.topicFilter,
                searchQuery = "",
                directionFilter = null
            )
        }
    }

    fun clearSubscriptionMessageFilter() = _state.update { it.copy(subscriptionTopicFilter = null) }

    fun setDirectionFilter(value: MessageDirection?) = _state.update { it.copy(directionFilter = value) }

    fun setJsonOnly(value: Boolean) = _state.update { it.copy(jsonOnly = value) }

    fun setSortAscending(value: Boolean) = _state.update { it.copy(sortAscending = value) }

    fun setMessageLimit(value: Int) {
        repository.setMessageLimit(value)
        _state.update { current -> current.copy(messageLimit = repository.messageLimit(), messages = current.messages.take(value)) }
    }

    fun setDefaultDisplayFormat(value: com.mqttmobile.app.data.model.PayloadFormat) {
        _state.update { it.copy(defaultDisplayFormat = value) }
        viewModelScope.launch { appSettingsStore.setDefaultDisplayFormat(value) }
    }

    fun setLogLimit(value: Int) {
        val normalized = value.coerceIn(100, 1000)
        _state.update { it.copy(logLimit = normalized, logs = it.logs.take(normalized)) }
        viewModelScope.launch { appSettingsStore.setLogLimit(normalized) }
    }

    fun setPaused(value: Boolean) = _state.update { it.copy(isPaused = value) }

    fun clearMessages() {
        _state.value.activeProfileId?.let(repository::clearHistory)
        _state.update { it.copy(messages = emptyList()) }
    }

    fun clearLogs() = _state.update { it.copy(logs = emptyList()) }

    fun showMessage(message: String) = _state.update { it.copy(snackbar = message) }

    fun consumeSnackbar() = _state.update { it.copy(snackbar = null) }

    override fun onCleared() {
        super.onCleared()
    }

    private fun handleEvent(event: MqttEvent) {
        when (event) {
            is MqttEvent.State -> _state.update { current ->
                current.copy(
                    connectionStatus = if (current.activeProfileId == event.profileId) event.status else current.connectionStatus,
                    connectionStatuses = current.connectionStatuses + (event.profileId to event.status)
                )
            }
            is MqttEvent.Message -> _state.update { current ->
                if (current.activeProfileId != event.message.profileId || current.isPaused) {
                    current
                } else {
                    current.copy(messages = (listOf(event.message) + current.messages).take(current.messageLimit))
                }
            }
            is MqttEvent.Log -> _state.update { current ->
                if (current.activeProfileId == event.log.profileId) {
                    current.copy(logs = (listOf(event.log) + current.logs).take(current.logLimit))
                } else {
                    current
                }
            }
        }
    }

    private companion object {
    }
}

