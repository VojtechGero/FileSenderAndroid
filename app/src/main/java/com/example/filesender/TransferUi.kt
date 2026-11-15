package com.example.filesender

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.filesender.ui.theme.Pine
import com.example.filesender.ui.theme.Sprout

@Composable
internal fun Glyph(@DrawableRes id: Int, modifier: Modifier = Modifier.size(22.dp)) {
    Icon(painterResource(id), contentDescription = null, modifier = modifier)
}

@Composable
internal fun FileSenderHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Surface(shape = RoundedCornerShape(9.dp), color = Pine, contentColor = Sprout) {
            Glyph(R.drawable.fs_transfer, Modifier.padding(6.dp).size(22.dp))
        }
        Text("FileSender", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun PageHeading(text: String) {
    Text(text, modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
}

@Composable
internal fun Panel(content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
internal fun PanelTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun DetailText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun RecipientCard(name: String, address: String, enabled: Boolean, choose: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Pine, contentColor = Color.White) {
        Row(Modifier.clickable(enabled = enabled, role = Role.Button, onClickLabel = "Vybrat počítač", onClick = choose)
            .padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(11.dp), color = Sprout, contentColor = Pine) {
                Glyph(R.drawable.fs_monitor, Modifier.padding(10.dp).size(26.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Příjemce", style = MaterialTheme.typography.labelMedium, color = Color(0xFFBAD2C2))
                Text(name.ifEmpty { "Vybrat počítač" }, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (address.isNotBlank() && address != name) {
                    Text(address, style = MaterialTheme.typography.bodySmall, color = Color(0xFFCEDECF))
                }
            }
            Glyph(R.drawable.fs_forward, Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun DevicesPanel(
    discovery: DeviceDiscovery?, devices: List<DiscoveredDevice>, enabled: Boolean, selectedUrl: String, address: String,
    onAddressChange: (String) -> Unit, onRefresh: () -> Unit, onScan: () -> Unit,
    onPick: (String, String) -> Unit, onManualPick: () -> Unit, onPing: (String) -> Unit
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelTitle("V místní síti", Modifier.weight(1f))
            TextButton(enabled = enabled, onClick = onRefresh) {
                Glyph(R.drawable.fs_refresh, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Obnovit")
            }
        }
        if (devices.isEmpty()) {
            DetailText("Zatím žádný počítač. Na desktopu spusť příjem a připoj obě zařízení ke stejné síti.")
        }
        if (discovery != null && (devices.isEmpty() || !discovery.isSearching)) {
            Text(discovery.status, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        for (device in devices) {
            val selected = selectedUrl == device.baseUrl
            Surface(shape = RoundedCornerShape(10.dp), color = if (selected) colors.primaryContainer else colors.surfaceVariant) {
                Row(modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button) {
                    onPick(device.name, device.baseUrl)
                }.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Glyph(R.drawable.fs_monitor)
                    Column(Modifier.weight(1f)) {
                        Text(device.name, fontWeight = FontWeight.SemiBold)
                        DetailText(device.baseUrl)
                    }
                    Glyph(if (selected) R.drawable.fs_check else R.drawable.fs_forward, Modifier.size(17.dp))
                }
            }
        }
    }
    Panel {
        PanelTitle("Další možnosti připojení")
        OutlinedButton(onClick = onScan, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Glyph(R.drawable.fs_qr)
            Spacer(Modifier.width(8.dp))
            Text("Skenovat QR")
        }
        TextButton(onClick = { editing = !editing }, enabled = enabled) {
            Text(if (editing) "Skrýt ruční zadání" else "Zadat adresu ručně")
        }
        if (editing) {
            OutlinedTextField(value = address, onValueChange = onAddressChange, enabled = enabled,
                modifier = Modifier.fillMaxWidth(), label = { Text("Adresa počítače") },
                placeholder = { Text("http://192.168.1.10:5000") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onManualPick, enabled = enabled) { Text("Použít adresu") }
                TextButton(onClick = { onPing(address) }, enabled = enabled) { Text("Otestovat") }
            }
        } else if (selectedUrl.isNotEmpty()) {
            TextButton(onClick = { onPing(selectedUrl) }, enabled = enabled) { Text("Otestovat spojení") }
        }
    }
}

@Composable
internal fun TransferNavigation(devices: Boolean, enabled: Boolean, onSwitch: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
        for (devicePage in listOf(false, true)) {
            NavigationBarItem(selected = devices == devicePage, enabled = enabled, onClick = { onSwitch(devicePage) },
                icon = { Glyph(if (devicePage) R.drawable.fs_monitor else R.drawable.fs_send) },
                label = { Text(if (devicePage) "Zařízení" else "Odeslat") },
                colors = NavigationBarItemDefaults.colors(indicatorColor = colors.primaryContainer,
                    selectedIconColor = colors.onPrimaryContainer, selectedTextColor = colors.primary))
        }
    }
}
