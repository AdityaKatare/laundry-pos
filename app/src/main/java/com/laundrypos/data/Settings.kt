package com.laundrypos.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ShopSettings(
    val shopName: String = "My Laundry",
    val address: String = "",
    val phone: String = "",
    val gstEnabled: Boolean = false,
    val gstin: String = "",
    val stateName: String = "",
    val sacCode: String = "9997",
    val gstRateBp: Int = 1800,
    val pricesIncludeGst: Boolean = false,
    val invoicePrefix: String = "INV",
    val paperChars: Int = 32,
    val copies: Int = 1,
    val defaultDueDays: Int = 2,
    val footer: String = "Thank you! Please bring this receipt at pickup.",
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(load())

    val state: StateFlow<ShopSettings> = _state.asStateFlow()
    val current: ShopSettings get() = _state.value

    fun save(settings: ShopSettings) {
        prefs.edit().putString(KEY, json.encodeToString(ShopSettings.serializer(), settings)).apply()
        _state.value = settings
    }

    private fun load(): ShopSettings =
        prefs.getString(KEY, null)
            ?.let { runCatching { json.decodeFromString(ShopSettings.serializer(), it) }.getOrNull() }
            ?: ShopSettings()

    private companion object {
        const val KEY = "shop"
    }
}
