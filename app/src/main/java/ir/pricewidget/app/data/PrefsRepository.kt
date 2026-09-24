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
        val WIDGET_FOLLOW_SYSTEM = androidx.datastore.preferences.core.booleanPreferencesKey("widget_follow_system")
        val HEADER_INTERVAL = androidx.datastore.preferences.core.intPreferencesKey("header_interval_seconds")
        val HOME_VIEW_MODE = androidx.datastore.preferences.core.stringPreferencesKey("home_view_mode")
        val NOTIF_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("notif_enabled")
        val ONBOARDED = androidx.datastore.preferences.core.booleanPreferencesKey("onboarded")
        val HOME_ITEMS_ORDER = stringPreferencesKey("home_items_order")
    }

    // Home-screen watchlist, stored as an ORDERED list (joined by a separator
    // char that won't appear in item names) so the user's custom card order
    // survives app restarts. No hard cap on count.
    private val HOME_SEP = "\u241F"

    val homeItemsFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.HOME_ITEMS_ORDER]
            ?.split(HOME_SEP)
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    suspend fun setHomeItems(keys: List<String>) {
        context.dataStore.edit { it[Keys.HOME_ITEMS_ORDER] = keys.joinToString(HOME_SEP) }
    }

    suspend fun getHomeItemsOnce(): List<String> = homeItemsFlow.first()

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

    val widgetFollowSystemFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.WIDGET_FOLLOW_SYSTEM] ?: false }

    suspend fun setWidgetFollowSystem(follow: Boolean) {
        context.dataStore.edit { it[Keys.WIDGET_FOLLOW_SYSTEM] = follow }
    }

    suspend fun isWidgetFollowSystemOnce(): Boolean = widgetFollowSystemFlow.first()

    val headerIntervalFlow: Flow<Int> = context.dataStore.data.map { it[Keys.HEADER_INTERVAL] ?: 7 }
    suspend fun setHeaderInterval(seconds: Int) {
        context.dataStore.edit { it[Keys.HEADER_INTERVAL] = seconds }
    }
    suspend fun getHeaderIntervalOnce(): Int = headerIntervalFlow.first()

    val homeViewModeFlow: Flow<String> = context.dataStore.data.map { it[Keys.HOME_VIEW_MODE] ?: "grid" }
    suspend fun setHomeViewMode(mode: String) {
        context.dataStore.edit { it[Keys.HOME_VIEW_MODE] = mode }
    }
    suspend fun getHomeViewModeOnce(): String = homeViewModeFlow.first()

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
    suspend fun getLastUpdatedOnce(): String? = lastUpdatedFlow.first()
}
