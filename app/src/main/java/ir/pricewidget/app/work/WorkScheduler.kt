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
}
