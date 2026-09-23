package ir.pricewidget.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ir.pricewidget.app.widget.PriceWidget

/**
 * Fix (v2.9.9.3): updating the widget used to happen inside a
 * `rememberCoroutineScope()` coroutine launched from the UI (MainActivity).
 * That scope is tied to the screen — if the user left the screen or the
 * app went to background before the update finished, the coroutine got
 * cancelled and the widget silently never updated ("sometimes works,
 * sometimes doesn't").
 *
 * This worker runs independently of the UI through WorkManager, so it's
 * guaranteed to run to completion regardless of what the screen does.
 * It does NOT hit the network — it just re-renders the widget from
 * whatever is already saved in DataStore.
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            PriceWidget.forceUpdateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}