package com.mqttmobile.app.util

/** Implements MQTT topic-filter matching for the message workspace filter. */
object TopicFilterMatcher {
    fun validate(filter: String): String? {
        val normalized = filter.trim()
        if (normalized.isBlank()) return "Topic Filter 不能为空"
        val levels = normalized.split('/')
        levels.forEachIndexed { index, level ->
            if (level.contains('#') && level != "#") return "# 必须单独占用一个 Topic 层级"
            if (level.contains('+') && level != "+") return "+ 必须单独占用一个 Topic 层级"
            if (level == "#" && index != levels.lastIndex) return "# 必须位于 Topic Filter 末尾"
        }
        return null
    }

    fun matches(topic: String, filter: String): Boolean {
        if (filter.isBlank()) return true
        val topicLevels = topic.split('/')
        val filterLevels = filter.split('/')
        val hashIndex = filterLevels.indexOfFirst { it == "#" }
        val exactLevels = if (hashIndex >= 0) filterLevels.take(hashIndex) else filterLevels
        if (hashIndex < 0 && topicLevels.size != exactLevels.size) return false
        if (hashIndex >= 0 && topicLevels.size < exactLevels.size) return false
        return exactLevels.indices.all { index ->
            exactLevels[index] == "+" || exactLevels[index] == topicLevels[index]
        }
    }
}
