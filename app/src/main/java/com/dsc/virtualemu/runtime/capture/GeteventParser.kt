package com.dsc.virtualemu.runtime.capture

/**
 * Parses raw `getevent` text output lines into [PhysicalInputEvent] objects.
 *
 * `getevent /dev/input/eventN` produces lines like:
 * ```
 * 0003 0035 000003e8   (EV_ABS ABS_MT_POSITION_X 1000)
 * 0001 0110 00000001   (EV_KEY BTN_LEFT 1)
 * 0002 0000 fffffffe   (EV_REL REL_X -2)
 * 0000 0000 00000000   (EV_SYN SYN_REPORT 0)
 * ```
 *
 * With `-l` flag the output is human-readable labels, but the hex format is more reliable
 * for parsing and works across all Android versions.
 *
 * This parser accumulates EV_REL deltas per SYN_REPORT frame so that a single mouse
 * movement event contains the full (dx, dy) delta for that frame.
 */
object GeteventParser {

    // Linux input event type constants
    private const val EV_SYN = 0x00
    private const val EV_KEY = 0x01
    private const val EV_REL = 0x02

    // Relative axis codes
    private const val REL_X = 0x00
    private const val REL_Y = 0x01
    private const val REL_WHEEL = 0x08
    private const val REL_HWHEEL = 0x06

    // Mouse button codes (Linux input-event-codes.h)
    private const val BTN_MOUSE = 0x110  // BTN_LEFT
    private const val BTN_RIGHT = 0x111
    private const val BTN_MIDDLE = 0x112

    // Android keycodes used to represent mouse buttons in the mapping engine
    private const val KEYCODE_MOUSE_LCLICK = 102  // KEYCODE_BUTTON_L1
    private const val KEYCODE_MOUSE_RCLICK = 103  // KEYCODE_BUTTON_R1
    private const val KEYCODE_MOUSE_MCLICK = 104  // KEYCODE_BUTTON_THUMBL

    /**
     * Stateful accumulator for building mouse-move events from per-axis EV_REL reports
     * within a single SYN_REPORT frame.
     */
    class FrameAccumulator(
        val deviceId: Int = -1,
        val deviceName: String = ""
    ) {
        private var relX: Int = 0
        private var relY: Int = 0
        private var relWheel: Int = 0
        private var hasRelData = false
        private val pendingKeyEvents = mutableListOf<PhysicalInputEvent>()

        /**
         * Feed a single parsed getevent line. Returns a list of finalized events when a
         * SYN_REPORT is encountered, or an empty list if more data is needed.
         */
        fun feedLine(line: String): List<PhysicalInputEvent> {
            val parts = line.trim().split(WHITESPACE_REGEX)
            if (parts.size < 3) return emptyList()

            val evType = parseHexInt(parts[0]) ?: return emptyList()
            val evCode = parseHexInt(parts[1]) ?: return emptyList()
            val evValue = parseHexInt(parts[2]) ?: return emptyList()

            return when (evType) {
                EV_KEY -> {
                    val event = parseKeyEvent(evCode, evValue)
                    if (event != null) {
                        pendingKeyEvents.add(event)
                    }
                    emptyList()
                }
                EV_REL -> {
                    when (evCode) {
                        REL_X -> { relX += evValue; hasRelData = true }
                        REL_Y -> { relY += evValue; hasRelData = true }
                        REL_WHEEL -> { relWheel += evValue; hasRelData = true }
                        REL_HWHEEL -> { /* horizontal wheel — ignored for now */ }
                    }
                    emptyList()
                }
                EV_SYN -> {
                    flushFrame()
                }
                else -> emptyList()
            }
        }

        /**
         * Flush accumulated data into finalized events on SYN_REPORT.
         */
        private fun flushFrame(): List<PhysicalInputEvent> {
            val result = mutableListOf<PhysicalInputEvent>()
            val now = System.nanoTime()

            // Emit accumulated key events first
            result.addAll(pendingKeyEvents)
            pendingKeyEvents.clear()

            // Emit mouse movement if we have relative data
            if (hasRelData) {
                if (relX != 0 || relY != 0) {
                    result.add(
                        PhysicalInputEvent(
                            type = PhysicalInputType.MOUSE_MOVE,
                            deltaX = relX.toFloat(),
                            deltaY = relY.toFloat(),
                            deviceId = deviceId,
                            deviceName = deviceName,
                            source = CaptureSource.SHIZUKU_GETEVENT,
                            timestampNs = now
                        )
                    )
                }
                if (relWheel != 0) {
                    result.add(
                        PhysicalInputEvent(
                            type = PhysicalInputType.MOUSE_WHEEL,
                            wheelDelta = relWheel.toFloat(),
                            deviceId = deviceId,
                            deviceName = deviceName,
                            source = CaptureSource.SHIZUKU_GETEVENT,
                            timestampNs = now
                        )
                    )
                }
                relX = 0
                relY = 0
                relWheel = 0
                hasRelData = false
            }

            return result
        }

        private fun parseKeyEvent(evCode: Int, evValue: Int): PhysicalInputEvent? {
            val now = System.nanoTime()
            return when {
                // Mouse buttons
                evCode == BTN_MOUSE || evCode == BTN_RIGHT || evCode == BTN_MIDDLE -> {
                    val androidKeyCode = when (evCode) {
                        BTN_MOUSE -> KEYCODE_MOUSE_LCLICK
                        BTN_RIGHT -> KEYCODE_MOUSE_RCLICK
                        BTN_MIDDLE -> KEYCODE_MOUSE_MCLICK
                        else -> return null
                    }
                    val type = if (evValue == 1) PhysicalInputType.MOUSE_BUTTON_DOWN
                               else PhysicalInputType.MOUSE_BUTTON_UP
                    PhysicalInputEvent(
                        type = type,
                        keyCode = androidKeyCode,
                        isDown = evValue == 1,
                        deviceId = deviceId,
                        deviceName = deviceName,
                        source = CaptureSource.SHIZUKU_GETEVENT,
                        timestampNs = now
                    )
                }
                // Keyboard keys (EV_KEY codes below 0x100 are keyboard keys; above are buttons)
                evCode in 1..0xFF -> {
                    val androidKeyCode = linuxToAndroidKeyCode(evCode)
                    val type = when (evValue) {
                        1 -> PhysicalInputType.KEY_DOWN
                        2 -> PhysicalInputType.KEY_REPEAT
                        0 -> PhysicalInputType.KEY_UP
                        else -> return null
                    }
                    PhysicalInputEvent(
                        type = type,
                        keyCode = androidKeyCode,
                        isDown = evValue != 0,
                        repeatCount = if (evValue == 2) 1 else 0,
                        deviceId = deviceId,
                        deviceName = deviceName,
                        source = CaptureSource.SHIZUKU_GETEVENT,
                        timestampNs = now
                    )
                }
                else -> null
            }
        }

        fun reset() {
            relX = 0
            relY = 0
            relWheel = 0
            hasRelData = false
            pendingKeyEvents.clear()
        }
    }

    private val WHITESPACE_REGEX = Regex("\\s+")

    /**
     * Parse a hex string that may or may not have a 0x prefix, including negative
     * two's-complement 32-bit values (e.g. "fffffffe" → -2).
     */
    private fun parseHexInt(s: String): Int? {
        return try {
            val clean = s.removePrefix("0x").removePrefix("0X")
            java.lang.Long.parseLong(clean, 16).toInt()
        } catch (_: NumberFormatException) {
            null
        }
    }

    /**
     * Maps a Linux input event key code to the closest Android KeyEvent.KEYCODE_* constant.
     *
     * The Linux kernel key codes and Android key codes share the same numbering for
     * most alphanumeric and function keys up to ~255. Some codes differ; this function
     * provides a best-effort mapping for common gaming keys.
     */
    fun linuxToAndroidKeyCode(linuxCode: Int): Int {
        // Most Linux KEY_* codes below 128 map directly to Android KEYCODE_* values.
        // The kernel headers and Android KeyEvent share the same code space for:
        //   KEY_Q(16)→KEYCODE_Q(45), KEY_W(17)→KEYCODE_W(51), KEY_E(18)→KEYCODE_E(33), etc.
        // However, the mapping is NOT 1:1 for all keys. We handle common gaming keys explicitly.
        return LINUX_TO_ANDROID_MAP[linuxCode] ?: linuxCode
    }

    /**
     * Explicit mapping table for the most commonly used gaming keys.
     * Linux KEY_* code → Android KEYCODE_*.
     */
    private val LINUX_TO_ANDROID_MAP: Map<Int, Int> = mapOf(
        // Row 1: Number row
        1 to 111,   // KEY_ESC → KEYCODE_ESCAPE
        2 to 8,     // KEY_1 → KEYCODE_1
        3 to 9,     // KEY_2 → KEYCODE_2
        4 to 10,    // KEY_3 → KEYCODE_3
        5 to 11,    // KEY_4 → KEYCODE_4
        6 to 12,    // KEY_5 → KEYCODE_5
        7 to 13,    // KEY_6 → KEYCODE_6
        8 to 14,    // KEY_7 → KEYCODE_7
        9 to 15,    // KEY_8 → KEYCODE_8
        10 to 16,   // KEY_9 → KEYCODE_9
        11 to 7,    // KEY_0 → KEYCODE_0
        12 to 69,   // KEY_MINUS → KEYCODE_MINUS
        13 to 70,   // KEY_EQUAL → KEYCODE_EQUALS
        14 to 67,   // KEY_BACKSPACE → KEYCODE_DEL

        // Row 2: QWERTY
        15 to 61,   // KEY_TAB → KEYCODE_TAB
        16 to 45,   // KEY_Q → KEYCODE_Q
        17 to 51,   // KEY_W → KEYCODE_W
        18 to 33,   // KEY_E → KEYCODE_E
        19 to 46,   // KEY_R → KEYCODE_R
        20 to 48,   // KEY_T → KEYCODE_T
        21 to 53,   // KEY_Y → KEYCODE_Y
        22 to 49,   // KEY_U → KEYCODE_U
        23 to 37,   // KEY_I → KEYCODE_I
        24 to 43,   // KEY_O → KEYCODE_O
        25 to 44,   // KEY_P → KEYCODE_P
        26 to 71,   // KEY_LEFTBRACE → KEYCODE_LEFT_BRACKET
        27 to 72,   // KEY_RIGHTBRACE → KEYCODE_RIGHT_BRACKET
        28 to 66,   // KEY_ENTER → KEYCODE_ENTER

        // Row 3: ASDF
        29 to 113,  // KEY_LEFTCTRL → KEYCODE_CTRL_LEFT
        30 to 29,   // KEY_A → KEYCODE_A
        31 to 47,   // KEY_S → KEYCODE_S
        32 to 32,   // KEY_D → KEYCODE_D
        33 to 34,   // KEY_F → KEYCODE_F
        34 to 35,   // KEY_G → KEYCODE_G
        35 to 36,   // KEY_H → KEYCODE_H
        36 to 38,   // KEY_J → KEYCODE_J
        37 to 39,   // KEY_K → KEYCODE_K
        38 to 40,   // KEY_L → KEYCODE_L
        39 to 74,   // KEY_SEMICOLON → KEYCODE_SEMICOLON
        40 to 75,   // KEY_APOSTROPHE → KEYCODE_APOSTROPHE
        41 to 68,   // KEY_GRAVE → KEYCODE_GRAVE

        // Row 4: ZXCV
        42 to 59,   // KEY_LEFTSHIFT → KEYCODE_SHIFT_LEFT
        43 to 73,   // KEY_BACKSLASH → KEYCODE_BACKSLASH
        44 to 54,   // KEY_Z → KEYCODE_Z
        45 to 52,   // KEY_X → KEYCODE_X
        46 to 31,   // KEY_C → KEYCODE_C
        47 to 50,   // KEY_V → KEYCODE_V
        48 to 30,   // KEY_B → KEYCODE_B
        49 to 42,   // KEY_N → KEYCODE_N
        50 to 41,   // KEY_M → KEYCODE_M
        51 to 55,   // KEY_COMMA → KEYCODE_COMMA
        52 to 56,   // KEY_DOT → KEYCODE_PERIOD
        53 to 76,   // KEY_SLASH → KEYCODE_SLASH
        54 to 60,   // KEY_RIGHTSHIFT → KEYCODE_SHIFT_RIGHT

        // Bottom row
        56 to 57,   // KEY_LEFTALT → KEYCODE_ALT_LEFT
        57 to 62,   // KEY_SPACE → KEYCODE_SPACE
        58 to 115,  // KEY_CAPSLOCK → KEYCODE_CAPS_LOCK
        100 to 58,  // KEY_RIGHTALT → KEYCODE_ALT_RIGHT
        97 to 114,  // KEY_RIGHTCTRL → KEYCODE_CTRL_RIGHT

        // Function keys
        59 to 131,  // KEY_F1 → KEYCODE_F1
        60 to 132,  // KEY_F2 → KEYCODE_F2
        61 to 133,  // KEY_F3 → KEYCODE_F3
        62 to 134,  // KEY_F4 → KEYCODE_F4
        63 to 135,  // KEY_F5 → KEYCODE_F5
        64 to 136,  // KEY_F6 → KEYCODE_F6
        65 to 137,  // KEY_F7 → KEYCODE_F7
        66 to 138,  // KEY_F8 → KEYCODE_F8
        67 to 139,  // KEY_F9 → KEYCODE_F9
        68 to 140,  // KEY_F10 → KEYCODE_F10
        87 to 141,  // KEY_F11 → KEYCODE_F11
        88 to 142,  // KEY_F12 → KEYCODE_F12

        // Arrow keys
        103 to 19,  // KEY_UP → KEYCODE_DPAD_UP
        108 to 20,  // KEY_DOWN → KEYCODE_DPAD_DOWN
        105 to 21,  // KEY_LEFT → KEYCODE_DPAD_LEFT
        106 to 22,  // KEY_RIGHT → KEYCODE_DPAD_RIGHT

        // Other common keys
        110 to 124, // KEY_INSERT → KEYCODE_INSERT
        111 to 112, // KEY_DELETE → KEYCODE_FORWARD_DEL
        102 to 122, // KEY_HOME → KEYCODE_MOVE_HOME
        107 to 123, // KEY_END → KEYCODE_MOVE_END
        104 to 92,  // KEY_PAGEUP → KEYCODE_PAGE_UP
        109 to 93,  // KEY_PAGEDOWN → KEYCODE_PAGE_DOWN
    )

    /**
     * Parse a single `/proc/bus/input/devices` or `/sys/class/input/` style device name line.
     * Returns the human-readable device name, or null.
     */
    fun parseDeviceName(line: String): String? {
        // Lines like: N: Name="Logitech USB Keyboard"
        val nameMatch = Regex("""N:\s*Name="(.+?)"""").find(line)
        return nameMatch?.groupValues?.getOrNull(1)
    }
}
