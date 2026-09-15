package ir.pricewidget.app.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import ir.pricewidget.app.R
import ir.pricewidget.app.data.ApiService
import ir.pricewidget.app.data.GoldCurrencyResponse
import ir.pricewidget.app.data.PrefsRepository
import ir.pricewidget.app.widget.PriceWidget
import ir.pricewidget.app.widget.PriceWidgetReceiver
import ir.pricewidget.app.work.WorkScheduler
import kotlinx.coroutines.launch

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
fun AppScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { PrefsRepository(context) }
    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var response by remember { mutableStateOf<GoldCurrencyResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastUpdated by remember { mutableStateOf<String?>(null) }

    var isDark by remember { mutableStateOf(false) }
    var limitMessage by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val apiKey = repo.getApiKeyOnce()
        try {
            val result = ApiService.create().getGoldCurrency(apiKey)
            response = result
            val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            repo.saveCache(result, now)
            lastUpdated = now
            error = null
            PriceWidget().updateAll(context)
        } catch (e: Exception) {
            response = repo.getCachedOnce()
            error = "اتصال برقرار نشد — آخرین دادهٔ ذخیره‌شده نمایش داده می‌شود"
        }
    }

    LaunchedEffect(Unit) {
        selected = repo.getSelectedItemsOnce()
        isDark = repo.isDarkWidgetOnce()
        refresh()
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium
                )
                IconButton(onClick = {
                    scope.launch {
                        refreshing = true
                        refresh()
                        refreshing = false
                    }
                }) {
                    if (refreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "بروزرسانی")
                    }
                }
            }
            Text(
                lastUpdated?.let { "آخرین بروزرسانی: $it" } ?: "حداکثر ۳ آیتم برای ویجت انتخاب کن",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("تم ویجت:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
                SegmentedThemeToggle(isDark = isDark) { dark ->
                    isDark = dark
                    scope.launch {
                        repo.setWidgetDark(dark)
                        PriceWidget().updateAll(context)
                    }
                }
            }
        }

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            )
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
                    Text("دیتایی دریافت نشد. اتصال اینترنت رو چک کن.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val grouped = allItems.groupBy { it.first }
                    grouped.forEach { (category, categoryItems) ->
                        item {
                            Text(
                                category,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp, start = 4.dp)
                            )
                        }
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                categoryItems.forEachIndexed { index, (_, priceItem) ->
                                    val key = priceItem.itemKey
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Checkbox(
                                            checked = selected.contains(key),
                                            onCheckedChange = { checked ->
                                                if (checked && selected.size >= 3) {
                                                    limitMessage = "حداکثر ۳ آیتم برای ویجت قابل انتخابه"
                                                } else {
                                                    selected = if (checked) selected + key else selected - key
                                                    limitMessage = null
                                                    scope.launch {
                                                        repo.setSelectedItems(selected)
                                                        PriceWidget().updateAll(context)
                                                    }
                                                }
                                            }
                                        )
                                        Text(
                                            priceItem.displayName,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                priceItem.priceValue?.let { "%,.0f".format(it) }
                                                    ?: priceItem.price ?: "--",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            val pct = priceItem.changePercent
                                            if (pct != null) {
                                                val positive = pct >= 0
                                                Text(
                                                    "${if (positive) "+" else ""}${"%.1f".format(pct)}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (positive) androidx.compose.ui.graphics.Color(0xFF32D74B)
                                                            else androidx.compose.ui.graphics.Color(0xFFFF453A)
                                                )
                                            }
                                        }
                                    }
                                    if (index != categoryItems.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 14.dp),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("افزودن ویجت به صفحه اصلی")
            }

            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.developer_credit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}
