package ir.pricewidget.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pricewidget.app.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    notifEnabled: Boolean,
    onNotifToggle: (Boolean) -> Unit,
    headerIntervalSeconds: Int,
    onHeaderIntervalChange: (Int) -> Unit,
    onOpenWidgetDialog: () -> Unit,
    onBack: () -> Unit
) {
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            SettingsSectionTitle("اعلان‌ها و ویجت")

            SettingsRow(
                icon = Icons.Filled.Notifications,
                title = "اعلان‌های نرخ لحظه‌ای",
                subtitle = "نمایش یه نوتیفیکیشن ثابت با آخرین قیمت‌ها"
            ) {
                Switch(checked = notifEnabled, onCheckedChange = onNotifToggle)
            }

            Spacer(Modifier.height(8.dp))

            SettingsRow(
                icon = Icons.Filled.Widgets,
                title = "ویجت صفحه‌ی اصلی",
                subtitle = "افزودن یا تغییر ظاهر ویجت",
                onClick = onOpenWidgetDialog
            ) {
                Text("تنظیم", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
                    value = headerIntervalSeconds.toFloat(),
                    onValueChange = { onHeaderIntervalChange(it.toInt()) },
                    valueRange = 3f..15f,
                    steps = 11,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text("${headerIntervalSeconds}s", style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(24.dp))
            SettingsSectionTitle("درباره‌ی برنامه")

            Text(
                "نسخه ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
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
private fun SettingsSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
        }
        trailing()
    }
}