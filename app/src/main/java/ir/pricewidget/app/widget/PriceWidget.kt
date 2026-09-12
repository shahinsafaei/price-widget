package ir.pricewidget.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ir.pricewidget.app.data.GoldCurrencyResponse
import ir.pricewidget.app.data.PrefsRepository

class PriceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = PrefsRepository(context)
        val selected = repo.getSelectedItemsOnce()
        val cached = repo.getCachedOnce()

        provideContent {
            WidgetContent(cached, selected)
        }
    }

    @Composable
    private fun WidgetContent(response: GoldCurrencyResponse?, selectedKeys: Set<String>) {
        val items = response?.allItems()?.filter { it.second.itemKey in selectedKeys } ?: emptyList()

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF1C1C1E))
                .padding(14.dp)
        ) {
            Text(
                text = "نرخ لحظه‌ای",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF9A9A9E)),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )

            if (items.isEmpty()) {
                Text(
                    text = "آیتمی انتخاب نشده — اپ رو باز کن",
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp)
                )
            } else {
                items.take(5).forEach { (_, item) ->
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Text(
                            text = item.displayName,
                            style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp),
                            modifier = GlanceModifier.padding(end = 8.dp)
                        )
                        Column(modifier = GlanceModifier.fillMaxWidth()) {}
                        val priceText = item.priceValue?.let { formatPrice(it) } ?: item.price ?: "--"
                        Text(
                            text = priceText,
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        val changePct = item.changePercent ?: 0.0
                        val changeColor = if (changePct >= 0) Color(0xFF32D74B) else Color(0xFFFF453A)
                        Text(
                            text = "  ${if (changePct >= 0) "+" else ""}${"%.1f".format(changePct)}%",
                            style = TextStyle(color = ColorProvider(changeColor), fontSize = 12.sp),
                            modifier = GlanceModifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        }
    }

    private fun formatPrice(value: Double): String {
        return "%,.0f".format(value)
    }
}
