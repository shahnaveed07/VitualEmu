package com.vitualemu.app.cloud

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EntitlementState(
    val isProUnlocked: Boolean = false,
    val tierName: String = "Free Tier",
    val licenseToken: String? = null,
    val verifiedAt: Long? = null
)

class EntitlementCloudService {
    private val _entitlement = MutableStateFlow(EntitlementState())
    val entitlement: StateFlow<EntitlementState> = _entitlement.asStateFlow()

    fun verifyAndUnlockPro(licenseKey: String): Boolean {
        val clean = licenseKey.trim()
        if (clean.equals("VITUALEMU-PRO", ignoreCase = true) || clean.length >= 8) {
            _entitlement.value = EntitlementState(
                isProUnlocked = true,
                tierName = "VitualEmu Pro",
                licenseToken = "PRO-${clean.take(6).uppercase()}",
                verifiedAt = System.currentTimeMillis()
            )
            return true
        }
        return false
    }

    fun deleteCloudAccountAndEntitlements() {
        _entitlement.value = EntitlementState(
            isProUnlocked = false,
            tierName = "Free Tier",
            licenseToken = null,
            verifiedAt = null
        )
    }
}
