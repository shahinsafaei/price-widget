package ir.pricewidget.app.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.input.nestedscroll.nestedScroll
import ir.pricewidget.app.R
import ir.pricewidget.app.data.ApiService
import ir.pricewidget.app.data.GoldCurrencyResponse
import ir.pricewidget.app.data.PrefsRepository
import ir.pricewidget.app.data.PriceFormat
import ir.pricewidget.app.widget.PriceWidget
import ir.pricewidget.app.widget.PriceWidgetReceiver
import ir.pricewidget.app.work.WorkScheduler
import kotlinx.coroutines.launch
import java.util.Locale



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WorkScheduler.schedule(this)

        setContent {
            PriceWidgetTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppScreen()
                }
            }
        }
    }
}

@Composable
fun RollingNumberText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    // Bug fix (v2.9.3): when the device language is Persian, the overall layout
    // direction becomes RTL. Since this Row lays out one Text per digit/comma,
    // an RTL layout direction reverses the order of those children — so
    // "32,363,000" was rendered as "000,363,32". Numbers must always read
    // left-to-right regardless of system language, so we pin this Row (and
    // everything inside it) to LTR explicitly.
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
    ) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.Start
        ) {
            text.forEach { char ->
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        (slideInVertically(animationSpec = tween(320)) { h -> h } )
                            .togetherWith(slideOutVertically(animationSpec = tween(320)) { h -> -h })
                    },
                    label = "digit"
                ) { c ->
                    Text(
                        c.toString(),
                        style = style.copy(
                            textDirection = androidx.compose.ui.text.style.TextDirection.Ltr
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SegmentedThemeToggle(isDark: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.background)
    ) {
        listOf(false to "روشن", true to "تیره").forEach { (dark, label) ->
            val selected = isDark == dark
            Text(
                label,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onChange(dark) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun LazyRowCategoryChips(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
    ) {
        item {
            CategoryChip(label = "همه", isSelected = selected == null, onClick = { onSelect(null) })
        }
        items(categories) { category ->
            CategoryChip(label = category, isSelected = selected == category, onClick = { onSelect(category) })
        }
    }
}

@Composable
private fun CategoryChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (isSelected) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

@Composable
fun FeaturedPriceCard(
    item: ir.pricewidget.app.data.PriceItem,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null
) {
    val pct = item.changePercent
    val positive = (pct ?: 0.0) >= 0
    val trendColor = if (positive) androidx.compose.ui.graphics.Color(0xFF32D74B) else androidx.compose.ui.graphics.Color(0xFFFF453A)
    // The whole card is pinned to RTL explicitly. Reasoning: alignment/order
    // (Row child order, Alignment.TopStart/End) is resolved by the PARENT
    // layout node using whatever LocalLayoutDirection was active where THAT
    // parent was composed — a provider wrapped only around one child (like
    // just the star) has no effect on how its parent places it, so the whole
    // subtree needs the override together. Some phones resolve this app's
    // ambient layout direction as LTR even with Persian text (it follows the
    // phone's system language setting, not the string content), which is
    // what caused the name/flag/star to land on the wrong sides before.
    // Forcing RTL here guarantees: name+number on the right, flag on the
    // left, star top-right — identical on every device.
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
    ) {
        Box(
            modifier = modifier
                .shadow(6.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .background(trendColor.copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Text content on the reading side (right) — full independent
                // column, nothing ever overlaps it.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    Text(
                        item.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (pct != null) {
                        Text(
                            "${if (positive) "+" else ""}${"%.1f".format(Locale.US, pct)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = trendColor
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        RollingNumberText(
                            text = item.priceValue?.let { PriceFormat.format(it) } ?: item.price ?: "--",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        if (!item.unit.isNullOrBlank()) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                item.unit,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
                // Flag lives entirely on the other side (left), in its own lane
                // — no separate background, just the glyph.
                Box(
                    modifier = Modifier
                        .width(88.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        ir.pricewidget.app.data.IconMap.flag(item.symbol),
                        fontSize = 48.sp
                    )
                }
            }
            // Star sits in the true top-right corner of the card as an
            // overlay, clear of both the name and the flag.
            if (onRemove != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(26.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                        .clickable { onRemove() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("★", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun PriceCard(
    priceItem: ir.pricewidget.app.data.PriceItem,
    checked: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit
) {
    val pct = priceItem.changePercent
    val positive = (pct ?: 0.0) >= 0
    val trendTint = if (pct == null) MaterialTheme.colorScheme.surface
        else if (positive) androidx.compose.ui.graphics.Color(0xFF32D74B).copy(alpha = 0.07f)
        else androidx.compose.ui.graphics.Color(0xFFFF453A).copy(alpha = 0.07f)
    Column(
        modifier = modifier
            .alpha(if (enabled || checked) 1f else 0.35f)
            .shadow(if (checked) 4.dp else 1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .background(trendTint)
            .then(
                if (checked) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                else Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            )
            .clickable(enabled = enabled || checked) { onToggle(!checked) }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                ir.pricewidget.app.data.IconMap.flag(priceItem.symbol),
                style = MaterialTheme.typography.titleMedium
            )
            if (checked) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            priceItem.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        if (pct != null) {
            Text(
                "${if (positive) "+" else ""}${"%.1f".format(Locale.US, pct)}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (positive) androidx.compose.ui.graphics.Color(0xFF32D74B)
                        else androidx.compose.ui.graphics.Color(0xFFFF453A)
            )
        }
        RollingNumberText(
            text = priceItem.priceValue?.let { PriceFormat.format(it) } ?: priceItem.price ?: "--",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun DailySummaryLine(items: List<ir.pricewidget.app.data.PriceItem>) {
    val withPct = items.filter { it.changePercent != null }
    if (withPct.isEmpty()) return
    val upCount = withPct.count { (it.changePercent ?: 0.0) >= 0 }
    val best = withPct.maxByOrNull { kotlin.math.abs(it.changePercent ?: 0.0) }
    val bestPositive = (best?.changePercent ?: 0.0) >= 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "امروز $upCount از ${withPct.size} مورد رشد داشتن",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f)
        )
        if (best != null) {
            Text(
                "بیشترین تغییر: ${best.displayName}",
                style = MaterialTheme.typography.labelSmall,
                color = if (bestPositive) androidx.compose.ui.graphics.Color(0xFF32D74B) else androidx.compose.ui.graphics.Color(0xFFFF453A)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeFeaturedPager(
    items: List<ir.pricewidget.app.data.PriceItem>,
    intervalSeconds: Int = 7,
    onRemove: (String) -> Unit
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })
    val itemCount = items.size

    // چرخش خودکار — بازه‌ش الان قابل‌تنظیمه (از صفحه‌ی تنظیمات)، دیگه هاردکد نیست.
    LaunchedEffect(itemCount, intervalSeconds) {
        if (itemCount <= 1) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(intervalSeconds * 1000L)
            val next = (pagerState.currentPage + 1) % itemCount
            pagerState.animateScrollToPage(next)
        }
    }

    val currentPage = pagerState.currentPage.coerceIn(0, items.size - 1)
    val offsetFraction = pagerState.currentPageOffsetFraction
    val neighborPage = when {
        offsetFraction > 0f -> (currentPage + 1).coerceAtMost(items.size - 1)
        offsetFraction < 0f -> (currentPage - 1).coerceAtLeast(0)
        else -> currentPage
    }

    fun trendColorOf(item: ir.pricewidget.app.data.PriceItem): androidx.compose.ui.graphics.Color {
        val positive = (item.changePercent ?: 0.0) >= 0
        return if (positive) androidx.compose.ui.graphics.Color(0xFF32D74B) else androidx.compose.ui.graphics.Color(0xFFFF453A)
    }

    val progress = kotlin.math.abs(offsetFraction).coerceIn(0f, 1f)
    val ambientColor = androidx.compose.ui.graphics.lerp(
        trendColorOf(items[currentPage]),
        trendColorOf(items[neighborPage]),
        progress
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .background(ambientColor.copy(alpha = 0.05f)),
            contentPadding = PaddingValues(horizontal = 36.dp),
            pageSpacing = 12.dp
        ) { page ->
            FeaturedPriceCard(
                items[page],
                modifier = Modifier.fillMaxWidth(),
                onRemove = { onRemove(items[page].itemKey) }
            )
        }
        if (items.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(items.size) { index ->
                    val active = currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (active) 7.dp else 5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${currentPage + 1} / ${items.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
fun HomeGridCard(
    priceItem: ir.pricewidget.app.data.PriceItem,
    modifier: Modifier = Modifier,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onRemove: () -> Unit
) {
    val pct = priceItem.changePercent
    val positive = (pct ?: 0.0) >= 0
    val trendTint = if (pct == null) MaterialTheme.colorScheme.surface
        else if (positive) androidx.compose.ui.graphics.Color(0xFF32D74B).copy(alpha = 0.07f)
        else androidx.compose.ui.graphics.Color(0xFFFF453A).copy(alpha = 0.07f)
    Column(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .background(trendTint)
            .padding(9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                ir.pricewidget.app.data.IconMap.flag(priceItem.symbol),
                fontSize = 24.sp
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "حذف",
                    modifier = Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            priceItem.displayName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
        Spacer(Modifier.height(5.dp))
        if (pct != null) {
            Text(
                "${if (positive) "+" else ""}${"%.1f".format(Locale.US, pct)}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (positive) androidx.compose.ui.graphics.Color(0xFF32D74B)
                        else androidx.compose.ui.graphics.Color(0xFFFF453A)
            )
        }
        RollingNumberText(
            text = priceItem.priceValue?.let { PriceFormat.format(it) } ?: priceItem.price ?: "--",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
        )
        if (!priceItem.unit.isNullOrBlank()) {
            Text(
                priceItem.unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1
            )
        }
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .alpha(if (canMoveEarlier) 1f else 0.25f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = canMoveEarlier) { onMoveEarlier() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "‹",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .alpha(if (canMoveLater) 1f else 0.25f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = canMoveLater) { onMoveLater() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "›",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun HomeListRow(
    priceItem: ir.pricewidget.app.data.PriceItem,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            ir.pricewidget.app.data.IconMap.flag(priceItem.symbol ?: priceItem.itemKey),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(priceItem.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, maxLines = 1)
            if (!priceItem.unit.isNullOrBlank()) {
                Text(priceItem.unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            RollingNumberText(
                text = priceItem.priceValue?.let { PriceFormat.format(it) } ?: priceItem.price ?: "--",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
            val pct = priceItem.changePercent
            if (pct != null) {
                Text(
                    "${if (pct >= 0) "+" else ""}${"%.1f".format(java.util.Locale.US, pct)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pct >= 0) androidx.compose.ui.graphics.Color(0xFF32D74B) else androidx.compose.ui.graphics.Color(0xFFFF453A)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "حذف", modifier = Modifier.size(16.dp))
        }
    }
}


@Composable
fun AddItemTile(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = "افزودن آیتم",
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "افزودن",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun ManageItemsDialog(
    allItems: List<Pair<String, ir.pricewidget.app.data.PriceItem>>,
    homeItems: List<String>,
    onToggleHome: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val categories = allItems.map { it.first }.distinct()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("مدیریت آیتم‌ها", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "بستن")
                    }
                }

                if (categories.size > 1) {
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("جستجو…", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .height(52.dp)
                )

                limitMessage?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }

                if (categories.size > 1) {
                    LazyRowCategoryChips(
                        categories = categories,
                        selected = selectedCategory,
                        onSelect = { selectedCategory = it }
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val grouped = allItems
                        .filter { selectedCategory == null || it.first == selectedCategory }
                        .filter { query.isBlank() || it.second.displayName.contains(query, ignoreCase = true) }
                        .groupBy { it.first }
                    grouped.forEach { (category, categoryItems) ->
                        item {
                            val icon = when (category) {
                                "طلا و سکه" -> "🪙"
                                "ارز" -> "💵"
                                "ارز دیجیتال" -> "₿"
                                else -> "📊"
                            }
                            Text(
                                "$icon $category",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp)
                            )
                        }
                        items(categoryItems) { (_, priceItem) ->
                            ManageItemRow(
                                priceItem = priceItem,
                                checked = priceItem.itemKey in homeItems,
                                enabled = true,
                                onToggle = { checked -> onToggleHome(priceItem.itemKey, checked) }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ManageItemRow(
    priceItem: ir.pricewidget.app.data.PriceItem,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            ir.pricewidget.app.data.IconMap.flag(priceItem.symbol),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 10.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(priceItem.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                priceItem.priceValue?.let { PriceFormat.format(it) } ?: priceItem.price ?: "--",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onToggle
        )
    }
}

private fun isMarketOpenNow(): Boolean {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return hour in 9 until 20
}

/** "1405/07/01 16:50" — Jalali date + time, Latin digits, via ICU (available since API 24).
 *  Falls back to a plain Gregorian HH:mm if ICU's Persian calendar isn't available on this
 *  device/ROM, so a broken ICU implementation never crashes the app.
 *
 *  Bug fix: this used to always format "right now" (device clock), so a stale
 *  cached/GitHub-fetched response would still show "just updated". Pass the
 *  data's own time_unix (epoch seconds) so the label reflects when the DATA
 *  was actually generated, not when the app happened to render it. */
private fun persianDateTimeLabel(epochSeconds: Long? = null): String {
    return try {
        val cal = android.icu.util.Calendar.getInstance(android.icu.util.ULocale.forLanguageTag("fa-u-ca-persian"))
        if (epochSeconds != null) {
            cal.timeInMillis = epochSeconds * 1000L
        }
        val year = cal.get(android.icu.util.Calendar.YEAR)
        val month = cal.get(android.icu.util.Calendar.MONTH) + 1
        val day = cal.get(android.icu.util.Calendar.DAY_OF_MONTH)
        val hour = cal.get(android.icu.util.Calendar.HOUR_OF_DAY)
        val minute = cal.get(android.icu.util.Calendar.MINUTE)
        "%d/%02d/%02d %02d:%02d".format(java.util.Locale.US, year, month, day, hour, minute)
    } catch (e: Exception) {
        val date = if (epochSeconds != null) java.util.Date(epochSeconds * 1000L) else java.util.Date()
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(date)
    }
}

/** Default watchlist for first-time users: dollar, euro, 18k gold, bitcoin. */
private val DEFAULT_HOME_SYMBOLS = listOf("USD", "EUR", "IR_GOLD_18K", "BTC")

private fun defaultHomeKeys(allItems: List<Pair<String, ir.pricewidget.app.data.PriceItem>>): List<String> {
    val bySymbol = allItems.map { it.second }.associateBy { it.symbol }
    return DEFAULT_HOME_SYMBOLS.mapNotNull { bySymbol[it]?.itemKey }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { PrefsRepository(context) }
    val scope = rememberCoroutineScope()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    var selected by remember { mutableStateOf<Set<String>>(emptySet()) } // widget items (max 3)
    var homeItems by remember { mutableStateOf<List<String>>(emptyList()) } // home screen watchlist, ordered
    var response by remember { mutableStateOf<GoldCurrencyResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var showWidgetDialog by remember { mutableStateOf(false) }
    var widgetAlreadyPinned by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastUpdated by remember { mutableStateOf<String?>(null) }

    var isDark by remember { mutableStateOf(false) }
    var widgetFollowSystem by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("home") }
    var headerInterval by remember { mutableStateOf(7) }
    val snackbarHostState = remember { SnackbarHostState() }
    var notifEnabled by remember { mutableStateOf(false) }
    var limitMessage by remember { mutableStateOf<String?>(null) }
    var showOnboarding by remember { mutableStateOf(false) }
    val marketOpen = remember { isMarketOpenNow() }

    suspend fun refresh(hapticOnChange: Boolean = false) {
        try {
            val previousPrices = response?.allItems()?.associate { it.second.itemKey to it.second.price }
            val result = ApiService.create().getGoldCurrency()
            response = result
            // زمان واقعی داده (نه لحظه‌ی الان گوشی): جدیدترین time_unix بین همه‌ی آیتم‌ها
            val freshestEpoch = result.allItems().mapNotNull { it.second.timeUnix }.maxOrNull()
            val now = persianDateTimeLabel(freshestEpoch)
            repo.saveCache(result, now)
            lastUpdated = now
            error = null
            WorkScheduler.refreshWidgetNow(context)
            if (hapticOnChange && previousPrices != null) {
                val changed = result.allItems().any { (_, item) -> previousPrices[item.itemKey] != null && previousPrices[item.itemKey] != item.price }
                if (changed) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            }
        } catch (e: Exception) {
            response = repo.getCachedOnce()
            error = "اتصال برقرار نشد — آخرین دادهٔ ذخیره‌شده نمایش داده می‌شود"
        }
    }

    fun moveHomeItem(key: String, delta: Int) {
        val idx = homeItems.indexOf(key)
        if (idx < 0) return
        val newIdx = (idx + delta).coerceIn(0, homeItems.size - 1)
        if (newIdx == idx) return
        val mutable = homeItems.toMutableList()
        val moved = mutable.removeAt(idx)
        mutable.add(newIdx, moved)
        homeItems = mutable
        WorkScheduler.saveHomeItems(context, homeItems)
    }

    fun removeHomeItemWithUndo(key: String) {
        val idx = homeItems.indexOf(key)
        if (idx < 0) return
        val removedName = response?.allItems()?.firstOrNull { it.second.itemKey == key }?.second?.displayName ?: "آیتم"
        homeItems = homeItems - key
        WorkScheduler.saveHomeItems(context, homeItems)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "$removedName حذف شد",
                actionLabel = "برگردون",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                val restored = homeItems.toMutableList()
                restored.add(idx.coerceAtMost(restored.size), key)
                homeItems = restored
                WorkScheduler.saveHomeItems(context, homeItems)
            }
        }
    }

    LaunchedEffect(Unit) {
        selected = repo.getSelectedItemsOnce()
        homeItems = repo.getHomeItemsOnce()
        isDark = repo.isDarkWidgetOnce()
        widgetFollowSystem = repo.isWidgetFollowSystemOnce()
        notifEnabled = repo.isNotificationEnabledOnce()
        headerInterval = repo.getHeaderIntervalOnce()

        // بگ فیکس: اول کش رو نشون بده (اگه بود)، بعد صبر کن برای شبکه —
        // به‌جای این‌که کاربر تا جواب شبکه اسپینر ببینه حتی وقتی دیتای قدیمی داریم
        val cached = repo.getCachedOnce()
        if (cached != null) {
            response = cached
            lastUpdated = repo.getLastUpdatedOnce()
            loading = false
        }

        refresh()
        if (notifEnabled) ir.pricewidget.app.notification.RateNotifier.show(context)
        loading = false
        val onboarded = repo.isOnboardedOnce()
        if (homeItems.isEmpty()) {
            val allItems = response?.allItems() ?: emptyList()
            val defaults = defaultHomeKeys(allItems)
            if (defaults.isNotEmpty()) {
                homeItems = defaults
                repo.setHomeItems(defaults)
            }
        }
        if (!onboarded) {
            showOnboarding = true
        }
    }

    if (currentScreen == "settings") {
        SettingsScreen(
            notifEnabled = notifEnabled,
            onNotifToggle = { checked ->
                if (checked) {
                    notifEnabled = true
                    scope.launch {
                        repo.setNotificationEnabled(true)
                        ir.pricewidget.app.notification.RateNotifier.show(context)
                    }
                } else {
                    notifEnabled = false
                    scope.launch { repo.setNotificationEnabled(false) }
                    ir.pricewidget.app.notification.RateNotifier.cancel(context)
                }
            },
            headerIntervalSeconds = headerInterval,
            onHeaderIntervalChange = { seconds ->
                headerInterval = seconds
                scope.launch { repo.setHeaderInterval(seconds) }
            },
            onOpenWidgetDialog = {
                currentScreen = "home"
                scope.launch {
                    widgetAlreadyPinned = GlanceAppWidgetManager(context)
                        .getGlanceIds(PriceWidget::class.java)
                        .isNotEmpty()
                }
                showWidgetDialog = true
            },
            onBack = { currentScreen = "home" }
        )
    } else {
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (marketOpen) androidx.compose.ui.graphics.Color(0xFF32D74B) else androidx.compose.ui.graphics.Color(0xFFFF453A))
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (marketOpen) "بازار باز" else "بازار بسته",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.weight(1f)
            )
            Text(
                lastUpdated ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            IconButton(
                onClick = {
                    scope.launch {
                        refreshing = true
                        refresh(hapticOnChange = true)
                        refreshing = false
                    }
                },
                modifier = Modifier.size(28.dp)
            ) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "بروزرسانی", modifier = Modifier.size(18.dp))
                }
            }
            IconButton(
                onClick = { currentScreen = "settings" },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "تنظیمات", modifier = Modifier.size(18.dp))
            }
        }

        error?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text("⚠️", modifier = Modifier.padding(end = 8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = {
                    scope.launch { refreshing = true; refresh(); refreshing = false }
                }) {
                    Text("تلاش دوباره", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        limitMessage?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val allItems = response?.allItems() ?: emptyList()
            if (allItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📡", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "دیتایی دریافت نشد",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "اتصال اینترنت رو چک کن و دوباره تلاش کن",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { scope.launch { refreshing = true; refresh(); refreshing = false } }) {
                            Text("تلاش دوباره")
                        }
                    }
                }
            } else {
                val allItemsFlat = allItems.map { it.second }
                val homeList = homeItems.mapNotNull { key -> allItemsFlat.find { it.itemKey == key } }
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (homeList.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("⭐", style = MaterialTheme.typography.displayMedium)
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "هنوز چیزی به صفحه اصلی اضافه نکردی",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "می‌تونی با یک ضربه ۴ مورد پرطرفدار رو اضافه کنی",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.height(14.dp))
                                Button(onClick = {
                                    val defaults = defaultHomeKeys(allItems)
                                    homeItems = defaults
                                    WorkScheduler.saveHomeItems(context, defaults)
                                }) {
                                    Text("افزودن دلار، یورو، طلای ۱۸ و بیت‌کوین")
                                }
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = { showManageDialog = true }) {
                                    Text("یا انتخاب دستی")
                                }
                            }
                        }
                    } else {
                        // Header shows only the "main 3" (the widget selection if the
                        // user has one, else the first 3 by order) — the rest of the
                        // watchlist lives only in the grid below, so nothing is
                        // duplicated between header and grid.
                        val widgetInHome = homeItems.filter { it in selected }
                        val headerKeys = if (widgetInHome.isNotEmpty()) widgetInHome else homeItems.take(3)
                        var headerList = homeList.filter { it.itemKey in headerKeys }
                        if (headerList.isEmpty()) headerList = homeList.take(3)
                        val gridList = homeList.filter { it !in headerList }

                        DailySummaryLine(homeList)
                        HomeFeaturedPager(
                            items = headerList,
                            intervalSeconds = headerInterval,
                            onRemove = { key -> removeHomeItemWithUndo(key) }
                        )
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val rows = gridList.chunked(2)
                            val lastRowHasSpace = rows.isEmpty() || rows.last().size < 2
                            itemsIndexed(rows) { rowIndex, rowItems ->
                                val isLastRow = rowIndex == rows.lastIndex
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { priceItem ->
                                        val idx = homeItems.indexOf(priceItem.itemKey)
                                        HomeGridCard(
                                            priceItem = priceItem,
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            canMoveEarlier = idx > 0,
                                            canMoveLater = idx in 0 until (homeItems.size - 1),
                                            onMoveEarlier = { moveHomeItem(priceItem.itemKey, -1) },
                                            onMoveLater = { moveHomeItem(priceItem.itemKey, 1) },
                                            onRemove = { removeHomeItemWithUndo(priceItem.itemKey) }
                                        )
                                    }
                                    if (isLastRow && rowItems.size == 1) {
                                        AddItemTile(
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            onClick = { showManageDialog = true }
                                        )
                                    }
                                }
                            }
                            if (lastRowHasSpace.not()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        AddItemTile(
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            onClick = { showManageDialog = true }
                                        )
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(6.dp)) }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            val notifPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    notifEnabled = true
                    scope.launch {
                        repo.setNotificationEnabled(true)
                        ir.pricewidget.app.notification.RateNotifier.show(context)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Notification tile — matches PriceCard styling: subtle shadow,
                // surface background, faint border, colored icon badge.
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .shadow(1.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "اعلان‌ها",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Switch(
                        checked = notifEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    notifEnabled = true
                                    scope.launch {
                                        repo.setNotificationEnabled(true)
                                        ir.pricewidget.app.notification.RateNotifier.show(context)
                                    }
                                }
                            } else {
                                notifEnabled = false
                                scope.launch { repo.setNotificationEnabled(false) }
                                ir.pricewidget.app.notification.RateNotifier.cancel(context)
                            }
                        },
                        modifier = Modifier.scale(0.8f)
                    )
                }

                // Add-widget tile — same card language as the notification tile.
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .shadow(1.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .clickable {
                            scope.launch {
                                widgetAlreadyPinned = GlanceAppWidgetManager(context)
                                    .getGlanceIds(ir.pricewidget.app.widget.PriceWidget::class.java)
                                    .isNotEmpty()
                            }
                            showWidgetDialog = true
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Widgets,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "ویجت",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        val appIntent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("instagram://user?username=shahinsafaei")
                        ).apply { setPackage("com.instagram.android") }
                        try {
                            context.startActivity(appIntent)
                        } catch (e: Exception) {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://instagram.com/shahinsafaei")
                                )
                            )
                        }
                    }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_instagram),
                    contentDescription = "اینستاگرام",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "توسعه‌دهنده: شاهین صفایی",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }

    if (showOnboarding) {
        AlertDialog(
            onDismissRequest = { showOnboarding = false; scope.launch { repo.setOnboarded(true) } },
            title = { Text("خوش اومدی 👋") },
            text = {
                Text(
                    "چند مورد رو که می‌خوای دنبال کنی انتخاب کن — دلار، یورو، طلای ۱۸ و بیت‌کوین به‌صورت پیش‌فرض روی صفحه اصلی اضافه شدن. هر وقت خواستی از دکمه «افزودن آیتم» بقیه رو هم اضافه یا کم کن، و حداکثر ۳ مورد رو برای ویجت صفحه اصلی گوشی انتخاب کن.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showOnboarding = false
                    scope.launch { repo.setOnboarded(true) }
                }) { Text("باشه، متوجه شدم") }
            }
        )
    }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
    }

    if (showManageDialog) {
        ManageItemsDialog(
            allItems = allItems,
            homeItems = homeItems,
            onToggleHome = { key, checked ->
                homeItems = if (checked) homeItems + key else homeItems - key
                WorkScheduler.saveHomeItems(context, homeItems)
            },
            onDismiss = { showManageDialog = false }
        )
    }

    if (showWidgetDialog) {
        var dialogDark by remember { mutableStateOf(isDark) }
        var dialogFollowSystem by remember { mutableStateOf(widgetFollowSystem) }
        var dialogSelected by remember { mutableStateOf(selected) }
        WidgetConfigDialog(
            alreadyPinned = widgetAlreadyPinned,
            allItems = response?.allItems() ?: emptyList(),
            selectedItems = dialogSelected,
            onToggleItem = { key, checked ->
                dialogSelected = if (checked) dialogSelected + key else dialogSelected - key
            },
            isDark = dialogDark,
            followSystem = dialogFollowSystem,
            onThemeChange = { dark -> dialogDark = dark },
            onFollowSystemChange = { follow -> dialogFollowSystem = follow },
            onDismiss = { showWidgetDialog = false },
            onConfirm = {
                showWidgetDialog = false
                isDark = dialogDark
                widgetFollowSystem = dialogFollowSystem
                selected = dialogSelected
                WorkScheduler.saveWidgetTheme(context, dialogDark, dialogFollowSystem)
                WorkScheduler.saveWidgetItems(context, dialogSelected)
                scope.launch {
                    val alreadyPinned = GlanceAppWidgetManager(context)
                        .getGlanceIds(PriceWidget::class.java)
                        .isNotEmpty()

                    if (alreadyPinned) {
                        android.widget.Toast.makeText(
                            context,
                            "تنظیمات ویجت ذخیره و اعمال شد",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
                        val provider = ComponentName(context, PriceWidgetReceiver::class.java)
                        if (appWidgetManager.isRequestPinAppWidgetSupported) {
                            android.widget.Toast.makeText(
                                context,
                                "درخواست افزودن ویجت ارسال شد — تایید کن",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            appWidgetManager.requestPinAppWidget(provider, null, null)
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "این گوشی افزودن خودکار ویجت رو پشتیبانی نمی‌کنه؛ دستی از صفحه اصلی اضافه کن",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "ویجت رو از صفحه اصلی گوشی دستی اضافه کن",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }
}

@Composable
fun WidgetConfigDialog(
    alreadyPinned: Boolean,
    allItems: List<Pair<String, ir.pricewidget.app.data.PriceItem>>,
    selectedItems: Set<String>,
    onToggleItem: (String, Boolean) -> Unit,
    isDark: Boolean,
    followSystem: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onFollowSystemChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val categories = allItems.map { it.first }.distinct()
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val previewItem = allItems.map { it.second }.firstOrNull { it.itemKey in selectedItems }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تنظیم ویجت", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "بستن")
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text("تم ویجت", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)
                    ) {
                        WidgetPreviewOption(
                            label = "روشن",
                            dark = false,
                            selected = !followSystem && !isDark,
                            previewItem = previewItem,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onFollowSystemChange(false); onThemeChange(false) }
                        )
                        WidgetPreviewOption(
                            label = "تیره",
                            dark = true,
                            selected = !followSystem && isDark,
                            previewItem = previewItem,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onFollowSystemChange(false); onThemeChange(true) }
                        )
                        AutoThemeOption(
                            selected = followSystem,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onFollowSystemChange(true) }
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("آیتم‌های ویجت", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "${selectedItems.size}/3",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selectedItems.size >= 3) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("جستجو…", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .height(52.dp)
                )

                if (categories.size > 1) {
                    LazyRowCategoryChips(
                        categories = categories,
                        selected = selectedCategory,
                        onSelect = { selectedCategory = it }
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val grouped = allItems
                        .filter { selectedCategory == null || it.first == selectedCategory }
                        .filter { query.isBlank() || it.second.displayName.contains(query, ignoreCase = true) }
                        .groupBy { it.first }
                    grouped.forEach { (category, categoryItems) ->
                        item {
                            val icon = when (category) {
                                "طلا و سکه" -> "🪙"
                                "ارز" -> "💵"
                                "ارز دیجیتال" -> "₿"
                                else -> "📊"
                            }
                            Text(
                                "$icon $category",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp)
                            )
                        }
                        items(categoryItems) { (_, priceItem) ->
                            val inWidget = priceItem.itemKey in selectedItems
                            ManageItemRow(
                                priceItem = priceItem,
                                checked = inWidget,
                                enabled = selectedItems.size < 3 || inWidget,
                                onToggle = { checked -> onToggleItem(priceItem.itemKey, checked) }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        enabled = selectedItems.isNotEmpty()
                    ) {
                        Text(if (alreadyPinned) "ذخیره تغییرات" else "افزودن ویجت")
                    }
                }
            }
        }
    }
}



@Composable
private fun AutoThemeOption(
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                else Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            )
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            Box(Modifier.weight(1f).fillMaxHeight().background(androidx.compose.ui.graphics.Color(0xFFF2F2F7)))
            Box(Modifier.weight(1f).fillMaxHeight().background(androidx.compose.ui.graphics.Color(0xFF17171A)))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "خودکار",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}


@Composable
private fun WidgetPreviewOption(
    label: String,
    dark: Boolean,
    selected: Boolean,
    previewItem: ir.pricewidget.app.data.PriceItem?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (dark) androidx.compose.ui.graphics.Color(0xFF17171A) else androidx.compose.ui.graphics.Color(0xFFF2F2F7)
    val cardBg = if (dark) androidx.compose.ui.graphics.Color(0xFF2C2C2E) else androidx.compose.ui.graphics.Color.White
    val textColor = if (dark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF1C1C1E)

    val previewName = previewItem?.displayName ?: "دلار"
    val previewPct = previewItem?.changePercent
    val previewPctText = if (previewPct != null) "${if (previewPct >= 0) "+" else ""}${"%.1f".format(java.util.Locale.US, previewPct)}%" else "+1.2%"
    val previewPctColor = if (previewPct == null || previewPct >= 0) androidx.compose.ui.graphics.Color(0xFF32D74B) else androidx.compose.ui.graphics.Color(0xFFFF453A)
    val previewPrice = previewItem?.priceValue?.let { ir.pricewidget.app.data.PriceFormat.format(it) } ?: previewItem?.price ?: "235,975"

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                else Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            )
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(cardBg)
                .padding(8.dp)
        ) {
            Text(previewName, style = MaterialTheme.typography.labelSmall, color = textColor, maxLines = 1)
            Text(previewPctText, style = MaterialTheme.typography.labelSmall, color = previewPctColor)
            Text(previewPrice, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}
