package ir.pricewidget.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import ir.pricewidget.app.data.ApiService
import ir.pricewidget.app.data.PrefsRepository
import ir.pricewidget.app.widget.PriceWidget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = PrefsRepository(applicationContext)
            val key = repo.getApiKeyOnce()
            val response = ApiService.create().getGoldCurrency(key)
            val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            repo.saveCache(response, now)
            PriceWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
