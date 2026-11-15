package com.example.filesender

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.filesender.ui.theme.FileSenderTheme
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException

class MainActivity : ComponentActivity() {
    private lateinit var deviceDiscovery: DeviceDiscovery

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceDiscovery = DeviceDiscovery(applicationContext)
        enableEdgeToEdge()
        setContent {
            FileSenderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ShowApp(Modifier.padding(innerPadding), deviceDiscovery)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasLocalNetworkPermission(this)) deviceDiscovery.start()
    }

    override fun onStop() {
        deviceDiscovery.stop()
        super.onStop()
    }
}

private fun hasLocalNetworkPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(
        context, "android.permission.ACCESS_LOCAL_NETWORK"
    ) == PackageManager.PERMISSION_GRANTED

@Composable
fun ShowApp(modifier: Modifier = Modifier, discovery: DeviceDiscovery? = null) {
    val selector = remember { FileSelector() }
    TransferScreen(modifier, selector, discovery)
}

private data class ServerRequest(val baseUrl: String, val files: List<ISelectedItem>)

private enum class DiscoveryAction { Start, Refresh }

private fun parseServerAddress(value: String): String? {
    val address = value.trim().trimEnd('/')
    val uri = try {
        URI(address)
    } catch (e: URISyntaxException) {
        return null
    }
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()
        || uri.port !in -1..65535 || uri.port == 0 || !uri.path.isNullOrEmpty()
        || uri.query != null || uri.fragment != null || uri.userInfo != null) {
        return null
    }
    return address
}

@Composable
private fun TransferScreen(modifier: Modifier, selector: FileSelector, discovery: DeviceDiscovery?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var baseUrl by rememberSaveable { mutableStateOf("") }
    var recipientName by rememberSaveable { mutableStateOf("") }
    var manualAddress by rememberSaveable { mutableStateOf("http://10.0.2.2:5000") }
    var devicePage by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var batchProgress by remember { mutableStateOf<Float?>(null) }

    fun selectRecipient(name: String, url: String) {
        recipientName = name
        baseUrl = url
        manualAddress = url
        status = ""
        devicePage = false
    }
    var isBusy by remember { mutableStateOf(false) }
    var pendingRequest by remember { mutableStateOf<ServerRequest?>(null) }
    var pendingDiscovery by remember { mutableStateOf<DiscoveryAction?>(null) }
    var initialDiscoveryRequested by rememberSaveable { mutableStateOf(false) }
    var offlineDevices by remember { mutableStateOf(emptySet<DiscoveredDevice>()) }
    val devices = discovery?.devices.orEmpty().filterNot { it in offlineDevices }.distinctBy { it.baseUrl }

    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents == null) {
            val missingCameraPermission = result.originalIntent?.getBooleanExtra(
                Intents.Scan.MISSING_CAMERA_PERMISSION, false
            ) == true
            status = if (missingCameraPermission) "Pro skenování QR kódu je potřeba povolit kameru."
                else "Skenování bylo zrušeno."
        } else {
            val address = parseServerAddress(contents)
            if (address == null) {
                status = "QR kód neobsahuje platnou adresu desktopu ve tvaru http://IP_ADRESA:5000."
            } else {
                selectRecipient(URI(address).host, address)
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            qrScanner.launch(qrOptions())
        } else {
            status = "Přístup ke kameře nebyl povolen. Adresu desktopu můžeš zadat ručně."
        }
    }

    fun performRequest(request: ServerRequest) {
        status = if (request.files.isEmpty()) "Testuji spojení…" else "Odesílám soubory…"
        scope.launch {
            try {
                if (request.files.isEmpty()) {
                    status = "Spojení funguje: ${pingServer(request.baseUrl)}"
                } else {
                    var sentCount = 0
                    val errors = mutableListOf<String>()
                    batchProgress = 0f
                    request.files.forEachIndexed { index, file ->
                        status = "Odesílám ${index + 1}/${request.files.size}: ${file.fileName}"
                        try {
                            sendFile(request.baseUrl, file)
                            sentCount++
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: IOException) {
                            errors += "${file.fileName}: ${e.message}"
                        } catch (e: SecurityException) {
                            errors += "${file.fileName}: přístup k souboru nebo síti nebyl povolen"
                        } catch (e: IllegalArgumentException) {
                            errors += "${file.fileName}: ${e.message}"
                        }
                        batchProgress = (index + 1).toFloat() / request.files.size
                    }
                    status = "Desktop přijal $sentCount/${request.files.size} souborů." +
                        if (errors.isEmpty()) "" else "\nNepodařilo se odeslat:\n${errors.joinToString("\n")}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                status = "Požadavek selhal: ${e.message}"
            } catch (e: SecurityException) {
                status = "Přístup k síti nebo souboru nebyl povolen: ${e.message}"
            } catch (e: IllegalArgumentException) {
                status = "Požadavek nelze odeslat: ${e.message}"
            } finally {
                batchProgress = null
                isBusy = false
            }
        }
    }

    val permission = "android.permission.ACCESS_LOCAL_NETWORK"

    fun discover(action: DiscoveryAction) {
        if (discovery == null) return
        if (action == DiscoveryAction.Start) {
            discovery.start()
            return
        }

        val candidates = discovery.devices
        val addresses = candidates.mapTo(mutableSetOf()) { it.baseUrl }
        val selectedAddress = baseUrl
        if (selectedAddress.isNotEmpty()) addresses += selectedAddress
        discovery.stop()
        discovery.start()
        if (addresses.isEmpty()) return

        isBusy = true
        status = "Ověřuji dostupnost počítačů…"
        scope.launch {
            try {
                val offline = findOfflineReceivers(addresses)
                offlineDevices = (candidates + discovery.devices).filterTo(mutableSetOf()) { it.baseUrl in offline }
                if (selectedAddress in offline && baseUrl == selectedAddress) {
                    status = "Počítač $recipientName je nedostupný. Vyber příjemce znovu."
                    baseUrl = ""
                    recipientName = ""
                } else {
                    status = ""
                }
            } finally {
                isBusy = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingRequest
        val discoveryAction = pendingDiscovery
        pendingRequest = null
        pendingDiscovery = null
        if (granted && request != null) {
            performRequest(request)
        } else {
            isBusy = false
            if (granted && discoveryAction != null) {
                discover(discoveryAction)
            } else {
                status = if (granted) "Přístup povolen. Spusť požadavek znovu."
                    else "Přístup k místní síti nebyl povolen."
            }
        }
    }

    fun requestDiscovery(action: DiscoveryAction) {
        if (discovery == null || isBusy || selector.isLoading) return
        if (hasLocalNetworkPermission(context)) {
            discover(action)
        } else {
            pendingDiscovery = action
            isBusy = true
            permissionLauncher.launch(permission)
        }
    }

    LaunchedEffect(discovery) {
        if (discovery != null && !initialDiscoveryRequested) {
            initialDiscoveryRequested = true
            requestDiscovery(DiscoveryAction.Start)
        }
    }

    fun requestAction(sendSelectedFiles: Boolean, target: String = baseUrl) {
        if (isBusy || selector.isLoading) return

        val address = parseServerAddress(target)
        if (address == null) {
            status = "Zadej adresu ve tvaru http://IP_ADRESA:5000 bez další cesty."
            return
        }

        val files = if (sendSelectedFiles) selector.files.toList() else emptyList()
        if (sendSelectedFiles && files.isEmpty()) {
            status = "Nejdřív vyber soubory."
            return
        }

        val request = ServerRequest(address, files)
        isBusy = true
        val needsPermission = !hasLocalNetworkPermission(context)

        if (needsPermission) {
            pendingRequest = request
            status = "Čekám na povolení přístupu k místní síti…"
            permissionLauncher.launch(permission)
        } else {
            performRequest(request)
        }
    }

    val enabled = !isBusy && !selector.isLoading
    val sendScroll = rememberScrollState()
    val devicesScroll = rememberScrollState()
    BackHandler(enabled = devicePage && enabled) { devicePage = false }
    Column(modifier.fillMaxSize().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f).widthIn(max = 620.dp).fillMaxWidth()
            .verticalScroll(if (devicePage) devicesScroll else sendScroll)
            .padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FileSenderHeader()
            PageHeading(if (devicePage) "Zařízení" else "Odeslat soubory")
            if (devicePage) {
                DevicesPanel(discovery = discovery, devices = devices, enabled = enabled, selectedUrl = baseUrl,
                    address = manualAddress, onAddressChange = { manualAddress = it },
                    onRefresh = { requestDiscovery(DiscoveryAction.Refresh) },
                    onScan = {
                        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                            status = "Zařízení nemá kameru. Zadej adresu ručně."
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            != PackageManager.PERMISSION_GRANTED) {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        } else {
                            qrScanner.launch(qrOptions())
                        }
                    },
                    onPick = ::selectRecipient,
                    onManualPick = {
                        val address = parseServerAddress(manualAddress)
                        if (address == null) status = "Zadej adresu ve tvaru http://IP_ADRESA:5000."
                        else selectRecipient(URI(address).host, address)
                    },
                    onPing = { target -> requestAction(false, target) })
            } else {
                RecipientCard(recipientName, baseUrl, enabled) { devicePage = true }
                selector.AddSelector(Modifier.fillMaxWidth(), enabled = enabled)
            }
        }
        if (status.isNotBlank()) {
            Surface(modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(status, modifier = Modifier.heightIn(max = 96.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!devicePage) {
            batchProgress?.let { LinearProgressIndicator(progress = { it },
                modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) }
            Button(modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp).heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = enabled && selector.files.isNotEmpty(),
                onClick = { if (baseUrl.isBlank()) devicePage = true else requestAction(true) }) {
                Glyph(R.drawable.fs_send, Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
                Text(when {
                    batchProgress != null -> "Odesílám…"
                    baseUrl.isBlank() && selector.files.isNotEmpty() -> "Vybrat příjemce"
                    selector.files.isEmpty() -> "Odeslat soubory"
                    else -> "Odeslat (${selector.files.size})"
                })
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TransferNavigation(devicePage, enabled) { devicePage = it }
    }
}

private fun qrOptions() = ScanOptions().apply {
    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    setPrompt("Naskenuj QR kód z desktopové aplikace FileSender")
    setBeepEnabled(false)
    setOrientationLocked(false)
}

@Preview(showBackground = true)
@Composable
fun TransferPreview() {
    FileSenderTheme { ShowApp() }
}
