package ir.pricewidget.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ir.pricewidget.app.data.PrefsRepository
import ir.pricewidget.app.widget.PriceWidget

/**
 * فیکس (v2.9.9.4): قبلاً ذخیره‌سازی تنظیمات (تم ویجت، آیتم‌های ویجت، آیتم‌های
 * صفحه‌ی اصلی) از طریق scope.launch در UI انجام می‌شد که به عمر صفحه وابسته
 * بود. اگه کاربر درست بعد از تغییر تنظیمات از اپ خارج می‌شد (که خیلی طبیعیه،
 * چون می‌خواد بره ویجت رو نگاه کنه!)، اون کوروتین ممکن بود قبل از تموم شدن
 * لغو بشه — یعنی تنظیمات ذخیره نمی‌شد ولی ویجت با دیتای قدیمی رندر می‌شد.
 *
 * الان: هم «ذخیره» هم «رندر» تو یه Worker واحد و تضمینی (مستقل از UI) انجام
 * می‌شن — یا هر دو با هم کامل تموم می‌شن، یا (در بدترین حالت نادر) هیچ‌کدوم؛
 * اما هرگز حالت نصفه (ذخیره‌نشده ولی رندرشده) پیش نمیاد.
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_DARK = "key_dark"
        const val KEY_FOLLOW_SYSTEM = "key_follow_system"
        const val KEY_SELECTED_ITEMS = "key_selected_items"
        const val KEY_HOME_ITEMS = "key_home_items"
        const val KEY_PRICE_HIDDEN = "key_price_hidden"
    }

    override suspend fun doWork(): Result {
        return try {
            val repo = PrefsRepository(applicationContext)

            if (inputData.keyValueMap.containsKey(KEY_DARK)) {
                repo.setWidgetDark(inputData.getBoolean(KEY_DARK, false))
            }
            if (inputData.keyValueMap.containsKey(KEY_FOLLOW_SYSTEM)) {
                repo.setWidgetFollowSystem(inputData.getBoolean(KEY_FOLLOW_SYSTEM, false))
            }
            inputData.getStringArray(KEY_SELECTED_ITEMS)?.let { items ->
                repo.setSelectedItems(items.toSet())
            }
            inputData.getStringArray(KEY_HOME_ITEMS)?.let { items ->
                repo.setHomeItems(items.toList())
            }
            if (inputData.keyValueMap.containsKey(KEY_PRICE_HIDDEN)) {
                repo.setWidgetPriceHidden(inputData.getBoolean(KEY_PRICE_HIDDEN, false))
            }

            PriceWidget.forceUpdateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}