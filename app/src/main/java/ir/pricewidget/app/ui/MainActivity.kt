package ir.pricewidget.app.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
fun PriceCard(
    priceItem: ir.pricewidget.app.data.PriceItem,
    checked: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit
) {
    val pct = priceItem.changePercent
    val positive = (pct ?: 0.0) >= 0
    Column(
        modifier = modifier
            .alpha(if (enabled || checked) 1f else 0.35f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
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
                "${if (positive) "+" else ""}${"%.1f".format(pct)}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (positive) androidx.compose.ui.graphics.Color(0xFF32D74B)
                        else androidx.compose.ui.graphics.Color(0xFFFF453A)
            )
        }
        Text(
            priceItem.priceValue?.let { "%,.0f".format(it) } ?: priceItem.price ?: "--",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
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
    var showWidgetDialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastUpdated by remember { mutableStateOf<String?>(null) }

    var isDark by remember { mutableStateOf(false) }
    var notifEnabled by remember { mutableStateOf(false) }
    var limitMessage by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        try {
            val result = ApiService.create().getGoldCurrency()
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
        notifEnabled = repo.isNotificationEnabledOnce()
        refresh()
        if (notifEnabled) ir.pricewidget.app.notification.RateNotifier.show(context)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        lastUpdated?.let { "بروزرسانی: $it" } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    IconButton(
                        onClick = {
                            scope.launch {
                                refreshing = true
                                refresh()
                                refreshing = false
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "بروزرسانی", modifier = Modifier.size(18.dp))
                        }
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
                        val rows = categoryItems.chunked(2)
                        items(rows) { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowItems.forEach { (_, priceItem) ->
                                    PriceCard(
                                        priceItem = priceItem,
                                        checked = selected.contains(priceItem.itemKey),
                                        enabled = selected.size < 3,
                                        modifier = Modifier.weight(1f),
                                        onToggle = { checked ->
                                            val key = priceItem.itemKey
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
                                }
                                if (rowItems.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(10.dp))
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
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("نمایش در نوار اعلان‌ها", style = MaterialTheme.typography.bodyMedium)
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
                    }
                )
            }

            Button(
                onClick = { showWidgetDialog = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("افزودن ویجت به صفحه اصلی")
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "طراحی و توسعه: شاهین صفایی",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
                Text(
                    " · @shahinsafaei",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        val intent = try {
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("instagram://user?username=shahinsafaei")
                            ).apply { setPackage("com.instagram.android") }
                        } catch (e: Exception) { null }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://instagram.com/shahinsafaei")
                                )
                            )
                        }
                    }
                )
            }
        }
    }

    if (showWidgetDialog) {
        var dialogDark by remember { mutableStateOf(isDark) }
        WidgetSetupDialog(
            isDark = dialogDark,
            onThemeChange = { dark -> dialogDark = dark },
            onDismiss = { showWidgetDialog = false },
            onConfirm = {
                showWidgetDialog = false
                isDark = dialogDark
                scope.launch {
                    repo.setWidgetDark(dialogDark)
                    PriceWidget().updateAll(context)
                }
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
            }
        )
    }
}

@Composable
fun WidgetSetupDialog(
    isDark: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ظاهر ویجت رو انتخاب کن") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WidgetPreviewOption(
                        label = "روشن",
                        dark = false,
                        selected = !isDark,
                        modifier = Modifier.weight(1f),
                        onClick = { onThemeChange(false) }
                    )
                    WidgetPreviewOption(
                        label = "تیره",
                        dark = true,
                        selected = isDark,
                        modifier = Modifier.weight(1f),
                        onClick = { onThemeChange(true) }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "بعداً هم از همین صفحه می‌تونی تم رو عوض کنی",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("افزودن ویجت") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
private fun WidgetPreviewOption(
    label: String,
    dark: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (dark) androidx.compose.ui.graphics.Color(0xFF17171A) else androidx.compose.ui.graphics.Color(0xFFF2F2F7)
    val cardBg = if (dark) androidx.compose.ui.graphics.Color(0xFF2C2C2E) else androidx.compose.ui.graphics.Color.White
    val textColor = if (dark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF1C1C1E)

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
            Text("دلار", style = MaterialTheme.typography.labelSmall, color = textColor)
            Text("+1.2%", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(0xFF32D74B))
            Text("235,975", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}
