package com.mqttmobile.app.data.local

import android.content.Context
import com.mqttmobile.app.data.model.Subscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SubscriptionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _subscriptions = MutableStateFlow(load())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    fun forProfile(profileId: String): List<Subscription> =
        _subscriptions.value.filter { it.profileId == profileId }

    fun upsert(subscription: Subscription) {
        val next = _subscriptions.value.filterNot { it.id == subscription.id } + subscription
        _subscriptions.value = next
        persist(next)
    }

    fun delete(subscriptionId: String) {
        val next = _subscriptions.value.filterNot { it.id == subscriptionId }
        _subscriptions.value = next
        persist(next)
    }

    fun deleteForProfile(profileId: String) {
        val next = _subscriptions.value.filterNot { it.profileId == profileId }
        _subscriptions.value = next
        persist(next)
    }

    private fun load(): List<Subscription> {
        val raw = preferences.getString(SUBSCRIPTIONS_KEY, null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            buildList(json.length()) {
                for (index in 0 until json.length()) {
                    val item = json.getJSONObject(index)
                    add(
                        Subscription(
                            id = item.getString("id"),
                            profileId = item.getString("profileId"),
                            topicFilter = item.getString("topicFilter"),
                            qos = item.optInt("qos", 0),
                            noLocal = item.optBoolean("noLocal", false),
                            retainAsPublished = item.optBoolean("retainAsPublished", false),
                            retainHandling = item.optInt("retainHandling", 0).coerceIn(0, 2)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(subscriptions: List<Subscription>) {
        val json = JSONArray()
        subscriptions.forEach { subscription ->
            json.put(
                JSONObject().apply {
                    put("id", subscription.id)
                    put("profileId", subscription.profileId)
                    put("topicFilter", subscription.topicFilter)
                    put("qos", subscription.qos)
                    put("noLocal", subscription.noLocal)
                    put("retainAsPublished", subscription.retainAsPublished)
                    put("retainHandling", subscription.retainHandling)
                }
            )
        }
        preferences.edit().putString(SUBSCRIPTIONS_KEY, json.toString()).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "mqtt_subscriptions"
        const val SUBSCRIPTIONS_KEY = "subscriptions"
    }
}
