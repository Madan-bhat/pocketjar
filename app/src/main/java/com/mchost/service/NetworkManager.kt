package com.mchost.service

import android.content.Context
import com.mchost.data.ForwardingMethod
import com.mchost.data.NetworkPrefs
import com.mchost.network.LocalXposeAgent
import com.mchost.network.ForwardingStatus
import com.mchost.network.PortForwardInfo
import com.mchost.network.PublicIpLookup
import com.mchost.network.UpnpPortMapper
import com.mchost.runtime.LogBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class NetworkManager(context: Context) {
    private val appContext = context.applicationContext
    private val upnp = UpnpPortMapper(appContext)
    private val localXpose = LocalXposeAgent(appContext)

    private val _forwardInfo = MutableStateFlow(PortForwardInfo())
    val forwardInfo: StateFlow<PortForwardInfo> = _forwardInfo.asStateFlow()

    private val _connectionAddress = MutableStateFlow("")
    val connectionAddress: StateFlow<String> = _connectionAddress.asStateFlow()

    private val _portOpen = MutableStateFlow(false)
    val portOpen: StateFlow<Boolean> = _portOpen.asStateFlow()

    fun localIp(): String = upnp.getLocalIpAddress() ?: "unknown"

    suspend fun applyForwarding(method: ForwardingMethod, port: Int, networkPrefs: NetworkPrefs? = null): String =
        withContext(Dispatchers.IO) {
        val localIp = upnp.getLocalIpAddress() ?: "unknown"
        val localAddress = "$localIp:$port"

        _forwardInfo.value = PortForwardInfo(
            status = ForwardingStatus.APPLYING,
            method = method,
            localAddress = localAddress,
            message = "Applying ${method.name}…",
        )

        val result = when (method) {
            ForwardingMethod.UPNP -> applyUpnp(port, localAddress)
            ForwardingMethod.LOCALXPOSE -> applyLocalXpose(port, localAddress, networkPrefs)
            ForwardingMethod.TAILSCALE -> applyTailscale(port, localAddress)
            ForwardingMethod.MANUAL -> applyManual(port, localAddress)
        }

        _forwardInfo.value = result
        _connectionAddress.value = result.shareAddress
        _portOpen.value = result.status == ForwardingStatus.ACTIVE

        if (result.message.isNotBlank()) {
            val level = when (result.status) {
                ForwardingStatus.ACTIVE -> com.mchost.data.LogLevel.INFO
                ForwardingStatus.FAILED -> com.mchost.data.LogLevel.WARN
                else -> com.mchost.data.LogLevel.INFO
            }
            LogBus.emit(level, result.message)
        }
        result.message
    }

    suspend fun clearForwarding(port: Int) = withContext(Dispatchers.IO) {
        upnp.unmapPort(port)
        localXpose.stop()
        _portOpen.value = false
        _connectionAddress.value = ""
        _forwardInfo.value = PortForwardInfo(status = ForwardingStatus.IDLE)
    }

    private fun applyUpnp(port: Int, localAddress: String): PortForwardInfo {
        val result = upnp.mapPort(port)
        return if (result.success) {
            val public = result.externalIp?.let { "$it:$port" } ?: ""
            PortForwardInfo(
                status = ForwardingStatus.ACTIVE,
                method = ForwardingMethod.UPNP,
                localAddress = localAddress,
                publicAddress = public,
                message = result.message,
            )
        } else {
            PortForwardInfo(
                status = ForwardingStatus.FAILED,
                method = ForwardingMethod.UPNP,
                localAddress = localAddress,
                message = result.message,
            )
        }
    }

    private suspend fun applyLocalXpose(
        port: Int,
        localAddress: String,
        networkPrefs: NetworkPrefs?,
    ): PortForwardInfo {
        val manual = networkPrefs?.localXposeManualAddress?.trim().orEmpty()
        if (manual.isNotBlank() && manual.contains(':')) {
            return PortForwardInfo(
                status = ForwardingStatus.ACTIVE,
                method = ForwardingMethod.LOCALXPOSE,
                localAddress = localAddress,
                publicAddress = manual,
                message = "Tunnel active — share $manual (keep Termux loclx running)",
            )
        }

        val token = networkPrefs?.localXposeToken
        val region = networkPrefs?.localXposeRegion ?: "ap"
        return localXpose.start(port, token ?: "", region).fold(
            onSuccess = { addr ->
                PortForwardInfo(
                    status = ForwardingStatus.ACTIVE,
                    method = ForwardingMethod.LOCALXPOSE,
                    localAddress = localAddress,
                    publicAddress = addr,
                    message = "LocalXpose tunnel active — share $addr",
                )
            },
            onFailure = { e ->
                PortForwardInfo(
                    status = ForwardingStatus.FAILED,
                    method = ForwardingMethod.LOCALXPOSE,
                    localAddress = localAddress,
                    message = e.message ?: "LocalXpose failed",
                )
            },
        )
    }

    private fun applyTailscale(port: Int, localAddress: String): PortForwardInfo {
        return PortForwardInfo(
            status = ForwardingStatus.MANUAL,
            method = ForwardingMethod.TAILSCALE,
            localAddress = localAddress,
            publicAddress = "",
            message = "Share your Tailscale IP with :$port — open the Tailscale app",
        )
    }

    private fun applyManual(port: Int, localAddress: String): PortForwardInfo {
        val publicIp = PublicIpLookup.fetch()
        val public = publicIp?.let { "$it:$port" } ?: ""
        val deviceIp = upnp.getLocalIpAddress() ?: "your-phone-ip"
        return PortForwardInfo(
            status = ForwardingStatus.MANUAL,
            method = ForwardingMethod.MANUAL,
            localAddress = localAddress,
            publicAddress = public,
            message = if (publicIp != null) {
                "Forward TCP $port on your router → $deviceIp, friends join $public"
            } else {
                "Forward TCP $port on your router → $deviceIp"
            },
        )
    }
}
