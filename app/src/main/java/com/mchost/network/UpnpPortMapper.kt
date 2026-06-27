package com.mchost.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.mchost.runtime.LogBus
import org.bitlet.weupnp.GatewayDiscover
import java.net.Inet4Address
import java.net.NetworkInterface

class UpnpPortMapper(private val context: Context) {

    data class MappingResult(
        val success: Boolean,
        val externalIp: String? = null,
        val localIp: String? = null,
        val message: String = "",
    )

    fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            for (name in listOf("wlan0", "eth0", "ap0", "wifi-aware0")) {
                val ip = interfaces.find { it.name == name }?.inetAddresses?.toList()
                    ?.filterIsInstance<Inet4Address>()
                    ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    ?.hostAddress
                if (!ip.isNullOrBlank()) return ip
            }
            interfaces.flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?.hostAddress
        } catch (e: Exception) {
            Log.w("UpnpPortMapper", "Local IP lookup failed", e)
            null
        }
    }

    fun mapPort(internalPort: Int, description: String = "MCHost Minecraft"): MappingResult {
        return withMulticastLock {
            try {
                val discover = GatewayDiscover()
                discover.setTimeout(8_000)
                val gateways = discover.discover()
                if (gateways.isEmpty()) {
                    return@withMulticastLock MappingResult(
                        success = false,
                        message = "No UPnP router found — try Manual or mobile hotspot",
                    )
                }
                val device = discover.validGateway
                    ?: return@withMulticastLock MappingResult(false, message = "Router found but UPnP gateway invalid")
                val localIp = getLocalIpAddress()
                    ?: return@withMulticastLock MappingResult(false, message = "No Wi‑Fi IP — connect to Wi‑Fi first")

                // Refresh mapping if one already exists
                device.deletePortMapping(internalPort, "TCP")
                val mapped = device.addPortMapping(
                    internalPort,
                    internalPort,
                    localIp,
                    "TCP",
                    description,
                )
                val externalIp = device.externalIPAddress?.takeIf { it.isNotBlank() }
                if (mapped) {
                    LogBus.emit(com.mchost.data.LogLevel.INFO, "UPnP forwarded TCP $internalPort → $localIp")
                    if (!externalIp.isNullOrBlank()) {
                        LogBus.emit(com.mchost.data.LogLevel.INFO, "Friends can join: $externalIp:$internalPort")
                    }
                    MappingResult(
                        success = true,
                        externalIp = externalIp,
                        localIp = localIp,
                        message = if (externalIp.isNullOrBlank()) {
                            "Port mapped on router (public IP unknown)"
                        } else {
                            "UPnP active — share $externalIp:$internalPort"
                        },
                    )
                } else {
                    MappingResult(
                        success = false,
                        localIp = localIp,
                        message = "Router rejected port $internalPort — try Manual forwarding",
                    )
                }
            } catch (e: Exception) {
                MappingResult(false, message = e.message ?: "UPnP error")
            }
        }
    }

    fun unmapPort(port: Int) {
        withMulticastLock {
            try {
                val discover = GatewayDiscover()
                discover.setTimeout(3_000)
                if (discover.discover().isEmpty()) return@withMulticastLock
                discover.validGateway?.deletePortMapping(port, "TCP")
            } catch (e: Exception) {
                Log.w("UpnpPortMapper", "unmap failed", e)
            }
        }
    }

    private fun <T> withMulticastLock(block: () -> T): T {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("mchost-upnp")?.apply {
            setReferenceCounted(true)
            acquire()
        }
        return try {
            block()
        } finally {
            try {
                lock?.release()
            } catch (_: Exception) {
            }
        }
    }
}
