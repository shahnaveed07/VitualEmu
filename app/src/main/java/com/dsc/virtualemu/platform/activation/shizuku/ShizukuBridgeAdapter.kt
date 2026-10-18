package com.dsc.virtualemu.platform.activation.shizuku

import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.dsc.virtualemu.domain.model.BridgeHealthState
import com.dsc.virtualemu.domain.model.BridgeStatus
import com.dsc.virtualemu.domain.model.BridgeType
import com.dsc.virtualemu.domain.model.InputBridgeCapabilities
import com.dsc.virtualemu.domain.model.MacroStep
import com.dsc.virtualemu.domain.model.TouchAction
import com.dsc.virtualemu.domain.policy.SafetyPolicy
import com.dsc.virtualemu.domain.usecase.HeldInputRegistry
import com.dsc.virtualemu.runtime.bridge.InputBridgeAdapter
import com.dsc.virtualemu.runtime.input.CombinedTouchFrame
import com.dsc.virtualemu.runtime.input.MultiTouchPointerCombiner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.lang.reflect.Method

class ShizukuBridgeAdapter(
    private val fallbackCombiner: MultiTouchPointerCombiner = MultiTouchPointerCombiner()
) : InputBridgeAdapter {

    companion object {
        private const val INJECT_INPUT_EVENT_MODE_ASYNC = 0
        private const val DEFAULT_SHIZUKU_REQUEST_CODE = 1001
    }

    override val bridgeType: BridgeType = BridgeType.SHIZUKU

    private val capabilities = InputBridgeCapabilities(
        maxPointers = SafetyPolicy.MAX_SIMULTANEOUS_POINTERS,
        supportsMultiTouch = true,
        supportsRelativeMouseLook = true,
        supportsKeyInjection = true,
        minDispatchIntervalMs = 2L
    )

    private val _statusFlow = MutableStateFlow(
        BridgeStatus(
            type = BridgeType.SHIZUKU,
            state = BridgeHealthState.NOT_INITIALIZED,
            isAuthorized = false,
            lastLatencyMs = 0L,
            detailMessage = "Shizuku binder not yet checked",
            capabilities = capabilities
        )
    )
    override val statusFlow: StateFlow<BridgeStatus> = _statusFlow.asStateFlow()

    // Cached reflection handles for IInputManager.injectInputEvent via ShizukuBinderWrapper
    @Volatile
    private var cachedInputManagerProxy: Any? = null

    @Volatile
    private var cachedInjectInputEventMethod: Method? = null

    // Persistent privileged shell process for Linux evdev sendevent Protocol B fallback
    @Volatile
    private var persistentShellProcess: Process? = null

    @Volatile
    private var persistentShellWriter: BufferedWriter? = null

    private val shellLock = Any()

    @Volatile
    var dispatchedFrameCount: Long = 0L
        private set

    override fun getStatus(): BridgeStatus = _statusFlow.value

    override fun getCapabilities(): InputBridgeCapabilities = capabilities

    /**
     * Requests Shizuku runtime authorization if the Shizuku binder is alive but permission is not yet granted.
     */
    fun requestShizukuPermission(requestCode: Int = DEFAULT_SHIZUKU_REQUEST_CODE): Boolean {
        return try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val pingMethod = shizukuClass.getDeclaredMethod("pingBinder")
            val isAlive = pingMethod.invoke(null) as? Boolean ?: false
            if (!isAlive) return false

            val reqMethod = shizukuClass.getDeclaredMethod("requestPermission", Int::class.javaPrimitiveType)
            reqMethod.invoke(null, requestCode)
            true
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun checkHealth(): BridgeStatus {
        val startNs = System.nanoTime()
        val binderState = probeShizukuBinderAndPermission()
        val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)

        val next = when {
            binderState.isBinderAlive && binderState.isPermissionGranted -> {
                val isBinderBound = ensurePrivilegedInjectorBound()
                val transportLabel = if (isBinderBound) "IInputManager binder active" else "Shizuku shell evdev fallback"
                BridgeStatus(
                    type = BridgeType.SHIZUKU,
                    state = BridgeHealthState.CONNECTED,
                    isAuthorized = true,
                    lastLatencyMs = elapsedMs,
                    detailMessage = "Shizuku authorized ($transportLabel, ${elapsedMs}ms)",
                    capabilities = capabilities
                )
            }
            binderState.isBinderAlive && !binderState.isPermissionGranted -> {
                BridgeStatus(
                    type = BridgeType.SHIZUKU,
                    state = BridgeHealthState.PERMISSION_REVOKED,
                    isAuthorized = false,
                    lastLatencyMs = elapsedMs,
                    detailMessage = "Shizuku is running, but VitualEmu permission is not granted. Tap 'Grant Shizuku Permission'.",
                    capabilities = capabilities
                )
            }
            else -> {
                BridgeStatus(
                    type = BridgeType.SHIZUKU,
                    state = BridgeHealthState.DISCONNECTED,
                    isAuthorized = false,
                    lastLatencyMs = -1L,
                    detailMessage = "Shizuku service is not active. Start Shizuku via Wireless Debugging or ADB.",
                    capabilities = capabilities
                )
            }
        }
        _statusFlow.value = next
        return next
    }

    override suspend fun initialize(): Boolean {
        val health = checkHealth()
        return health.state == BridgeHealthState.CONNECTED && health.isAuthorized
    }

    override suspend fun startSession(sessionId: String): Boolean {
        val health = checkHealth()
        return health.state == BridgeHealthState.CONNECTED
    }

    override suspend fun injectCombinedTouchFrame(frame: CombinedTouchFrame): Boolean {
        if (_statusFlow.value.state != BridgeHealthState.CONNECTED) {
            return false
        }
        val startNs = System.nanoTime()
        val dispatched = dispatchMotionFramePrivileged(frame)
        val latencyMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
        dispatchedFrameCount++
        _statusFlow.value = _statusFlow.value.copy(lastLatencyMs = latencyMs)
        return dispatched
    }

    override suspend fun injectTouch(
        pointerId: Int,
        action: TouchAction,
        normX: Float,
        normY: Float,
        pressure: Float
    ): Boolean {
        if (!SafetyPolicy.isPointerSlotValid(pointerId)) return false
        if (_statusFlow.value.state != BridgeHealthState.CONNECTED) {
            return false
        }
        val frame = fallbackCombiner.onPointerTransition(
            pointerId = pointerId,
            requestedAction = action,
            normX = normX,
            normY = normY,
            pressure = pressure
        ) ?: return false
        return injectCombinedTouchFrame(frame)
    }

    override suspend fun injectTouch(action: String, x: Float, y: Float, pointerId: Int): Boolean {
        val mapped = when (action.uppercase()) {
            "DOWN", "ACTION_DOWN" -> TouchAction.DOWN
            "MOVE", "ACTION_MOVE" -> TouchAction.MOVE
            "UP", "ACTION_UP" -> TouchAction.UP
            "POINTER_DOWN", "ACTION_POINTER_DOWN" -> TouchAction.POINTER_DOWN
            "POINTER_UP", "ACTION_POINTER_UP" -> TouchAction.POINTER_UP
            else -> TouchAction.CANCEL
        }
        return injectTouch(pointerId, mapped, x, y, 1.0f)
    }

    override suspend fun injectKey(keyCode: Int, isDown: Boolean): Boolean {
        if (_statusFlow.value.state != BridgeHealthState.CONNECTED) return false
        if (isDown) {
            HeldInputRegistry.recordKeyDown(keyCode)
        } else {
            HeldInputRegistry.recordKeyUp(keyCode)
        }
        return dispatchKeyEventPrivileged(keyCode, isDown)
    }

    override suspend fun executeMacroStep(step: MacroStep, sessionId: String): Boolean {
        if (_statusFlow.value.state != BridgeHealthState.CONNECTED) return false
        val clean = SafetyPolicy.sanitizeMacroStep(step)
        if (clean.delayMs > 0) {
            delay(clean.delayMs)
        }
        val slot = SafetyPolicy.FIRST_DYNAMIC_POINTER_SLOT
        return when (clean.actionType.uppercase()) {
            "TAP" -> {
                injectTouch(slot, TouchAction.DOWN, clean.normalizedX, clean.normalizedY, 1.0f)
                delay(clean.holdDurationMs)
                injectTouch(slot, TouchAction.UP, clean.normalizedX, clean.normalizedY, 0.0f)
            }
            "SWIPE" -> {
                val slices = 5
                val sliceDelay = (clean.holdDurationMs / slices).coerceAtLeast(8L)
                injectTouch(slot, TouchAction.DOWN, clean.normalizedX, clean.normalizedY, 1.0f)
                for (i in 1..slices) {
                    val t = i.toFloat() / slices.toFloat()
                    val ix = clean.normalizedX + (clean.endNormalizedX - clean.normalizedX) * t
                    val iy = clean.normalizedY + (clean.endNormalizedY - clean.normalizedY) * t
                    delay(sliceDelay)
                    injectTouch(slot, TouchAction.MOVE, ix, iy, 1.0f)
                }
                injectTouch(slot, TouchAction.UP, clean.endNormalizedX, clean.endNormalizedY, 0.0f)
            }
            "RELEASE" -> {
                injectTouch(slot, TouchAction.UP, clean.normalizedX, clean.normalizedY, 0.0f)
            }
            else -> {
                injectTouch(slot, TouchAction.DOWN, clean.normalizedX, clean.normalizedY, 1.0f)
                delay(clean.holdDurationMs)
                injectTouch(slot, TouchAction.UP, clean.normalizedX, clean.normalizedY, 0.0f)
            }
        }
    }

    override suspend fun cancelAll(sessionId: String) {
        releaseHeldInputs(sessionId)
    }

    override suspend fun releaseHeldInputs(sessionId: String) {
        HeldInputRegistry.releaseAll()
        val cancelFrame = fallbackCombiner.cancelAll()
        if (cancelFrame != null && _statusFlow.value.state == BridgeHealthState.CONNECTED) {
            dispatchMotionFramePrivileged(cancelFrame)
        }
    }

    override suspend fun stopSession(sessionId: String) {
        releaseHeldInputs(sessionId)
    }

    override fun shutdown() {
        HeldInputRegistry.releaseAll()
        fallbackCombiner.cancelAll()
        closePersistentShell()
        cachedInputManagerProxy = null
        cachedInjectInputEventMethod = null
        _statusFlow.value = _statusFlow.value.copy(
            state = BridgeHealthState.DISCONNECTED,
            isAuthorized = false,
            detailMessage = "Shizuku bridge stopped and all held inputs released"
        )
    }

    private data class BinderProbeResult(
        val isBinderAlive: Boolean,
        val isPermissionGranted: Boolean,
        val transportLabel: String
    )

    private fun probeShizukuBinderAndPermission(): BinderProbeResult {
        return try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val pingMethod = shizukuClass.getDeclaredMethod("pingBinder")
            val isAlive = pingMethod.invoke(null) as? Boolean ?: false
            if (!isAlive) {
                return BinderProbeResult(false, false, "none")
            }
            val permGranted = try {
                val checkPermMethod = shizukuClass.getDeclaredMethod("checkSelfPermission")
                val res = checkPermMethod.invoke(null) as? Int ?: PackageManager.PERMISSION_DENIED
                res == PackageManager.PERMISSION_GRANTED
            } catch (_: NoSuchMethodException) {
                true
            }
            BinderProbeResult(
                isBinderAlive = true,
                isPermissionGranted = permGranted,
                transportLabel = "IInputManager+sendevent"
            )
        } catch (_: Throwable) {
            BinderProbeResult(false, false, "unavailable")
        }
    }

    /**
     * Binds `IInputManager.Stub.asInterface(ShizukuBinderWrapper(ServiceManager.getService("input")))`
     * so multi-pointer [android.view.MotionEvent] frames can be injected directly at shell privilege level.
     */
    private fun ensurePrivilegedInjectorBound(): Boolean {
        if (cachedInputManagerProxy != null && cachedInjectInputEventMethod != null) {
            return true
        }
        return try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String::class.java)
            val rawInputBinder = getServiceMethod.invoke(null, "input") as? IBinder ?: return false

            val wrapperClass = Class.forName("rikka.shizuku.ShizukuBinderWrapper")
            val wrapperCtor = wrapperClass.getDeclaredConstructor(IBinder::class.java)
            val wrappedBinder = wrapperCtor.newInstance(rawInputBinder) as IBinder

            val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asInterfaceMethod = stubClass.getDeclaredMethod("asInterface", IBinder::class.java)
            val proxy = asInterfaceMethod.invoke(null, wrappedBinder) ?: return false

            val injectMethod = proxy.javaClass.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            cachedInputManagerProxy = proxy
            cachedInjectInputEventMethod = injectMethod
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun dispatchMotionFramePrivileged(frame: CombinedTouchFrame): Boolean {
        // Path 1: Direct IInputManager binder injection with combined multi-pointer MotionEvent
        val motionEvent = frame.obtainMotionEvent()
        if (motionEvent != null) {
            try {
                if (ensurePrivilegedInjectorBound()) {
                    val proxy = cachedInputManagerProxy
                    val method = cachedInjectInputEventMethod
                    if (proxy != null && method != null) {
                        val ok = method.invoke(proxy, motionEvent, INJECT_INPUT_EVENT_MODE_ASYNC) as? Boolean ?: false
                        if (ok) return true
                    }
                }
            } catch (_: Throwable) {
                // Fall through to persistent Shizuku shell sendevent Protocol B fallback
            } finally {
                try {
                    motionEvent.recycle()
                } catch (_: Throwable) {
                }
            }
        }

        // Path 2: Persistent Shizuku shell stream executing Linux evdev Protocol B slot commands
        return dispatchEvdevViaShizukuShell(frame.evdevTypeBCommands)
    }

    private fun dispatchKeyEventPrivileged(keyCode: Int, isDown: Boolean): Boolean {
        val now = try {
            SystemClock.uptimeMillis().takeIf { it > 0L } ?: System.currentTimeMillis()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
        try {
            if (ensurePrivilegedInjectorBound()) {
                val action = if (isDown) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
                val event = KeyEvent(
                    now,
                    now,
                    action,
                    keyCode,
                    0,
                    0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD,
                    0,
                    0,
                    InputDevice.SOURCE_KEYBOARD
                )
                val proxy = cachedInputManagerProxy
                val method = cachedInjectInputEventMethod
                if (proxy != null && method != null) {
                    val ok = method.invoke(proxy, event, INJECT_INPUT_EVENT_MODE_ASYNC) as? Boolean ?: false
                    if (ok) return true
                }
            }
        } catch (_: Throwable) {
        }
        if (isDown) {
            return dispatchEvdevViaShizukuShell(listOf("input keyevent $keyCode"))
        }
        return true
    }

    private fun dispatchEvdevViaShizukuShell(commands: List<String>): Boolean {
        if (commands.isEmpty()) return true
        synchronized(shellLock) {
            try {
                var writer = persistentShellWriter
                if (writer == null) {
                    val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
                    val newProcessMethod = shizukuClass.getDeclaredMethod(
                        "newProcess",
                        Array<String>::class.java,
                        Array<String>::class.java,
                        String::class.java
                    )
                    newProcessMethod.isAccessible = true
                    val proc = newProcessMethod.invoke(null, arrayOf("sh"), null, null) as? Process
                    if (proc != null) {
                        persistentShellProcess = proc
                        writer = BufferedWriter(OutputStreamWriter(proc.outputStream))
                        persistentShellWriter = writer
                    }
                }
                if (writer != null) {
                    for (cmd in commands) {
                        writer.write(cmd)
                        writer.newLine()
                    }
                    writer.flush()
                    return true
                }
            } catch (_: Throwable) {
                closePersistentShell()
                return false
            }
            return false
        }
    }

    private fun closePersistentShell() {
        synchronized(shellLock) {
            try {
                persistentShellWriter?.close()
            } catch (_: Throwable) {
            }
            try {
                persistentShellProcess?.destroy()
            } catch (_: Throwable) {
            }
            persistentShellWriter = null
            persistentShellProcess = null
        }
    }
}
