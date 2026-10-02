package com.mqttmobile.app.util

import android.util.Base64
import com.mqttmobile.app.data.model.MqttMessageRecord
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MessageExportFormat(val extension: String) {
    JSON("json"),
    CSV("csv")
}

object MessageExportFormatter {
    fun format(messages: List<MqttMessageRecord>, format: MessageExportFormat): String = when (format) {
        MessageExportFormat.JSON -> toJson(messages)
        MessageExportFormat.CSV -> toCsv(messages)
    }

    private fun toJson(messages: List<MqttMessageRecord>): String {
        val json = JSONArray()
        messages.forEach { message ->
            json.put(
                JSONObject().apply {
                    put("id", message.id)
                    put("profileId", message.profileId)
                    put("direction", message.direction.label)
                    put("topic", message.topic)
                    put("payload", message.payload.toString(Charsets.UTF_8))
                    put("payloadBase64", Base64.encodeToString(message.payload, Base64.NO_WRAP))
                    put("qos", message.qos)
                    put("retain", message.retain)
                    put("duplicate", message.duplicate)
                    put("receivedAt", message.receivedAt)
                    put("properties", JSONObject().apply { message.properties.forEach { (key, value) -> put(key, value) } })
                }
            )
        }
        return json.toString(2)
    }

    private fun toCsv(messages: List<MqttMessageRecord>): String {
        val builder = StringBuilder()
        builder.appendLine("id,profileId,direction,topic,payload,payloadBase64,qos,retain,duplicate,receivedAt,properties")
        messages.forEach { message ->
            builder.appendLine(
                listOf(
                    message.id.toString(),
                    message.profileId,
                    message.direction.label,
                    message.topic,
                    message.payload.toString(Charsets.UTF_8),
                    Base64.encodeToString(message.payload, Base64.NO_WRAP),
                    message.qos.toString(),
                    message.retain.toString(),
                    message.duplicate.toString(),
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(message.receivedAt)),
                    message.properties.entries.joinToString("; ") { (key, value) -> "$key=$value" }
                ).joinToString(",", transform = ::escapeCsv)
            )
        }
        return builder.toString()
    }

    private fun escapeCsv(value: String): String = "\"${value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ")}\""
}
