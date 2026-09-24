package ir.pricewidget.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    headerIntervalSeconds: Int,
    headerItemCount: Int,
    homeViewMode: String,
    onSave: (headerInterval: Int, headerItemCount: Int, viewMode: String) -> Unit,
    onBack: () -> Unit
) {
    var draftInterval by remember { mutableStateOf(headerIntervalSeconds) }
    var draftCount by remember { mutableStateOf(headerItemCount) }
    var draftViewMode by remember { mutableStateOf(homeViewMode) }
    val dirty = draftInterval != headerIntervalSeconds || draftCount != headerItemCount || draftViewMode != homeViewMode

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = { onSave(draftInterval, draftCount, draftViewMode) },
                    enabled = dirty,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("ذخیره تغییرات")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            SettingsSectionTitle("نوع نمایش صفحه‌ی اصلی")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ViewModeOption(
                    icon = Icons.Filled.GridView,
                    label = "شبکه‌ای",
                    selected = draftViewMode == "grid",
                    modifier = Modifier.weight(1f),
                    onClick = { draftViewMode = "grid" }
                )
                ViewModeOption(
                    icon = Icons.Filled.ViewList,
                    label = "لیستی",
                    selected = draftViewMode == "list",
                    modifier = Modifier.weight(1f),
                    onClick = { draftViewMode = "list" }
                )
            }

            Spacer(Modifier.height(24.dp))
            SettingsSectionTitle("چرخش خودکار هدر")

            Text(
                "هر چند ثانیه، کارتِ بالای صفحه به آیتم بعدی بره",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = draftInterval.toFloat(),
                    onValueChange = { draftInterval = it.toInt() },
                    valueRange = 3f..15f,
                    steps = 11,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text("${draftInterval}s", style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "چند تا آیتم توی هدر بچرخه",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (draftCount > 1) draftCount-- },
                    enabled = draftCount > 1
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "کم کردن")
                }
                Text(
                    "$draftCount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(32.dp)
                )
                IconButton(
                    onClick = { if (draftCount < 5) draftCount++ },
                    enabled = draftCount < 5
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "زیاد کردن")
                }
            }

            Spacer(Modifier.height(24.dp))
            SettingsSectionTitle("درباره‌ی برنامه")

            Text(
                "توسعه‌دهنده: شاهین صفایی",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            val context = androidx.compose.ui.platform.LocalContext.current
            TextButton(onClick = {
                try {
                    val appIntent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("instagram://user?username=shahinsafaei")
                    ).apply { setPackage("com.instagram.android") }
                    context.startActivity(appIntent)
                } catch (e: Exception) {
                    try {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://instagram.com/shahinsafaei")
                            )
                        )
                    } catch (e2: Exception) {
                        // مرورگر یا اینستاگرام روی گوشی نیست؛ بی‌سروصدا رد می‌شیم
                    }
                }
            }) {
                Text("اینستاگرام توسعه‌دهنده")
            }
        }
    }
}

@Composable
private fun ViewModeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}