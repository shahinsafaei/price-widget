package ir.pricewidget.app.data

import ir.pricewidget.app.R

/** Maps symbols to flag emojis / icons and short widget-friendly labels. */
object IconMap {

    /** Returns a drawable resource id for a real flag PNG, or null if none applies (gold/crypto). */
    fun flagDrawableRes(symbol: String?): Int? = when {
        symbol == null -> null
        symbol.contains("XAU") -> null // gold ounce — not a currency flag
        symbol.contains("GOLD") || symbol.contains("COIN") -> null
        symbol.contains("USD") && !symbol.contains("USDT") && !symbol.contains("USDC") -> R.drawable.flag_us
        symbol.contains("EUR") -> R.drawable.flag_eu
        symbol.contains("GBP") -> R.drawable.flag_gb
        symbol.contains("AED") -> R.drawable.flag_ae
        symbol.contains("JPY") -> R.drawable.flag_jp
        symbol.contains("CNY") -> R.drawable.flag_cn
        symbol.contains("TRY") -> R.drawable.flag_tr
        symbol.contains("CHF") -> R.drawable.flag_ch
        symbol.contains("CAD") -> R.drawable.flag_ca
        symbol.contains("AUD") -> R.drawable.flag_au
        symbol.contains("SAR") -> R.drawable.flag_sa
        symbol.contains("KWD") -> R.drawable.flag_kw
        symbol.contains("QAR") -> R.drawable.flag_qa
        symbol.contains("OMR") -> R.drawable.flag_om
        symbol.contains("BHD") -> R.drawable.flag_bh
        symbol.contains("IQD") -> R.drawable.flag_iq
        symbol.contains("AFN") -> R.drawable.flag_af
        symbol.contains("RUB") -> R.drawable.flag_ru
        symbol.contains("INR") -> R.drawable.flag_in
        symbol.contains("PKR") -> R.drawable.flag_pk
        symbol.contains("SEK") -> R.drawable.flag_se
        symbol.contains("THB") -> R.drawable.flag_th
        symbol.contains("MYR") -> R.drawable.flag_my
        symbol.contains("AZN") -> R.drawable.flag_az
        symbol.contains("AMD") -> R.drawable.flag_am
        symbol.contains("GEL") -> R.drawable.flag_ge
        symbol.contains("SYP") -> R.drawable.flag_sy
        else -> null // gold, coins, crypto, tether -> use colored letter badge instead
    }

    fun flag(symbol: String?): String = when {
        symbol == null -> "💱"
        symbol.contains("XAU") -> "🪙"
        symbol.contains("GOLD") -> "🪙"
        symbol.contains("COIN") -> "🪙"
        symbol.contains("USDT") -> "₮"
        symbol.contains("USDC") -> "🪙"
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
        symbol.contains("BTC") -> "₿"
        symbol.contains("ETH") -> "Ξ"
        symbol.contains("XRP") -> "✕"
        symbol.contains("BNB") -> "🔶"
        symbol.contains("SOL") -> "◎"
        symbol.contains("TRX") -> "🔺"
        symbol.contains("DOGE") -> "🐕"
        symbol.contains("ADA") -> "🔷"
        symbol.contains("LINK") -> "🔗"
        symbol.contains("XLM") -> "✦"
        symbol.contains("AVAX") -> "🔺"
        symbol.contains("SHIB") -> "🐕"
        symbol.contains("LTC") -> "Ł"
        symbol.contains("DOT") -> "●"
        symbol.contains("UNI") -> "🦄"
        symbol.contains("ATOM") -> "⚛"
        symbol.contains("FIL") -> "📁"
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
