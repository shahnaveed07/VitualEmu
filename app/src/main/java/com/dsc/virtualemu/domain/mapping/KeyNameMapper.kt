package com.dsc.virtualemu.domain.mapping

import android.view.KeyEvent

object KeyNameMapper {

    fun parseKeyNameToCode(keyName: String?): Int {
        if (keyName == null || keyName.isBlank()) return 0
        val trimmed = keyName.trim()

        return when (trimmed.uppercase()) {
            "W" -> KeyEvent.KEYCODE_W
            "A" -> KeyEvent.KEYCODE_A
            "S" -> KeyEvent.KEYCODE_S
            "D" -> KeyEvent.KEYCODE_D
            "Q" -> KeyEvent.KEYCODE_Q
            "E" -> KeyEvent.KEYCODE_E
            "R" -> KeyEvent.KEYCODE_R
            "F" -> KeyEvent.KEYCODE_F
            "G" -> KeyEvent.KEYCODE_G
            "H" -> KeyEvent.KEYCODE_H
            "C" -> KeyEvent.KEYCODE_C
            "Z" -> KeyEvent.KEYCODE_Z
            "V" -> KeyEvent.KEYCODE_V
            "X" -> KeyEvent.KEYCODE_X
            "B" -> KeyEvent.KEYCODE_B
            "M" -> KeyEvent.KEYCODE_M
            "N" -> KeyEvent.KEYCODE_N
            "T" -> KeyEvent.KEYCODE_T
            "Y" -> KeyEvent.KEYCODE_Y
            "U" -> KeyEvent.KEYCODE_U
            "I" -> KeyEvent.KEYCODE_I
            "O" -> KeyEvent.KEYCODE_O
            "P" -> KeyEvent.KEYCODE_P
            "K" -> KeyEvent.KEYCODE_K
            "L" -> KeyEvent.KEYCODE_L
            "J" -> KeyEvent.KEYCODE_J
            "SPACE" -> KeyEvent.KEYCODE_SPACE
            "CTRL", "CONTROL", "LCTRL", "RCTRL" -> KeyEvent.KEYCODE_CTRL_LEFT
            "SHIFT", "LSHIFT", "RSHIFT" -> KeyEvent.KEYCODE_SHIFT_LEFT
            "ALT", "LALT", "RALT" -> KeyEvent.KEYCODE_ALT_LEFT
            "TAB" -> KeyEvent.KEYCODE_TAB
            "CAPS", "CAPSLOCK" -> KeyEvent.KEYCODE_CAPS_LOCK
            "ENTER", "RETURN" -> KeyEvent.KEYCODE_ENTER
            "ESC", "ESCAPE" -> KeyEvent.KEYCODE_ESCAPE
            "GRAVE", "`", "~" -> KeyEvent.KEYCODE_GRAVE
            "0" -> KeyEvent.KEYCODE_0
            "1" -> KeyEvent.KEYCODE_1
            "2" -> KeyEvent.KEYCODE_2
            "3" -> KeyEvent.KEYCODE_3
            "4" -> KeyEvent.KEYCODE_4
            "5" -> KeyEvent.KEYCODE_5
            "6" -> KeyEvent.KEYCODE_6
            "7" -> KeyEvent.KEYCODE_7
            "8" -> KeyEvent.KEYCODE_8
            "9" -> KeyEvent.KEYCODE_9
            "MOUSELBUTTON", "LEFTCLICK" -> KeyEvent.KEYCODE_BUTTON_L1
            "MOUSERBUTTON", "RIGHTCLICK" -> KeyEvent.KEYCODE_BUTTON_R1
            "MOUSEMBUTTON", "MIDDLECLICK" -> KeyEvent.KEYCODE_BUTTON_C
            "MOUSEWHEELUP" -> KeyEvent.KEYCODE_PAGE_UP
            "MOUSEWHEELDOWN" -> KeyEvent.KEYCODE_PAGE_DOWN
            "MOUSEXBUTTON1" -> KeyEvent.KEYCODE_BUTTON_THUMBL
            "MOUSEXBUTTON2" -> KeyEvent.KEYCODE_BUTTON_THUMBR
            "GAMEPADX" -> KeyEvent.KEYCODE_BUTTON_X
            "GAMEPADA" -> KeyEvent.KEYCODE_BUTTON_A
            "GAMEPADB" -> KeyEvent.KEYCODE_BUTTON_B
            "GAMEPADY" -> KeyEvent.KEYCODE_BUTTON_Y
            "GAMEPADLEFTSHOULDER" -> KeyEvent.KEYCODE_BUTTON_L1
            "GAMEPADRIGHTSHOULDER" -> KeyEvent.KEYCODE_BUTTON_R1
            "GAMEPADLEFTTRIGGER" -> KeyEvent.KEYCODE_BUTTON_L2
            "GAMEPADRIGHTTRIGGER" -> KeyEvent.KEYCODE_BUTTON_R2
            "GAMEPADDPADUP" -> KeyEvent.KEYCODE_DPAD_UP
            "GAMEPADDPADDOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
            "GAMEPADDPADLEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
            "GAMEPADDPADRIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
            else -> {
                if (trimmed.length == 1 && trimmed[0] in 'A'..'Z') {
                    KeyEvent.KEYCODE_A + (trimmed[0] - 'A')
                } else if (trimmed.length == 1 && trimmed[0] in '0'..'9') {
                    KeyEvent.KEYCODE_0 + (trimmed[0] - '0')
                } else {
                    0
                }
            }
        }
    }

    fun androidCodeToKeyName(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> "Space"
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> "Ctrl"
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> "Shift"
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> "Alt"
            KeyEvent.KEYCODE_TAB -> "Tab"
            KeyEvent.KEYCODE_ENTER -> "Enter"
            KeyEvent.KEYCODE_ESCAPE -> "Esc"
            KeyEvent.KEYCODE_GRAVE -> "`"
            KeyEvent.KEYCODE_BUTTON_L1 -> "Mouse LClick"
            KeyEvent.KEYCODE_BUTTON_R1 -> "Mouse RClick"
            KeyEvent.KEYCODE_BUTTON_C -> "Mouse MClick"
            KeyEvent.KEYCODE_PAGE_UP -> "MouseWheelUp"
            KeyEvent.KEYCODE_PAGE_DOWN -> "MouseWheelDown"
            KeyEvent.KEYCODE_BUTTON_THUMBL -> "MouseX1"
            KeyEvent.KEYCODE_BUTTON_THUMBR -> "MouseX2"
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
                ('A' + (keyCode - KeyEvent.KEYCODE_A)).toString()
            }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                ('0' + (keyCode - KeyEvent.KEYCODE_0)).toString()
            }
            else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
        }
    }
}
