package com.example.filesender

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.Inet4Address
import java.util.ArrayDeque

data class DiscoveredDevice(val serviceName: String, val name: String, val baseUrl: String)

// Starší API používáme také na nových telefonech kvůli jednotnému chování od Androidu 8.
@Suppress("DEPRECATION")
class DeviceDiscovery(context: Context) {
    companion object {
        private const val SERVICE_TYPE = "_filesender._tcp."
    }

    private val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private var listener: NsdManager.DiscoveryListener? = null
    private var generation = 0
    private val knownServices = mutableMapOf<String, NsdServiceInfo>()
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var activeResolve: NsdManager.ResolveListener? = null

    var devices by mutableStateOf<List<DiscoveredDevice>>(emptyList())
        private set
    var isSearching by mutableStateOf(false)
        private set
    var status by mutableStateOf("Hledání není spuštěno. Povol přístup k místní síti nebo použij QR.")
        private set

    fun start() {
        if (listener != null) return
        val currentGeneration = ++generation
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = update {
                status = "Hledám počítače s FileSenderem…"
            }

            override fun onServiceFound(info: NsdServiceInfo) = update {
                if (info.serviceType.trimEnd('.') == SERVICE_TYPE.trimEnd('.') &&
                    !knownServices.containsKey(info.serviceName)) {
                    knownServices[info.serviceName] = info
                    pending.addLast(info)
                    resolveNext()
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) = update {
                knownServices.remove(info.serviceName)
                pending.removeAll { it.serviceName == info.serviceName }
                devices = devices.filterNot { it.serviceName == info.serviceName }
            }

            override fun onDiscoveryStopped(type: String) = update {
                listener = null
                isSearching = false
                pending.clear()
                knownServices.clear()
                devices = emptyList()
                status = "Hledání bylo zastaveno."
            }

            override fun onStartDiscoveryFailed(type: String, errorCode: Int) = update {
                stop()
                status = "Hledání se nepodařilo spustit (chyba $errorCode). Zkus obnovit hledání nebo použij QR."
            }

            override fun onStopDiscoveryFailed(type: String, errorCode: Int) = update {
                status = "Hledání se nepodařilo zastavit (chyba $errorCode)."
            }

            private fun update(action: () -> Unit) {
                main.post {
                    if (listener === this && generation == currentGeneration) action()
                }
            }
        }
        listener = discoveryListener
        isSearching = true
        status = "Spouštím hledání v místní síti…"
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: RuntimeException) {
            stop()
            status = "Hledání není dostupné: ${e.message}. Použij QR nebo zadej adresu ručně."
        }
    }

    // Na starších Androidech lze vyhodnocovat jen jednu službu najednou.
    private fun resolveNext() {
        if (activeResolve != null || listener == null || pending.isEmpty()) return
        val info = pending.removeFirst()
        val currentGeneration = generation
        val resolver = object : NsdManager.ResolveListener {
            override fun onServiceResolved(resolved: NsdServiceInfo) {
                complete {
                    val host = resolved.host as? Inet4Address
                    val address = host?.hostAddress
                    if (address != null && resolved.port in 1..65535) {
                        val name = resolved.attributes["name"]?.toString(Charsets.UTF_8)
                            ?.takeIf { it.isNotBlank() } ?: resolved.serviceName
                        val device = DiscoveredDevice(info.serviceName, name, "http://$address:${resolved.port}")
                        devices = (devices.filterNot { it.serviceName == device.serviceName } + device)
                            .sortedWith(compareBy({ it.name }, { it.baseUrl }))
                    }
                }
            }

            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                complete {
                    status = "Adresu jednoho zařízení se nepodařilo načíst (chyba $errorCode). Zkus obnovit hledání."
                }
            }

            private fun complete(action: () -> Unit) {
                main.post {
                    if (activeResolve !== this) return@post
                    activeResolve = null
                    if (generation == currentGeneration && listener != null &&
                        knownServices[info.serviceName] === info) action()
                    resolveNext()
                }
            }
        }
        activeResolve = resolver
        try {
            manager.resolveService(info, resolver)
        } catch (e: RuntimeException) {
            resolver.onResolveFailed(info, NsdManager.FAILURE_INTERNAL_ERROR)
        }
    }

    fun stop() {
        val oldListener = listener
        listener = null
        generation++
        isSearching = false
        pending.clear()
        knownServices.clear()
        devices = emptyList()
        status = "Hledání je pozastaveno."
        // Probíhající resolve doběhne; jeho zastaralý výsledek se podle generace zahodí.
        if (oldListener != null) {
            try {
                manager.stopServiceDiscovery(oldListener)
            } catch (_: RuntimeException) {
                // Listener už mohl být odregistrován systémem.
            }
        }
    }
}
