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
        val API_KEY = stringPreferencesKey("api_key")
        val SELECTED_ITEMS = stringSetPreferencesKey("selected_items")
        val CACHED_JSON = stringPreferencesKey("cached_json")
        val LAST_UPDATED = stringPreferencesKey("last_updated")
    }

    // Default key is the one you already have from brsapi.ir.
    // You can change it any time from the in-app Settings screen.
    private val defaultApiKey = "BheFvPmxKvQMyv5W8DQrrFSX8JKrgQeP"

    val apiKeyFlow: Flow<String> = context.dataStore.data.map {
        it[Keys.API_KEY] ?: defaultApiKey
    }

    val selectedItemsFlow: Flow<Set<String>> = context.dataStore.data.map {
        it[Keys.SELECTED_ITEMS] ?: emptySet()
    }

    val cachedResponseFlow: Flow<GoldCurrencyResponse?> = context.dataStore.data.map { prefs ->
        prefs[Keys.CACHED_JSON]?.let { json ->
            runCatching { Gson().fromJson(json, GoldCurrencyResponse::class.java) }.getOrNull()
        }
    }

    val lastUpdatedFlow: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_UPDATED] }

    suspend fun setApiKey(key: String) {
        context.dataStore.edit { it[Keys.API_KEY] = key }
    }

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
    suspend fun getApiKeyOnce(): String = apiKeyFlow.first()
    suspend fun getCachedOnce(): GoldCurrencyResponse? = cachedResponseFlow.first()
}
