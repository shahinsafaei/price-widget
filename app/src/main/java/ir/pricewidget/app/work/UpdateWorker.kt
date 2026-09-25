package ir.pricewidget.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ir.pricewidget.app.data.ApiService
import ir.pricewidget.app.data.PrefsRepository
import ir.pricewidget.app.widget.PriceWidget
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Only refresh during Tehran market hours (09:00–20:00) to save API quota.
        // Manual refreshes from inside the app bypass this check.
        val isManual = inputData.getBoolean(KEY_MANUAL, false)
        if (!isManual && !isWithinMarketHours()) {
            return Result.success()
        }

        return try {
            val repo = PrefsRepository(applicationContext)
            val response = ApiService.create().getGoldCurrency()

            val availableKeys = response.allItems()
                .map { it.second.itemKey }
                .toSet()

            val currentSelected = repo.getSelectedItems()
            val validSelected = currentSelected.intersect(availableKeys)

            if (validSelected != currentSelected) {
                repo.setSelectedItems(validSelected)
            }

            val currentHomeItems = repo.getHomeItems()
            val validHomeItems = currentHomeItems.filter { it in availableKeys }

            if (validHomeItems != currentHomeItems) {
                repo.setHomeItems(validHomeItems)
            }

            val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            repo.saveCache(response, now)
            PriceWidget.forceUpdateAll(applicationContext)
            ir.pricewidget.app.notification.RateNotifier.show(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun isWithinMarketHours(): Boolean {
        val tehran = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"))
        val hour = tehran.get(Calendar.HOUR_OF_DAY)
        return hour in 9..19 // 9:00 up to just before 20:00
    }

    companion object {
        const val KEY_MANUAL = "manual"
    }
}
