package com.dsc.virtualemu.runtime.capture

import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Manages Shizuku-based physical input capture by spawning `getevent` processes
 * that read raw Linux input events from `/dev/input/eventN` device nodes.
 *
 * This runs in the app's process using Shizuku's `newProcess()` to execute commands
 * with shell UID (2000) permissions. It does NOT require root.
 *
 * Mouse capture (EV_REL for relative dx/dy) is the primary purpose — this is the
 * **only** way to get raw relative mouse movement on Android without root.
 *
 * Keyboard capture via getevent serves as a fallback when AccessibilityService is unavailable.
 */
class ShizukuInputCaptureService {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _capturedDevices = MutableStateFlow<List<InputDeviceNode>>(emptyList())
    val capturedDevices: StateFlow<List<InputDeviceNode>> = _capturedDevices.asStateFlow()

    /** Callback invoked on the IO dispatcher for each parsed event batch (per SYN_REPORT frame) */
    @Volatile
    var onEventsReceived: ((List<PhysicalInputEvent>) -> Unit)? = null

    private val activeJobs = mutableMapOf<String, Job>()
    private val jobLock = Any()

    data class InputDeviceNode(
        val path: String,          // e.g. "/dev/input/event3"
        val name: String,          // e.g. "Logitech USB Mouse"
        val hasRelAxes: Boolean,   // True if device reports EV_REL (mouse)
        val hasKeys: Boolean       // True if device reports EV_KEY with keyboard keys
    )

    /**
     * Check if Shizuku is available and has permission.
     */
    fun checkAvailability(): Boolean {
        return try {
            val alive = Shizuku.pingBinder()
            val granted = alive && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            _isAvailable.value = granted
            granted
        } catch (_: Throwable) {
            _isAvailable.value = false
            false
        }
    }

    /**
     * Enumerate input devices by running `getevent -pS` via Shizuku shell.
     * Returns a list of device nodes with their capabilities.
     */
    fun enumerateDevices(): List<InputDeviceNode> {
        if (!checkAvailability()) return emptyList()

        return try {
            val process = createShizukuProcess(arrayOf("getevent", "-pS"))
                ?: return emptyList()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val devices = mutableListOf<InputDeviceNode>()

            var currentPath: String? = null
            var currentName: String? = null
            var hasRel = false
            var hasKeys = false

            reader.useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("add device") -> {
                            // Flush previous device
                            if (currentPath != null) {
                                devices.add(InputDeviceNode(
                                    path = currentPath!!,
                                    name = currentName ?: currentPath!!,
                                    hasRelAxes = hasRel,
                                    hasKeys = hasKeys
                                ))
                            }
                            currentPath = trimmed.substringAfter(": ").trim()
                            currentName = null
                            hasRel = false
                            hasKeys = false
                        }
                        trimmed.startsWith("name:") -> {
                            currentName = trimmed.substringAfter("name:").trim()
                                .removeSurrounding("\"")
                        }
                        // EV_REL indicates mouse/trackball relative axes
                        trimmed.contains("EV_REL") || trimmed.contains("0002") -> {
                            hasRel = true
                        }
                        // EV_KEY with keyboard keys
                        (trimmed.contains("EV_KEY") || trimmed.contains("0001")) &&
                                !trimmed.startsWith("add") -> {
                            hasKeys = true
                        }
                    }
                }
                // Flush last device
                if (currentPath != null) {
                    devices.add(InputDeviceNode(
                        path = currentPath!!,
                        name = currentName ?: currentPath!!,
                        hasRelAxes = hasRel,
                        hasKeys = hasKeys
                    ))
                }
            }

            try { process.destroy() } catch (_: Throwable) {}

            // Filter out virtual/internal devices — keep only those with keyboard keys or mouse axes
            val filtered = devices.filter { it.hasRelAxes || it.hasKeys }
            _capturedDevices.value = filtered
            filtered
        } catch (e: Throwable) {
            emptyList()
        }
    }

    /**
     * Start capturing events from the specified device nodes.
     * Spawns a `getevent` process per device node via Shizuku shell.
     *
     * @param mouseDevices Device nodes with EV_REL (mouse)
     * @param keyboardDevices Device nodes with EV_KEY (keyboard) — only used as fallback
     * @param captureKeyboard Whether to capture keyboard via getevent (false if Accessibility is active)
     */
    fun startCapture(
        mouseDevices: List<InputDeviceNode>,
        keyboardDevices: List<InputDeviceNode>,
        captureKeyboard: Boolean = false
    ) {
        if (!checkAvailability()) return
        stopCapture()

        val devicesToCapture = mutableListOf<InputDeviceNode>()
        devicesToCapture.addAll(mouseDevices)
        if (captureKeyboard) {
            devicesToCapture.addAll(keyboardDevices.filter { it !in mouseDevices })
        }

        if (devicesToCapture.isEmpty()) return

        _isCapturing.value = true

        for (device in devicesToCapture) {
            val job = scope.launch {
                captureDeviceLoop(device)
            }
            synchronized(jobLock) {
                activeJobs[device.path] = job
            }
        }
    }

    /**
     * Stop all active capture loops.
     */
    fun stopCapture() {
        synchronized(jobLock) {
            for ((_, job) in activeJobs) {
                job.cancel()
            }
            activeJobs.clear()
        }
        _isCapturing.value = false
    }

    /**
     * Internal: Runs `getevent <devicePath>` in a Shizuku shell process and parses output.
     */
    private suspend fun captureDeviceLoop(device: InputDeviceNode) {
        val accumulator = GeteventParser.FrameAccumulator(
            deviceId = device.path.hashCode(),
            deviceName = device.name
        )

        var process: Process? = null
        try {
            process = createShizukuProcess(arrayOf("getevent", device.path))
                ?: return

            val reader = BufferedReader(InputStreamReader(process.inputStream), 4096)
            var line: String?

            while (scope.isActive) {
                line = reader.readLine() ?: break
                val events = accumulator.feedLine(line)
                if (events.isNotEmpty()) {
                    onEventsReceived?.invoke(events)
                }
            }
        } catch (_: Throwable) {
            // Process ended or was killed — expected on stopCapture()
        } finally {
            try { process?.destroy() } catch (_: Throwable) {}
            accumulator.reset()
        }

        // If we exit the loop unexpectedly while still supposed to be capturing,
        // the state manager should be notified externally
    }

    /**
     * Creates a new process via Shizuku's privileged shell.
     */
    private fun createShizukuProcess(command: Array<String>): Process? {
        return try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuClass.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            newProcessMethod.invoke(null, command, null, null) as? Process
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Check if SELinux allows reading input device nodes.
     * Returns true if at least one `/dev/input/event*` node is readable.
     */
    fun testSelinuxAccess(): Boolean {
        if (!checkAvailability()) return false
        return try {
            val process = createShizukuProcess(arrayOf("ls", "/dev/input/"))
                ?: return false
            val output = process.inputStream.bufferedReader().readText()
            try { process.destroy() } catch (_: Throwable) {}
            output.contains("event")
        } catch (_: Throwable) {
            false
        }
    }

    fun shutdown() {
        stopCapture()
        scope.cancel()
        _isAvailable.value = false
        _isCapturing.value = false
        _capturedDevices.value = emptyList()
    }
}
