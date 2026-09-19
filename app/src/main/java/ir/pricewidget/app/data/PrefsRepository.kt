package ir.pricewidget.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "price_widget_prefs")

class PrefsRepository(private val context: Context) {

    private object Keys {
        val SELECTED_ITEMS = stringSetPreferencesKey("selected_items")
        val CACHED_JSON = stringPreferencesKey("cached_json")
        val LAST_UPDATED = stringPreferencesKey("last_updated")
        val WIDGET_DARK = androidx.datastore.preferences.core.booleanPreferencesKey("widget_dark")
        val NOTIF_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("notif_enabled")
        val ONBOARDED = androidx.datastore.preferences.core.booleanPreferencesKey("onboarded")
        val HOME_ITEMS = stringSetPreferencesKey("home_items")
    }

    // Home-screen watchlist — separate from widget selection (SELECTED_ITEMS below).
    // No hard cap: the home grid just adds a row for every extra item.
    val homeItemsFlow: Flow<Set<String>> = context.dataStore.data.map {
        it[Keys.HOME_ITEMS] ?: emptySet()
    }

    suspend fun setHomeItems(keys: Set<String>) {
        context.dataStore.edit { it[Keys.HOME_ITEMS] = keys }
    }

    suspend fun getHomeItemsOnce(): Set<String> = homeItemsFlow.first()

    val onboardedFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDED] ?: false }

    suspend fun setOnboarded(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDED] = done }
    }

    suspend fun isOnboardedOnce(): Boolean = onboardedFlow.first()

    val selectedItemsFlow: Flow<Set<String>> = context.dataStore.data.map {
        it[Keys.SELECTED_ITEMS] ?: emptySet()
    }

    val cachedResponseFlow: Flow<GoldCurrencyResponse?> = context.dataStore.data.map { prefs ->
        prefs[Keys.CACHED_JSON]?.let { json ->
            runCatching { Gson().fromJson(json, GoldCurrencyResponse::class.java) }.getOrNull()
        }
    }

    val lastUpdatedFlow: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_UPDATED] }

    val isDarkWidgetFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.WIDGET_DARK] ?: false }

    suspend fun setWidgetDark(dark: Boolean) {
        context.dataStore.edit { it[Keys.WIDGET_DARK] = dark }
    }

    suspend fun isDarkWidgetOnce(): Boolean = isDarkWidgetFlow.first()

    val notificationEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIF_ENABLED] ?: false }

    suspend fun setNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIF_ENABLED] = enabled }
    }

    suspend fun isNotificationEnabledOnce(): Boolean = notificationEnabledFlow.first()

    suspend fun setSelectedItems(keys: Set<String>) {
        context.dataStore.edit { it[Keys.SELECTED_ITEMS] = keys }
    }

    suspend fun saveCache(response: GoldCurrencyResponse, updatedAt: String) {
        context.dataStore.edit {
            it[Keys.CACHED_JSON] = Gson().toJson(response)
            it[Keys.LAST_UPDATED] = updatedAt
        }
    }

    suspend fun getSelectedItemsOnce(): Set<String> = selectedItemsFlow.first()
    suspend fun getCachedOnce(): GoldCurrencyResponse? = cachedResponseFlow.first()
}
