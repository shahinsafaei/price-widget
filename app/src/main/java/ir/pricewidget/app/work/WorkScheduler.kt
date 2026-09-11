package ir.pricewidget.app.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val WORK_NAME = "price_update_work"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun runOnce(context: Context) {
        val request = androidx.work.OneTimeWorkRequestBuilder<UpdateWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
