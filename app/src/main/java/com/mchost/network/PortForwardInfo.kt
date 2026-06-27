package com.mchost.network

import com.mchost.data.ForwardingMethod

enum class ForwardingStatus {
    IDLE,
    APPLYING,
    ACTIVE,
    FAILED,
    MANUAL,
}

data class PortForwardInfo(
    val status: ForwardingStatus = ForwardingStatus.IDLE,
    val method: ForwardingMethod = ForwardingMethod.UPNP,
    val localAddress: String = "",
    val publicAddress: String = "",
    val message: String = "",
) {
    val shareAddress: String
        get() = when {
            publicAddress.isNotBlank() -> publicAddress
            localAddress.isNotBlank() -> localAddress
            else -> ""
        }
}
