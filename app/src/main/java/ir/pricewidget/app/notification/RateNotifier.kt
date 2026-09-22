package ir.pricewidget.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ir.pricewidget.app.R
import ir.pricewidget.app.data.PrefsRepository
import ir.pricewidget.app.ui.MainActivity

object RateNotifier {
    private const val CHANNEL_ID = "rate_updates"
    private const val NOTIF_ID = 1001

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "بروزرسانی نرخ‌ها",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "نمایش دائمی نرخ‌های انتخابی"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /** Builds/refreshes the persistent notification using the latest cached data. */
    suspend fun show(context: Context) {
        val repo = PrefsRepository(context)
        if (!repo.isNotificationEnabledOnce()) return

        ensureChannel(context)

        val selected = repo.getSelectedItemsOnce()
        val cached = repo.getCachedOnce()
        val items = cached?.allItems()
            ?.map { it.second }
            ?.filter { it.itemKey in selected }
            ?: emptyList()

        val summaryLine = if (items.isEmpty()) {
            "آیتمی انتخاب نشده"
        } else {
            items.joinToString("  ·  ") { it.displayName }
        }

        val detailText = if (items.isEmpty()) {
            "برای انتخاب نرخ‌ها، اپ رو باز کن"
        } else {
            items.joinToString("\n") { item ->
                val price = "\u2066" + (item.priceValue?.let { "%,.0f".format(java.util.Locale.US, it) } ?: item.price ?: "--") + "\u2069"
                val pct = item.changePercent
                val changePart = if (pct != null) {
                    val arrow = if (pct >= 0) "▲" else "▼"
                    "   $arrow${"%.1f".format(java.util.Locale.US, kotlin.math.abs(pct))}٪"
                } else ""
                // \u200F (RLM) forces right-to-left line layout so Persian labels
                // and left-to-right numbers don't get visually scrambled.
                "\u200F${item.displayName}:  \u200E$price\u200F$changePart"
            }
        }

        val openAppIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("نرخ لحظه‌ای")
            .setContentText(summaryLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
        } catch (e: SecurityException) {
            // Permission not granted; silently ignore.
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }
}
