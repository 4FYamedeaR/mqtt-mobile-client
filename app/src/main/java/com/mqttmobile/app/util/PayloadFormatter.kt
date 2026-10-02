package com.mqttmobile.app.util

import android.util.Base64
import com.mqttmobile.app.data.model.PayloadFormat
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

object PayloadFormatter {
    fun display(payload: ByteArray, format: PayloadFormat): String {
        return when (format) {
            PayloadFormat.JSON -> prettyJson(payload) ?: text(payload)
            PayloadFormat.TEXT -> text(payload)
            PayloadFormat.HEX -> payload.joinToString(" ") { "%02X".format(it) }
            PayloadFormat.BASE64 -> Base64.encodeToString(payload, Base64.NO_WRAP)
            PayloadFormat.BINARY -> "${payload.size} bytes\n${payload.joinToString(" ") { "%02X".format(it) }}"
        }
    }

    fun parse(input: String, format: PayloadFormat): Result<ByteArray> {
        return runCatching {
            when (format) {
                PayloadFormat.JSON -> {
                    require(input.isNotBlank()) { "JSON Payload 不能为空" }
                    JSONTokener(input).nextValue()
                    input.toByteArray(Charsets.UTF_8)
                }
                PayloadFormat.TEXT -> input.toByteArray(Charsets.UTF_8)
                PayloadFormat.HEX -> input.replace("0x", "", ignoreCase = true)
                    .replace(Regex("[^0-9A-Fa-f]"), "")
                    .let { hex ->
                        require(hex.length % 2 == 0) { "Hex payload 必须包含偶数个十六进制字符" }
                        ByteArray(hex.length / 2) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
                    }
                PayloadFormat.BASE64 -> Base64.decode(input, Base64.DEFAULT)
                PayloadFormat.BINARY -> input.toByteArray(Charsets.UTF_8)
            }
        }
    }

    fun prettyJson(payload: ByteArray): String? {
        return runCatching {
            when (val value = jsonValue(payload)) {
                is JSONObject -> value.toString(2)
                is JSONArray -> value.toString(2)
                else -> null
            }
        }.getOrNull()
    }

    /** Returns a JSONObject or JSONArray root for node-level Compose rendering. */
    fun jsonValue(payload: ByteArray): Any? = runCatching {
        JSONTokener(text(payload)).nextValue().takeIf { it is JSONObject || it is JSONArray }
    }.getOrNull()

    fun compactJson(input: String): String? {
        return runCatching {
            when (val value = JSONTokener(input).nextValue()) {
                is JSONObject -> value.toString()
                is JSONArray -> value.toString()
                else -> null
            }
        }.getOrNull()
    }

    fun summary(payload: ByteArray, maxLength: Int = 120): String {
        val value = prettyJson(payload)?.replace(Regex("\\s+"), " ") ?: text(payload)
        return if (value.length <= maxLength) value else value.take(maxLength - 1) + "…"
    }

    private fun text(payload: ByteArray): String = payload.toString(Charsets.UTF_8)
}
