package com.dsc.virtualemu.cloud

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EntitlementState(
    val isProUnlocked: Boolean = true,
    val tierName: String = "Full Local Edition",
    val licenseToken: String? = "LOCAL-UNRESTRICTED",
    val verifiedAt: Long? = System.currentTimeMillis()
)

class EntitlementCloudService {
    private val _entitlement = MutableStateFlow(EntitlementState())
    val entitlement: StateFlow<EntitlementState> = _entitlement.asStateFlow()

    fun verifyAndUnlockPro(licenseKey: String = "LOCAL"): Boolean {
        _entitlement.value = EntitlementState(
            isProUnlocked = true,
            tierName = "Full Local Edition",
            licenseToken = "LOCAL-UNRESTRICTED",
            verifiedAt = System.currentTimeMillis()
        )
        return true
    }

    fun deleteCloudAccountAndEntitlements() {
        _entitlement.value = EntitlementState(
            isProUnlocked = true,
            tierName = "Full Local Edition (Cloud Cleared)",
            licenseToken = null,
            verifiedAt = System.currentTimeMillis()
        )
    }
}
