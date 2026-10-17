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

class DeviceManager(
    context: Context,
    private var onPeripheralDisconnected: ((String) -> Unit)? = null
) : InputManager.InputDeviceListener {
    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager

    private val _devicesFlow = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val devicesFlow: StateFlow<List<DeviceInfo>> = _devicesFlow.asStateFlow()

    private val _hotPlugLog = MutableStateFlow<List<String>>(emptyList())
    val hotPlugLog: StateFlow<List<String>> = _hotPlugLog.asStateFlow()

    init {
        inputManager?.registerInputDeviceListener(this, null)
        refreshDevices()
    }

    /**
     * Registers a callback invoked immediately when a physical keyboard/mouse is unplugged
     * so [com.vitualemu.app.runtime.input.MultiTouchDispatcher] and the active bridge
     * dispatch `ACTION_CANCEL` / `ACTION_UP` across all active pointer slots.
     */
    fun setOnPeripheralDisconnectedListener(listener: (String) -> Unit) {
        onPeripheralDisconnected = listener
    }

    fun refreshDevices() {
        val currentIds = inputManager?.inputDeviceIds ?: IntArray(0)
        val detected = mutableListOf<DeviceInfo>()

        for (id in currentIds) {
            val dev = inputManager?.getInputDevice(id) ?: continue
            if (dev.isVirtual) continue

            val sources = dev.sources
            val isMouse = (sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                (sources and InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE
            val isKeyboard = (sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD &&
                dev.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
            val isTouchpad = (sources and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD
            val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK

            val type = when {
                isMouse -> DeviceType.MOUSE
                isKeyboard -> DeviceType.KEYBOARD
                isTouchpad -> DeviceType.TOUCHPAD
                isGamepad -> DeviceType.GAMEPAD
                else -> DeviceType.UNKNOWN
            }

            if (type != DeviceType.UNKNOWN) {
                detected.add(
                    DeviceInfo(
                        id = dev.id,
                        name = dev.name ?: "Input Device #$id",
                        type = type,
                        isConnected = true,
                        isPhysical = !dev.isVirtual,
                        vendorId = dev.vendorId,
                        productId = dev.productId
                    )
                )
            }
        }
        _devicesFlow.value = detected
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        refreshDevices()
        val devName = inputManager?.getInputDevice(deviceId)?.name ?: "Device #$deviceId"
        appendLog("CONNECTED: $devName")
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        // Immediately neutralize all held inputs and active bridge pointer slots on peripheral unplug!
        HeldInputRegistry.releaseAll()
        refreshDevices()
        val reason = "DISCONNECTED: Device #$deviceId — Emergency Safe-Release triggered"
        appendLog(reason)
        onPeripheralDisconnected?.invoke(reason)
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        refreshDevices()
    }

    private fun appendLog(entry: String) {
        _hotPlugLog.value = (listOf(entry) + _hotPlugLog.value).take(20)
    }

    fun unregister() {
        inputManager?.unregisterInputDeviceListener(this)
    }
}
