package ir.pricewidget.app.data

import com.google.gson.annotations.SerializedName

/**
 * NOTE: brsapi.ir field names are best-effort based on public documentation.
 * If the real response uses slightly different key names, adjust the
 * @SerializedName values below to match (open the API URL in a browser
 * with your key to see the exact JSON: https://brsapi.ir/Api/Market/gold_currency.php?key=YOUR_KEY )
 */
data class PriceItem(
    @SerializedName("name") val name: String? = null,
    @SerializedName("name_en") val nameEn: String? = null,
    @SerializedName("symbol") val symbol: String? = null,
    // brsapi returns numbers for gold/currency but quoted strings for crypto —
    // keeping this as String avoids Gson type-mismatch crashes; parse on demand.
    @SerializedName("price") val price: String? = null,
    @SerializedName("change_value") val changeValue: Double? = null,
    @SerializedName("change_percent") val changePercent: Double? = null,
    @SerializedName("unit") val unit: String? = null,
    @SerializedName("date") val date: String? = null,
    @SerializedName("time") val time: String? = null
) {
    /** Unique key used to identify this item across app + widget settings */
    val itemKey: String
        get() = (nameEn ?: symbol ?: name ?: "unknown").trim()

    val displayName: String
        get() = name ?: nameEn ?: symbol ?: "—"

    val priceValue: Double?
        get() = price?.toDoubleOrNull()
}

data class GoldCurrencyResponse(
    @SerializedName("gold") val gold: List<PriceItem>? = null,
    @SerializedName("currency") val currency: List<PriceItem>? = null,
    @SerializedName("cryptocurrency") val crypto: List<PriceItem>? = null
) {
    /** All items combined, tagged with a category label */
    fun allItems(): List<Pair<String, PriceItem>> {
        val out = mutableListOf<Pair<String, PriceItem>>()
        gold?.forEach { out.add("طلا و سکه" to it) }
        currency?.forEach { out.add("ارز" to it) }
        crypto?.forEach { out.add("ارز دیجیتال" to it) }
        return out
    }
}
