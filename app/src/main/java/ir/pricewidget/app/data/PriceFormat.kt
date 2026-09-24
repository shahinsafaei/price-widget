package ir.pricewidget.app.data

import java.util.Locale

/**
 * فرمت مشترک قیمت‌ها، تا آیتم‌های ارزون (مثل دوج/شیبا/ترون که زیر ۱ واحدن)
 * با فرمت بدون اعشار گرد نشن و «۰» نشون داده نشن.
 *
 * - قیمت >= ۱: بدون اعشار، با جداکننده‌ی هزارگان (مثلاً "235,975")
 * - قیمت < ۱: با ۴ رقم اعشار (مثلاً "0.0842")
 */
object PriceFormat {
    fun format(value: Double): String {
        return if (kotlin.math.abs(value) >= 1.0) {
            "%,.0f".format(Locale.US, value)
        } else {
            "%,.4f".format(Locale.US, value)
        }
    }
}