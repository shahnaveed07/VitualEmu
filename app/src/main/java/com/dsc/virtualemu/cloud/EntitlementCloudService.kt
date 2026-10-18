package com.dsc.virtualemu.cloud

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EntitlementState(
    val isProUnlocked: Boolean = true,
    val tierName: String = "Local Unrestricted Edition",
    val licenseToken: String? = "LOCAL-ON-DEVICE",
    val verifiedAt: Long? = System.currentTimeMillis()
)

class EntitlementCloudService {
    private val _entitlement = MutableStateFlow(EntitlementState())
    val entitlement: StateFlow<EntitlementState> = _entitlement.asStateFlow()

    fun verifyAndUnlockPro(licenseKey: String = "LOCAL"): Boolean {
        val cleanKey = licenseKey.trim()
        _entitlement.value = EntitlementState(
            isProUnlocked = true,
            tierName = "Local Unrestricted Edition",
            licenseToken = if (cleanKey.isNotBlank()) "LOCAL-$cleanKey" else "LOCAL-ON-DEVICE",
            verifiedAt = System.currentTimeMillis()
        )
        return true
    }

    fun deleteCloudAccountAndEntitlements() {
        _entitlement.value = EntitlementState(
            isProUnlocked = true,
            tierName = "Local Unrestricted Edition (Local Data Cleared)",
            licenseToken = null,
            verifiedAt = System.currentTimeMillis()
        )
    }
}
