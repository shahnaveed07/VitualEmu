package com.vitualemu.app.platform.devices

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import com.vitualemu.app.domain.model.DeviceInfo
import com.vitualemu.app.domain.model.DeviceType
import com.vitualemu.app.domain.usecase.HeldInputRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceManager(context: Context) : InputManager.InputDeviceListener {
    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager

    private val _devicesFlow = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val devicesFlow: StateFlow<List<DeviceInfo>> = _devicesFlow.asStateFlow()

    private val _hotPlugLog = MutableStateFlow<List<String>>(emptyList())
    val hotPlugLog: StateFlow<List<String>> = _hotPlugLog.asStateFlow()

    init {
        inputManager?.registerInputDeviceListener(this, null)
        refreshDevices()
    }

    fun refreshDevices() {
        val detected = mutableListOf<DeviceInfo>()
        val deviceIds = InputDevice.getDeviceIds()

        for (id in deviceIds) {
            val device = InputDevice.getDevice(id) ?: continue

            // Determine if device is a physical keyboard or mouse
            val sources = device.sources
            val isKeyboard = (sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD &&
                    device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
            val isMouse = (sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
            val isTouchpad = (sources and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD
            val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD

            val type = when {
                isKeyboard -> DeviceType.KEYBOARD
                isMouse -> DeviceType.MOUSE
                isTouchpad -> DeviceType.TOUCHPAD
                isGamepad -> DeviceType.GAMEPAD
                else -> DeviceType.UNKNOWN
            }

            // Exclude virtual on-screen keyboards (vendorId == 0 and not physical)
            val isPhysical = !device.isVirtual

            if (type != DeviceType.UNKNOWN) {
                detected.add(
                    DeviceInfo(
                        id = device.id,
                        name = device.name,
                        type = type,
                        isConnected = true,
                        isPhysical = isPhysical,
                        vendorId = device.vendorId,
                        productId = device.productId
                    )
                )
            }
        }

        _devicesFlow.value = detected
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        val device = InputDevice.getDevice(deviceId)
        val name = device?.name ?: "Device #$deviceId"
        appendLog("Device connected: $name")
        refreshDevices()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        appendLog("Device removed: ID #$deviceId. Triggering safe input release.")
        // Safety requirement: Peripheral unplug must neutralize all held inputs immediately
        HeldInputRegistry.releaseAll()
        refreshDevices()
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        refreshDevices()
    }

    private fun appendLog(entry: String) {
        val current = _hotPlugLog.value.toMutableList()
        current.add(0, "[${System.currentTimeMillis()}] $entry")
        if (current.size > 20) {
            current.removeAt(current.size - 1)
        }
        _hotPlugLog.value = current
    }

    fun unregister() {
        inputManager?.unregisterInputDeviceListener(this)
    }
}
