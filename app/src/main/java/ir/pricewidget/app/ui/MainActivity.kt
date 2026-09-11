package ir.pricewidget.app.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.pricewidget.app.data.ApiService
import ir.pricewidget.app.data.GoldCurrencyResponse
import ir.pricewidget.app.data.PrefsRepository
import androidx.glance.appwidget.updateAll
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { PrefsRepository(context) }
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var response by remember { mutableStateOf<GoldCurrencyResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        apiKey = repo.getApiKeyOnce()
        selected = repo.getSelectedItemsOnce()
        try {
            response = ApiService.create().getGoldCurrency(apiKey)
            response?.let { repo.saveCache(it, "الان") }
        } catch (e: Exception) {
            response = repo.getCachedOnce()
            error = "اتصال برقرار نشد؛ آخرین دادهٔ ذخیره‌شده نمایش داده می‌شه."
        }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("نرخ ارز و طلا", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "آیتم‌هایی که میخوای توی ویجت ببینی رو انتخاب کن",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val allItems = response?.allItems() ?: emptyList()
            if (allItems.isEmpty()) {
                Text("دیتایی دریافت نشد. کلید API یا اتصال اینترنت رو چک کن.")
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    val grouped = allItems.groupBy { it.first }
                    grouped.forEach { (category, categoryItems) ->
                        item {
                            Text(
                                category,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(categoryItems) { (_, priceItem) ->
                            val key = priceItem.itemKey
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = selected.contains(key),
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + key else selected - key
                                    }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(priceItem.displayName)
                                }
                                Text(priceItem.price?.let { "%,.0f".format(it) } ?: "--")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        repo.setSelectedItems(selected)
                        response?.let { repo.saveCache(it, "الان") }
                        PriceWidget().updateAll(context)
                        WorkScheduler.runOnce(context)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("ذخیره و به‌روزرسانی ویجت")
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
                    val provider = ComponentName(context, PriceWidgetReceiver::class.java)
                    if (appWidgetManager.isRequestPinAppWidgetSupported) {
                        appWidgetManager.requestPinAppWidget(provider, null, null)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("افزودن ویجت به صفحه اصلی گوشی")
        }

        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(ir.pricewidget.app.R.string.developer_credit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
