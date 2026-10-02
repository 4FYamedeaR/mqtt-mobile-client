package com.mqttmobile.app.data.local

import android.content.Context
import android.util.Base64
import com.mqttmobile.app.data.model.MessageDirection
import com.mqttmobile.app.data.model.MqttMessageRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists a bounded message history in Room so messages received while the UI is
 * backgrounded survive process recreation. Legacy JSON/SharedPreferences data is
 * imported the first time the Room table is empty.
 */
class MessageStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val historyFile = File(appContext.filesDir, HISTORY_FILE_NAME)
    private val messageDao = MqttDatabaseProvider.get(appContext).messageDao()

    init {
        migrateLegacyHistory()
    }

    @Synchronized
    fun forProfile(profileId: String): List<MqttMessageRecord> = load()
        .filter { it.profileId == profileId }
        .sortedByDescending { it.receivedAt }

    @Synchronized
    fun append(message: MqttMessageRecord) {
        val next = (load() + message)
            .groupBy { it.profileId }
            .values
            .flatMap { messages -> messages.sortedByDescending { it.receivedAt }.take(maxPerProfile()) }
        persist(next)
    }

    @Synchronized
    fun clear(profileId: String) {
        runCatching { messageDao.deleteForProfile(profileId) }
            .onFailure { persist(load().filterNot { it.profileId == profileId }) }
    }

    @Synchronized
    fun maxPerProfile(): Int = preferences.getInt(LIMIT_KEY, DEFAULT_LIMIT)

    @Synchronized
    fun setMaxPerProfile(limit: Int) {
        val normalized = limit.coerceIn(MIN_LIMIT, MAX_LIMIT)
        preferences.edit().putInt(LIMIT_KEY, normalized).apply()
        val trimmed = load()
            .groupBy { it.profileId }
            .values
            .flatMap { messages -> messages.sortedByDescending { it.receivedAt }.take(normalized) }
        persist(trimmed)
    }

    private fun load(): List<MqttMessageRecord> = runCatching {
        messageDao.all().map(::toModel)
    }.getOrElse {
        loadLegacy()
    }

    private fun persist(messages: List<MqttMessageRecord>) {
        runCatching {
            val existingIds = messageDao.all().map { it.id }.toSet()
            val nextIds = messages.map { it.id }.toSet()
            (existingIds - nextIds).forEach(messageDao::deleteById)
            if (messages.isNotEmpty()) {
                messageDao.insertAll(messages.map(::toEntity))
            }
        }.onFailure {
            // Keep the legacy format as an emergency fallback for an unavailable DB.
            persistLegacy(messages)
        }
    }

    private fun migrateLegacyHistory() {
        runCatching {
            if (messageDao.all().isEmpty()) {
                val legacy = loadLegacy()
                val migrated = legacy
                    .groupBy { it.profileId }
                    .values
                    .flatMap { messages -> messages.sortedByDescending { it.receivedAt }.take(maxPerProfile()) }
                if (migrated.isNotEmpty()) messageDao.insertAll(migrated.map(::toEntity))
            }
        }
    }

    private fun loadLegacy(): List<MqttMessageRecord> {
        val raw = runCatching {
            if (historyFile.exists()) historyFile.readText(Charsets.UTF_8)
            else preferences.getString(MESSAGES_KEY, null)
        }.getOrNull() ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            buildList(json.length()) {
                for (index in 0 until json.length()) {
                    val item = json.getJSONObject(index)
                    val properties = linkedMapOf<String, String>()
                    val propertyJson = item.optJSONObject("properties")
                    if (propertyJson != null) {
                        propertyJson.keys().forEach { key -> properties[key] = propertyJson.optString(key) }
                    }
                    add(
                        MqttMessageRecord(
                            id = item.optLong("id", System.nanoTime()),
                            profileId = item.getString("profileId"),
                            direction = runCatching {
                                MessageDirection.valueOf(item.optString("direction", MessageDirection.RECEIVED.name))
                            }.getOrDefault(MessageDirection.RECEIVED),
                            topic = item.getString("topic"),
                            payload = Base64.decode(item.optString("payload", ""), Base64.DEFAULT),
                            qos = item.optInt("qos", 0),
                            retain = item.optBoolean("retain", false),
                            duplicate = item.optBoolean("duplicate", false),
                            receivedAt = item.optLong("receivedAt", System.currentTimeMillis()),
                            properties = properties
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistLegacy(messages: List<MqttMessageRecord>) {
        val json = JSONArray()
        messages.forEach { message ->
            json.put(
                JSONObject().apply {
                    put("id", message.id)
                    put("profileId", message.profileId)
                    put("direction", message.direction.name)
                    put("topic", message.topic)
                    put("payload", Base64.encodeToString(message.payload, Base64.NO_WRAP))
                    put("qos", message.qos)
                    put("retain", message.retain)
                    put("duplicate", message.duplicate)
                    put("receivedAt", message.receivedAt)
                    put("properties", JSONObject().apply {
                        message.properties.forEach { (key, value) -> put(key, value) }
                    })
                }
            )
        }
        val serialized = json.toString()
        runCatching {
            val temporaryFile = File(historyFile.parentFile, "$HISTORY_FILE_NAME.tmp")
            temporaryFile.writeText(serialized, Charsets.UTF_8)
            if (!temporaryFile.renameTo(historyFile)) {
                historyFile.writeText(serialized, Charsets.UTF_8)
                temporaryFile.delete()
            }
            preferences.edit().remove(MESSAGES_KEY).apply()
        }.onFailure {
            preferences.edit().putString(MESSAGES_KEY, serialized).apply()
        }
    }

    private fun toEntity(message: MqttMessageRecord): MessageEntity = MessageEntity(
        id = message.id,
        profileId = message.profileId,
        direction = message.direction.name,
        topic = message.topic,
        payload = message.payload,
        qos = message.qos,
        retain = message.retain,
        duplicate = message.duplicate,
        receivedAt = message.receivedAt,
        propertiesJson = JSONObject().apply {
            message.properties.forEach { (key, value) -> put(key, value) }
        }.toString()
    )

    private fun toModel(entity: MessageEntity): MqttMessageRecord {
        val properties = linkedMapOf<String, String>()
        runCatching {
            val json = JSONObject(entity.propertiesJson)
            json.keys().forEach { key -> properties[key] = json.optString(key) }
        }
        return MqttMessageRecord(
            id = entity.id,
            profileId = entity.profileId,
            direction = runCatching { MessageDirection.valueOf(entity.direction) }.getOrDefault(MessageDirection.RECEIVED),
            topic = entity.topic,
            payload = entity.payload,
            qos = entity.qos,
            retain = entity.retain,
            duplicate = entity.duplicate,
            receivedAt = entity.receivedAt,
            properties = properties
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "mqtt_messages"
        const val MESSAGES_KEY = "messages"
        const val HISTORY_FILE_NAME = "mqtt-message-history.json"
        const val LIMIT_KEY = "message_limit"
        const val DEFAULT_LIMIT = 1000
        const val MIN_LIMIT = 100
        const val MAX_LIMIT = 5000
    }
}
