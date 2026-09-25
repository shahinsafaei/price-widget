package ir.pricewidget.app.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val WORK_NAME = "price_update_work"

    fun schedule(context: Context) {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun runOnce(context: Context) {
        val manualData = Data.Builder().putBoolean(UpdateWorker.KEY_MANUAL, true).build()
        val request = androidx.work.OneTimeWorkRequestBuilder<UpdateWorker>()
            .setInputData(manualData)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    /**
     * Refresh the home-screen widget right now, independent of the UI's
     * lifecycle (see WidgetRefreshWorker for why this matters). This does
     * NOT hit the network — call it any time saved widget settings change
     * (item selection, dark/light mode).
     */
    fun refreshWidgetNow(context: Context) {
        val request = androidx.work.OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "widget_refresh_now",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * ذخیره‌ی تضمینی تنظیمات ویجت (تم + آیتم‌های انتخاب‌شده) تو یه Worker واحد.
     * قبلاً این دو تا با enqueue جدا و هم‌زمان اجرا می‌شدن که باعث یه race
     * می‌شد: هرکدوم جداگونه ویجت رو رندر می‌کرد و ممکن بود اون یکی هنوز
     * نوشته نشده رو با مقدار قدیمی بخونه (مثلاً تم عوض نشه تا یه تغییر
     * دیگه دوباره trigger بشه). با یکی‌کردنشون، ذخیره + رندر همیشه atomic
     * و با آخرین دیتای هر دو انجام می‌شه.
     */
    fun saveWidgetSettings(
        context: Context,
        dark: Boolean,
        followSystem: Boolean,
        items: Set<String>
    ) {
        val data = Data.Builder()
            .putBoolean(WidgetRefreshWorker.KEY_DARK, dark)
            .putBoolean(WidgetRefreshWorker.KEY_FOLLOW_SYSTEM, followSystem)
            .putStringArray(WidgetRefreshWorker.KEY_SELECTED_ITEMS, items.toTypedArray())
            .build()
        val request = androidx.work.OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "widget_settings_save",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** ذخیره‌ی تضمینی آیتم‌های صفحه‌ی اصلی، مستقل از عمر صفحه. */
    fun saveHomeItems(context: Context, items: List<String>) {
        val data = Data.Builder()
            .putStringArray(WidgetRefreshWorker.KEY_HOME_ITEMS, items.toTypedArray())
            .build()
        val request = androidx.work.OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
