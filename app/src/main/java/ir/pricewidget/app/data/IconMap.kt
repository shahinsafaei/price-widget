package ir.pricewidget.app.data

/** Maps symbols to flag emojis / icons and short widget-friendly labels. */
object IconMap {

    fun flag(symbol: String?): String = when {
        symbol == null -> "💱"
        symbol.contains("USDT") -> "₮"
        symbol.contains("USD") -> "🇺🇸"
        symbol.contains("EUR") -> "🇪🇺"
        symbol.contains("GBP") -> "🇬🇧"
        symbol.contains("AED") -> "🇦🇪"
        symbol.contains("JPY") -> "🇯🇵"
        symbol.contains("CNY") -> "🇨🇳"
        symbol.contains("TRY") -> "🇹🇷"
        symbol.contains("CHF") -> "🇨🇭"
        symbol.contains("CAD") -> "🇨🇦"
        symbol.contains("AUD") -> "🇦🇺"
        symbol.contains("SAR") -> "🇸🇦"
        symbol.contains("KWD") -> "🇰🇼"
        symbol.contains("QAR") -> "🇶🇦"
        symbol.contains("OMR") -> "🇴🇲"
        symbol.contains("BHD") -> "🇧🇭"
        symbol.contains("IQD") -> "🇮🇶"
        symbol.contains("AFN") -> "🇦🇫"
        symbol.contains("RUB") -> "🇷🇺"
        symbol.contains("INR") -> "🇮🇳"
        symbol.contains("PKR") -> "🇵🇰"
        symbol.contains("SEK") -> "🇸🇪"
        symbol.contains("THB") -> "🇹🇭"
        symbol.contains("MYR") -> "🇲🇾"
        symbol.contains("AZN") -> "🇦🇿"
        symbol.contains("AMD") -> "🇦🇲"
        symbol.contains("GEL") -> "🇬🇪"
        symbol.contains("SYP") -> "🇸🇾"
        symbol.contains("GOLD") -> "🪙"
        symbol.contains("COIN") -> "🪙"
        symbol.contains("BTC") -> "₿"
        symbol.contains("ETH") -> "Ξ"
        symbol.contains("XRP") -> "✕"
        symbol.contains("BNB") -> "🔶"
        symbol.contains("SOL") -> "◎"
        symbol.contains("DOGE") -> "🐕"
        else -> "💱"
    }

    /** Short label used inside the compact widget so long names don't overflow. */
    fun shortLabel(symbol: String?, fallback: String): String = when (symbol) {
        "IR_GOLD_18K" -> "طلا ۱۸"
        "IR_GOLD_24K" -> "طلا ۲۴"
        "IR_GOLD_MELTED" -> "طلای آب‌شده"
        "XAUUSD" -> "انس طلا"
        "IR_COIN_1G" -> "سکه ۱گرمی"
        "IR_COIN_QUARTER" -> "ربع سکه"
        "IR_COIN_HALF" -> "نیم سکه"
        "IR_COIN_EMAMI" -> "امامی"
        "IR_COIN_BAHAR" -> "بهار آزادی"
        "USDT_IRT" -> "تتر"
        else -> fallback
    }
}
