package ir.pricewidget.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ir.pricewidget.app.data.GoldCurrencyResponse
import ir.pricewidget.app.data.PrefsRepository
import ir.pricewidget.app.data.PriceItem

private data class Palette(
    val cardBg: Color,
    val subCardBg: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

private val LightPalette = Palette(
    cardBg = Color(0xFFF2F2F7),
    subCardBg = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF1C1C1E),
    textSecondary = Color(0xFF8E8E93)
)

private val DarkPalette = Palette(
    cardBg = Color(0xFF17171A),
    subCardBg = Color(0xFF2C2C2E),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA0A0A5)
)

private val Positive = Color(0xFF34C759)
private val Negative = Color(0xFFFF3B30)

private val SMALL = DpSize(140.dp, 140.dp)
private val LARGE = DpSize(280.dp, 260.dp)

class PriceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = PrefsRepository(context)
        val selected = repo.getSelectedItemsOnce()
        val cached = repo.getCachedOnce()
        val dark = repo.isDarkWidgetOnce()

        provideContent {
            WidgetContent(cached, selected, if (dark) DarkPalette else LightPalette)
        }
    }

    @Composable
    private fun WidgetContent(response: GoldCurrencyResponse?, selectedKeys: Set<String>, palette: Palette) {
        val items = response?.allItems()
            ?.map { it.second }
            ?.filter { it.itemKey in selectedKeys }
            ?.take(3)
            ?: emptyList()

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(palette.cardBg)
                .cornerRadius(22.dp)
                .padding(10.dp)
        ) {
            when {
                items.isEmpty() -> EmptyState(palette)
                items.size == 1 -> SingleItemLayout(items.first(), palette)
                else -> GridLayout(items, palette)
            }
        }
    }

    @Composable
    private fun EmptyState(palette: Palette) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "آیتمی انتخاب نشده\nاپ رو باز کن",
                style = TextStyle(color = ColorProvider(palette.textSecondary), fontSize = 12.sp, textAlign = TextAlign.Center)
            )
        }
    }

    @Composable
    private fun SingleItemLayout(item: PriceItem, palette: Palette) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(palette.subCardBg)
                .cornerRadius(18.dp)
                .padding(14.dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Badge(item, small = true)
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        item.displayName,
                        style = TextStyle(color = ColorProvider(palette.textSecondary), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    )
                }
                Spacer(modifier = GlanceModifier.height(10.dp))
                ChangeText(item, fontSize = 13.sp)
                Text(
                    item.priceValue?.let { "%,.0f".format(it) } ?: item.price ?: "--",
                    style = TextStyle(color = ColorProvider(palette.textPrimary), fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
        }
    }

    @Composable
    private fun GridLayout(items: List<PriceItem>, palette: Palette) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                RowCard(item, palette, GlanceModifier.fillMaxWidth().defaultWeight())
                if (index != items.lastIndex) {
                    Spacer(modifier = GlanceModifier.height(8.dp))
                }
            }
        }
    }

    @Composable
    private fun RowCard(item: PriceItem, palette: Palette, modifier: GlanceModifier) {
        Row(
            modifier = modifier
                .background(palette.subCardBg)
                .cornerRadius(16.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Badge(item, small = true)
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                item.displayName,
                style = TextStyle(color = ColorProvider(palette.textPrimary), fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Column(horizontalAlignment = Alignment.Horizontal.End) {
                ChangeText(item, fontSize = 10.sp)
                Text(
                    item.priceValue?.let { "%,.0f".format(it) } ?: item.price ?: "--",
                    style = TextStyle(color = ColorProvider(palette.textPrimary), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
        }
    }

    @Composable
    private fun ChangeText(item: PriceItem, fontSize: androidx.compose.ui.unit.TextUnit) {
        val pct = item.changePercent ?: return
        val positive = pct >= 0
        Text(
            "${if (positive) "▲" else "▼"} ${"%.1f".format(kotlin.math.abs(pct))}%",
            style = TextStyle(
                color = ColorProvider(if (positive) Positive else Negative),
                fontSize = fontSize,
                fontWeight = FontWeight.Medium
            )
        )
    }

    @Composable
    private fun Badge(item: PriceItem, small: Boolean = false) {
        val (emoji, color) = badgeFor(item.symbol)
        val dim = if (small) 20.dp else 28.dp
        Box(
            modifier = GlanceModifier.size(dim).background(color).cornerRadius(dim / 2),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, style = TextStyle(fontSize = if (small) 10.sp else 14.sp))
        }
    }

    private fun badgeFor(symbol: String?): Pair<String, Color> = when {
        symbol == null -> "؟" to Color(0xFF8E8E93)
        symbol.contains("USD") -> "$" to Color(0xFF2E7D32)
        symbol.contains("EUR") -> "€" to Color(0xFF1565C0)
        symbol.contains("GBP") -> "£" to Color(0xFF6A1B9A)
        symbol.contains("GOLD") || symbol.contains("COIN") -> "🪙" to Color(0xFFD4A017)
        symbol.contains("BTC") -> "₿" to Color(0xFFF7931A)
        symbol.contains("ETH") -> "Ξ" to Color(0xFF627EEA)
        else -> symbol.take(1) to Color(0xFF546E7A)
    }
}
