# VitualEmu — Physical Input Capture: Complete Feasibility Plan

> [!IMPORTANT]
> **PLAN MODE — No code changes have been made. This document is the complete deliverable for review and approval before any implementation begins.**

---

## Table of Contents

1. [Repository Audit](#1-repository-audit)
2. [Input Architecture Audit](#2-input-architecture-audit)
3. [Capture Method Research](#3-capture-method-research)
4. [Feasibility Matrix](#4-feasibility-matrix)
5. [Keyboard vs Mouse Conclusions](#5-keyboard-vs-mouse-conclusions)
6. [Recommended Implementation Approach](#6-recommended-implementation-approach)
7. [Android 11–16 Compatibility](#7-android-1116-compatibility)
8. [Google Play Policy Implications](#8-google-play-policy-implications)
9. [Permissions & User Activation Flow](#9-permissions--user-activation-flow)
10. [Proposed Architecture & Event Pipeline](#10-proposed-architecture--event-pipeline)
11. [Input Event Correctness](#11-input-event-correctness)
12. [File Changes Required](#12-file-changes-required)
13. [Dependencies](#13-dependencies)
14. [Capture State Machine & Failure Handling](#14-capture-state-machine--failure-handling)
15. [Testing Plan](#15-testing-plan)
16. [Known Limitations](#16-known-limitations)
17. [Milestones & Acceptance Criteria](#17-milestones--acceptance-criteria)
18. [Final Verdict](#18-final-verdict)

---

## 1. Repository Audit

**Branch:** `main`
**Latest commit:** `e400e20` — "Real Physical Input Capture & End-to-End Mapping"

### Project Structure Summary

| Layer | Key Files |
|---|---|
| Entry | `MainActivity.kt` (326 lines) — single Compose Activity, landscape |
| ViewModel | `VitualEmuViewModel.kt` (969 lines) — orchestrates everything |
| Input Capture | `PhysicalInputEventSource.kt` (43 lines), `OverlayInputEventSource` |
| Mapping | `KeymapTranslationEngine.kt` (197 lines) |
| Dispatch | `MultiTouchDispatcher.kt` (301 lines), `MultiTouchPointerCombiner.kt` (379 lines) |
| Mouse Look | `MouseLookEngine.kt` (300 lines) |
| Bridge (output) | `ShizukuBridgeAdapter.kt` (431 lines), `RootlessWirelessAdbBridgeAdapter.kt` (540 lines) |
| Overlay | `PlayModeOverlayService.kt` (994 lines) — TYPE_APPLICATION_OVERLAY HUD |
| Session | `SessionManager.kt` (87 lines) |
| Devices | `DeviceManager.kt` (108 lines) — InputDeviceListener for hotplug |
| Runtime Bridge | `PlayModeOverlayRuntimeBridge` (singleton object in PlayModeOverlayService.kt) |

---

## 2. Input Architecture Audit

### 2.1 Current Event Flow (Intended)

```
Physical Hardware
    ↓
  ???  ← NO SYSTEM-LEVEL CAPTURE MECHANISM EXISTS
    ↓
PlayModeOverlayRuntimeBridge.onPhysicalKeyCaptured
    ↓
VitualEmuViewModel.dispatchSandboxKeyEvent()
    ↓
KeymapTranslationEngine.handleKeyEvent()
    ↓
MultiTouchDispatcher → MultiTouchPointerCombiner
    ↓
InputBridge (Shizuku IInputManager / ADB shell sendevent)
    ↓
Game receives injected touch
```

### 2.2 Critical Finding: No Physical Input Capture Exists

**`PhysicalInputEventSource.kt`** is a 43-line file containing:
- A `PhysicalKeyEvent` data class
- A `PhysicalInputEventSource` interface with `startListening()` / `stopListening()`
- `OverlayInputEventSource` — an implementation that simply **registers a lambda on** `PlayModeOverlayRuntimeBridge.onPhysicalKeyCaptured`

**This is a callback relay, not an input capture mechanism.**

Tracing through the entire codebase:

1. **`OverlayInputEventSource.startListening()`** sets `PlayModeOverlayRuntimeBridge.onPhysicalKeyCaptured` to forward events to `KeymapTranslationEngine`
2. **`VitualEmuViewModel.init`** also sets `PlayModeOverlayRuntimeBridge.onPhysicalKeyCaptured` (overwriting the OverlayInputEventSource registration)
3. **`PlayModeOverlayService`** — the overlay service has `FLAG_NOT_FOCUSABLE` set. It **never calls** `onPhysicalKeyCaptured`. It only handles touch drag for the floating icon.
4. **No component in the entire repository produces physical keyboard key events.**
5. **No component produces physical mouse events.**
6. **`PlayModeOverlayRuntimeBridge.onPhysicalMouseDeltaCaptured`** is wired in the ViewModel but **nothing ever invokes it**.

### 2.3 What Does Work

| Component | Status |
|---|---|
| **DeviceManager** | ✅ Works — correctly uses `InputManager.InputDeviceListener` to detect keyboard/mouse connect/disconnect. **But this is device discovery, not event capture.** |
| **KeymapTranslationEngine** | ✅ Works — if fed `(keyCode, isDown)` pairs, correctly resolves WASD, mappings, macros, mouse look toggle |
| **MultiTouchDispatcher** | ✅ Works — 10-slot pointer management with Protocol B evdev commands |
| **MultiTouchPointerCombiner** | ✅ Works — builds MotionEvent frames for Shizuku `injectInputEvent` |
| **MouseLookEngine** | ✅ Works — sub-pixel accumulation, sensitivity, acceleration, edge-recenter, free-look |
| **ShizukuBridgeAdapter** | ✅ Works — `IInputManager.injectInputEvent()` via reflection + `sendevent` fallback for **output injection** |
| **RootlessWirelessAdbBridgeAdapter** | ⚠️ Partial — ADB protocol CNXN/OPEN/WRTE framer connects to local daemon for shell commands. **Does not perform Wireless Debugging pairing** (the SPAKE2 handshake). Connection only works if the device is already authorized. |
| **PlayModeOverlayService** | ✅ Works — floating HUD, side menu, keymap settings, game-scoped visibility |
| **HeldInputRegistry** | ✅ Works — tracks held keys and active touches for safe-release |

### 2.4 Dead Code / Misleading States

| Item | Issue |
|---|---|
| `OverlayInputEventSource` | Exists but overwritten by ViewModel init; never actually produces events |
| `PhysicalInputEventSource` interface | Unused abstraction — no alternative implementation exists |
| `onPhysicalMouseDeltaCaptured` | Wired in ViewModel but never invoked by any component |
| `isInputGrabEnabled` | State toggled by overlay menu "Keyboard Typing" button but has no capture effect |
| Overlay `FLAG_NOT_FOCUSABLE` | Cannot receive key events when game is in foreground |
| ADB pairing | `LocalAdbShellTransport` does CNXN+AUTH with an ephemeral RSA key, but Android Wireless Debugging requires a SPAKE2+HKDF TLS pairing handshake on the **pairing port** first. The current code connects to the **daemon port** directly. |

---

## 3. Capture Method Research

### 3A. AccessibilityService

#### Keyboard Capture via `onKeyEvent()`

`AccessibilityService.onKeyEvent(KeyEvent)` is called for **all physical key events system-wide** when `FLAG_REQUEST_FILTER_KEY_EVENTS` is set in the service config XML (`android:accessibilityFlags="flagRequestFilterKeyEvents"`).

**Verified capabilities:**
- Receives `KeyEvent.ACTION_DOWN` and `KeyEvent.ACTION_UP` from physical keyboards
- Works while **any** app is in the foreground (including third-party games)
- Can **consume** (return `true`) or **pass through** (return `false`) each event selectively
- Receives key repeat events (successive ACTION_DOWN with `repeatCount > 0`)
- Works on Android 11–16 (API 30–36)
- `KeyEvent.getDevice()` provides device ID, vendor ID, product ID for device identification
- The service receives events regardless of which window has focus

**Limitations:**
- `canRequestFilterKeyEvents` requires the service to be configured via XML `meta-data` — cannot be toggled purely at runtime
- Volume keys, power button, and some OEM special keys may be filtered before reaching the service
- Some OEM skins (Xiaomi MIUI, Samsung One UI) may delay or restrict accessibility service startup
- **Cannot inject** events to other apps (only capture) — but VitualEmu already has Shizuku/ADB for injection

#### Mouse Capture via AccessibilityService

**`FLAG_SEND_MOTION_EVENTS`** (added in API 33, Android 13):
- Delivers `MotionEvent` objects to `onMotionEvent(MotionEvent event)` callback
- **Delivers absolute screen coordinates** for touch and mouse events
- For a physical mouse, provides `ACTION_HOVER_MOVE`, `ACTION_HOVER_ENTER`, `ACTION_HOVER_EXIT` with absolute X/Y
- **Does NOT provide relative mouse deltas** — only absolute cursor position
- This is **not usable for FPS mouse-look** which requires raw relative movement (dx, dy)
- Mouse buttons: `ACTION_BUTTON_PRESS` / `ACTION_BUTTON_RELEASE` are delivered
- Mouse wheel: `ACTION_SCROLL` with `AXIS_VSCROLL` / `AXIS_HSCROLL` is delivered
- Available only on API 33+ — not on Android 11 or 12

**Verdict for mouse via AccessibilityService:**
- Mouse buttons and wheel: ✅ Supported (API 33+)
- Relative mouse movement for FPS: ❌ **Unsupported** — only absolute coordinates available
- API 30–32 mouse: ❌ Not available

#### Pointer Capture via Overlay

`View.requestPointerCapture()` (API 26+):
- Delivers **relative mouse deltas** (dx, dy) — exactly what FPS mouse-look needs
- **Requires the View's window to have focus** — the view hierarchy must be focused
- A `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE` **cannot** request pointer capture
- If the overlay takes focus (`FLAG_NOT_FOCUSABLE` removed), **the game loses focus** and stops receiving touch input
- `requestPointerCapture()` hides the mouse cursor and delivers relative events to `onCapturedPointerEvent()`

**This creates a fundamental conflict:** the game needs focus to be playable, but pointer capture requires VitualEmu's view to have focus.

**Possible resolution — focusable overlay with pass-through:**
A `TYPE_APPLICATION_OVERLAY` could be made focusable to capture pointer events, but:
1. The game would lose input focus
2. The overlay would need to re-inject all non-mapped events back to the game
3. This introduces significant latency
4. Some games detect focus loss and pause
5. Android 12+ restricts overlay touch passthrough (`FLAG_NOT_TOUCHABLE` + untrusted touches blocked)

**Verdict:** Not viable for transparent game overlay use.

### 3B. Shizuku UserService — `/dev/input/` getevent

Shizuku grants **shell UID (2000)** permissions to the app's bound service process.

#### Event Reading via `getevent`

- Shell UID has **read access to `/dev/input/eventN`** device nodes on most devices
- `getevent -l` enumerates all input devices and their event types
- `getevent /dev/input/eventN` outputs raw Linux input events: `EV_KEY`, `EV_REL`, `EV_ABS`
- This provides **true hardware-level events** — key down, key up, relative mouse dx/dy, button presses, wheel

**Verified capabilities for keyboard:**
- `EV_KEY` with `KEY_W`, `KEY_A`, `KEY_S`, `KEY_D`, etc. — exact physical key events
- Key down (`value=1`), key repeat (`value=2`), key up (`value=0`)
- Device-specific: events come from the correct `/dev/input/eventN` for the USB keyboard
- Works regardless of which app has foreground focus
- Device identification: `/dev/input/eventN` → `cat /sys/class/input/eventN/device/name`

**Verified capabilities for mouse:**
- `EV_REL` + `REL_X` / `REL_Y` — **raw relative mouse movement** (the dx/dy FPS games need)
- `EV_KEY` + `BTN_LEFT` / `BTN_RIGHT` / `BTN_MIDDLE` — mouse button events
- `EV_REL` + `REL_WHEEL` — mouse wheel events
- Works while any app is in foreground

**Implementation approach:**
1. Shizuku `UserService` runs in a separate process with shell UID
2. The service opens `/dev/input/eventN` via `FileInputStream` or calls `getevent` in a loop
3. Events are parsed and sent to the app process via AIDL/Binder IPC
4. Device enumeration: list `/dev/input/` → read `/sys/class/input/eventN/device/name`

**Limitations:**
- **SELinux:** On Android 11+, SELinux policy restricts shell from reading some input device nodes. The restriction varies by OEM:
  - AOSP / Pixel: shell can typically read `/dev/input/eventN`
  - Samsung: some devices restrict access
  - Xiaomi/MIUI: generally permissive for shell
  - Must be tested per-device
- **Device node paths** are not stable — `/dev/input/event2` may be a different device after reboot. Must enumerate dynamically.
- **Process lifecycle:** Shizuku UserService may be killed when Shizuku is stopped or the app is force-stopped. Must handle reconnection.
- **Shizuku itself must be running** — user must start Shizuku via Wireless Debugging or ADB first
- **Concurrent reading:** Only one process should read from a device node at a time. The system InputReader also reads the same nodes, but Linux allows multiple readers via `inotify`/`open()`.

**Critical note:** Reading `/dev/input/eventN` does NOT consume the event from the system. The kernel delivers events to all file descriptors that have the node open. The game will still receive the event through the normal Android input pipeline. This means:
- No risk of "eating" the game's input
- But also means **double-input** for mapped keys — both VitualEmu's injected touch AND the original key event reach the game
- Solution: The game doesn't use keyboard input (it's a touch game), so the physical key arriving at the game has no effect unless the game has keyboard bindings

#### `getevent` vs Direct `open()`

| Approach | Pros | Cons |
|---|---|---|
| Spawn `getevent` process | Simple, text parsing | Process overhead, stdout parsing latency |
| Direct `open("/dev/input/eventN")` + `read()` | Low latency, binary parsing, no process spawn | Requires JNI or Java NIO `FileChannel`; SELinux may block `open()` even for shell UID |
| `inotify` on `/dev/input/` | Detects device add/remove | Only for discovery, not event reading |

**Recommended:** Start with `getevent` process spawned via Shizuku for reliability, with option to upgrade to direct `open()` + `read()` for lower latency.

### 3C. Wireless ADB

**Current implementation audit:**

`LocalAdbShellTransport` implements ADB protocol framing (CNXN, AUTH, OPEN, WRTE, CLSE) and connects to `127.0.0.1:<port>` to open an interactive shell.

**Critical finding:** This is a **daemon port** connection, not a Wireless Debugging **pairing** operation.

Android Wireless Debugging has two ports:
1. **Pairing port** (shown briefly in Developer Options → Wireless Debugging → "Pair device with pairing code") — requires SPAKE2+HKDF+TLS handshake with the 6-digit code
2. **Daemon port** (shown as the main port in Wireless Debugging settings) — standard ADB CNXN+AUTH

The current code connects to the daemon port using an ephemeral RSA key. This will **fail unless the device has already been paired** via `adb pair` from a computer or another method.

**Can Wireless ADB provide input capture?** No. ADB provides a **shell command transport**. You can use it to:
- Run `getevent` (same as Shizuku but via ADB shell instead)
- Run `input` commands for injection

But it does not provide any input capture mechanism beyond what `getevent` already offers. And `getevent` via ADB shell has the same SELinux constraints as via Shizuku shell.

**Verdict:** Wireless ADB is a **touch injection transport** (output), not an input capture mechanism. The existing implementation needs the SPAKE2 pairing handshake to work from scratch — which is a complex cryptographic protocol.

### 3D. Android Public APIs (InputManager, etc.)

| API | What It Does | Captures Events? |
|---|---|---|
| `InputManager.getInputDeviceIds()` | Lists connected device IDs | ❌ Discovery only |
| `InputManager.getInputDevice(id)` | Device metadata (name, sources, vendor/product ID) | ❌ Discovery only |
| `InputManager.InputDeviceListener` | Hotplug callbacks (added/removed/changed) | ❌ Discovery only |
| `View.onKeyEvent()` / `Activity.onKeyDown()` | Key events for the app's own windows | ❌ Only when app has focus |
| `View.onGenericMotionEvent()` | Mouse/gamepad events for the app's own windows | ❌ Only when app has focus |
| `View.requestPointerCapture()` | Relative mouse events for the app's View | ❌ Requires window focus (game loses focus) |
| `onCapturedPointerEvent()` | Receives relative mouse deltas | ❌ Requires pointer capture (see above) |

**No public Android API allows a background/overlay app to capture physical keyboard or mouse events globally.**

### 3E. Overlay and Current Window Flags

Current overlay flags in `PlayModeOverlayService`:
```
FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN
```

When the side menu opens, the flags change to:
```
FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN
```
(removes `FLAG_NOT_FOCUSABLE` so the menu is interactive)

**Impact on input capture:**
- With `FLAG_NOT_FOCUSABLE`: overlay cannot receive key events. Game retains focus.
- Without `FLAG_NOT_FOCUSABLE`: overlay steals focus. Game may pause or stop responding.
- Neither configuration enables global keyboard/mouse capture while the game is the focused app.

---

## 4. Feasibility Matrix

| Capability | Android Public API | AccessibilityService | Shizuku (`getevent`) | Wireless ADB |
|---|---|---|---|---|
| **Global keyboard key-down** | ❌ Unsupported | ✅ Supported | ✅ Supported | ❌ N/A (injection only) |
| **Global keyboard key-up** | ❌ Unsupported | ✅ Supported | ✅ Supported | ❌ N/A |
| **Key repeat** | ❌ Unsupported | ✅ Supported | ✅ Supported | ❌ N/A |
| **Mouse buttons** | ❌ Unsupported | ✅ Supported (API 33+) | ✅ Supported | ❌ N/A |
| **Mouse wheel** | ❌ Unsupported | ✅ Supported (API 33+) | ✅ Supported | ❌ N/A |
| **Relative mouse movement** | ❌ Unsupported | ❌ **Unsupported** (absolute only) | ✅ **Supported** (EV_REL) | ❌ N/A |
| **Foreground third-party game** | ❌ Unsupported | ✅ Supported | ✅ Supported | ❌ N/A |
| **Android 11 (API 30)** | ❌ | ✅ Keys only | ✅ Full | ❌ |
| **Android 12 (API 31–32)** | ❌ | ✅ Keys only | ✅ Full | ❌ |
| **Android 13–16 (API 33–36)** | ❌ | ✅ Keys + mouse (no rel.) | ✅ Full | ❌ |
| **No-root operation** | ✅ | ✅ | ✅ (Shizuku = no root) | ✅ |
| **User permission required** | N/A | Accessibility Settings | Shizuku grant | ADB pairing |
| **Google Play compatibility** | ✅ | ⚠️ **Restricted** | ✅ (Shizuku is third-party) | ✅ |
| **OEM dependencies** | None | Some OEM restrictions | SELinux varies | N/A |

---

## 5. Keyboard vs Mouse Conclusions

### Keyboard Capture

**Two viable approaches, one recommended:**

| Approach | Verdict |
|---|---|
| **AccessibilityService** | ✅ Most reliable for keyboard. Works on API 30–36. Key down, up, repeat, device ID. Google Play policy concerns (see §8). |
| **Shizuku getevent** | ✅ Also works. More setup required (Shizuku running). No Play policy issues. SELinux device-dependent. |

**Recommendation:** **AccessibilityService for keyboard** as the primary path. Shizuku getevent as a fallback for users who cannot or choose not to enable accessibility.

### Mouse Capture

**Only one viable approach for FPS mouse-look:**

| Approach | Relative Mouse Movement? | Verdict |
|---|---|---|
| AccessibilityService `FLAG_SEND_MOTION_EVENTS` | ❌ Absolute only | Not viable for FPS |
| Pointer capture (`requestPointerCapture`) | ✅ Relative | Not viable — requires focus, steals from game |
| **Shizuku getevent `EV_REL`** | ✅ **Relative** | ✅ **Only viable approach** |

**Recommendation:** **Shizuku getevent for mouse** — the only method that provides raw relative movement while a game is in the foreground without root.

### Summary

> **Keyboard capture and mouse capture require DIFFERENT mechanisms.**
>
> - **Keyboard:** AccessibilityService (primary) or Shizuku getevent (fallback)
> - **Mouse (FPS relative movement):** Shizuku getevent (only viable option)

---

## 6. Recommended Implementation Approach

### Dual-Source Architecture

```
┌─────────────────────────────────┐
│     AccessibilityService        │
│  onKeyEvent() → keyboard       │ ← API 30–36
│  (optional) onMotionEvent()    │ ← API 33+ (buttons/wheel only)
│     → mouse buttons & wheel    │
└────────────┬────────────────────┘
             │ Binder / LocalBroadcast
             ▼
┌─────────────────────────────────┐
│     Unified Input Dispatcher    │
│  (in VitualEmuViewModel)        │ ← Merges events from both sources
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│   Shizuku UserService           │
│  getevent → mouse EV_REL       │ ← Relative mouse dx/dy
│  getevent → mouse EV_KEY       │ ← BTN_LEFT, BTN_RIGHT, BTN_MIDDLE
│  getevent → mouse EV_REL       │ ← REL_WHEEL
│  (fallback) keyboard EV_KEY    │ ← When AccessibilityService unavailable
└────────────┬────────────────────┘
             │ AIDL Binder IPC
             ▼
┌─────────────────────────────────┐
│   Physical Input Router         │
│   Dedup, normalize, timestamp   │
│   Device identification         │
│   Source priority resolution    │
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│   KeymapTranslationEngine       │ ← Existing, works
│   MouseLookEngine               │ ← Existing, works
│   MultiTouchDispatcher          │ ← Existing, works
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│   Shizuku IInputManager /       │
│   ADB Shell sendevent           │ ← Existing output bridges
└─────────────────────────────────┘
```

### Why Dual-Source?

1. AccessibilityService is the **most reliable** keyboard capture on all Android 11–16 devices with no SELinux issues
2. Shizuku getevent is the **only way** to get relative mouse movement
3. Both Shizuku and AccessibilityService are already part of the app's activation ecosystem
4. Users who decline accessibility can fall back to Shizuku-only capture (keyboard + mouse)
5. Users without Shizuku lose mouse relative movement but keep keyboard capture via accessibility

---

## 7. Android 11–16 Compatibility

| Feature | API 30 | API 31 | API 32 | API 33 | API 34 | API 35 | API 36 |
|---|---|---|---|---|---|---|---|
| AccessibilityService onKeyEvent | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| AccessibilityService onMotionEvent | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ |
| Shizuku getevent keyboard | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Shizuku getevent mouse | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| SELinux `/dev/input` (AOSP) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅* | ✅* |
| FGS specialUse | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

*API 35–36 have tightened SELinux on some Pixel builds; must be verified per-device.

---

## 8. Google Play Policy Implications

### AccessibilityService Policy

Google Play restricts Accessibility API usage. Key requirements:

1. **`isAccessibilityTool` attribute:** Apps that set `isAccessibilityTool=true` in their service config must **genuinely be accessibility tools** (screen readers, switch access, voice control). VitualEmu is not an accessibility tool.

2. **Apps that DON'T set `isAccessibilityTool=true`:**
   - Must declare Accessibility API usage in the Play Console Data Safety form
   - Must provide a **prominent disclosure** to users explaining why accessibility permission is needed
   - Must obtain **user consent** before enabling the service
   - The service declaration must include a clear description

3. **Google's enforcement:** Apps using Accessibility API for non-accessibility purposes (automation, data scraping, ad fraud) are removed. However, legitimate input remapping/accessibility-adjacent tools (like key remappers for accessibility) have historically been permitted when properly disclosed.

4. **Recommended approach for VitualEmu:**
   - Do NOT set `isAccessibilityTool=true`
   - Set a clear `android:description` explaining keyboard capture for game control mapping
   - Show a prominent in-app disclosure before directing users to enable the service
   - Make accessibility entirely optional (Shizuku-only mode works without it)
   - In the Play Console, declare the Accessibility API usage and provide a privacy policy

### Shizuku Policy

Shizuku itself is a third-party tool. Google Play does not restrict apps that integrate the Shizuku SDK. The user must install and run Shizuku independently. No special Play policy declarations are needed for Shizuku integration.

---

## 9. Permissions & User Activation Flow

### Required Permissions

| Permission | Purpose | User Action |
|---|---|---|
| Shizuku API | Mouse capture (getevent), touch injection | Start Shizuku app, grant permission |
| Accessibility Service | Keyboard capture (onKeyEvent) | Settings → Accessibility → Enable |
| Draw Over Other Apps | Overlay HUD | Settings → Special Access → grant |
| Foreground Service | Keep overlay alive | Automatic (declared in manifest) |
| Usage Access | Game-scoped overlay visibility | Settings → Special Access → grant |

### Activation Wizard Flow

```
Step 1: Install & Start Shizuku
  → User opens Shizuku app
  → Starts Shizuku via Wireless Debugging or ADB
  → Returns to VitualEmu
  → VitualEmu requests Shizuku permission
  → [Grants mouse capture + touch injection]

Step 2: Enable Accessibility Service (Optional but recommended for keyboard)
  → Show prominent disclosure dialog:
    "VitualEmu uses the Accessibility Service ONLY to capture
     physical keyboard key presses while games are running.
     No screen content is read or stored."
  → User taps "Enable" → Opens Accessibility Settings
  → User enables VitualEmu service
  → Returns to app
  → [Grants keyboard capture]

Step 3: Overlay Permission
  → Existing flow (unchanged)

Step 4: Preflight Check
  → Verify at least one capture source is active
  → Show capture source status:
    - Keyboard: AccessibilityService ✅ / Shizuku Fallback ✅ / None ❌
    - Mouse: Shizuku getevent ✅ / None ❌
```

---

## 10. Proposed Architecture & Event Pipeline

### Core Interfaces

```kotlin
// New enum for event source identification
enum class CaptureSource {
    ACCESSIBILITY_SERVICE,
    SHIZUKU_GETEVENT,
    NONE
}

// New unified physical input event
data class PhysicalInputEvent(
    val type: PhysicalInputType,
    val keyCode: Int = 0,          // For KEY events
    val isDown: Boolean = false,    // For KEY events
    val repeatCount: Int = 0,
    val deltaX: Float = 0f,         // For MOUSE_MOVE (relative)
    val deltaY: Float = 0f,         // For MOUSE_MOVE (relative)
    val wheelDelta: Float = 0f,     // For MOUSE_WHEEL
    val deviceId: Int = -1,
    val deviceName: String = "",
    val source: CaptureSource,
    val timestampNs: Long = System.nanoTime(),
    val isSynthetic: Boolean = false
)

enum class PhysicalInputType {
    KEY_DOWN,
    KEY_UP,
    KEY_REPEAT,
    MOUSE_BUTTON_DOWN,
    MOUSE_BUTTON_UP,
    MOUSE_MOVE,
    MOUSE_WHEEL,
    DEVICE_CONNECTED,
    DEVICE_DISCONNECTED
}
```

### Capture State Machine

```kotlin
enum class CaptureState {
    NOT_CONFIGURED,       // No capture source set up
    PERMISSION_REQUIRED,  // Source selected but permission not granted
    INITIALIZING,         // Starting capture service/process
    READY,                // Capture configured but not actively capturing
    CAPTURING,            // Actively capturing and forwarding events
    PAUSED,               // Play Mode paused, capture suspended
    DEVICE_DISCONNECTED,  // Input device removed during capture
    PERMISSION_REVOKED,   // Permission lost while capturing
    CAPTURE_UNAVAILABLE,  // SELinux/OS restriction prevents capture
    ERROR                 // Unexpected failure
}
```

### Event Pipeline Design

```
AccessibilityService                    Shizuku UserService
┌──────────────────┐            ┌──────────────────────────┐
│ onKeyEvent()     │            │ getevent parser loop     │
│ onMotionEvent()  │            │ → keyboard EV_KEY        │
│                  │            │ → mouse EV_REL           │
│ → KeyEvent data  │            │ → mouse EV_KEY (buttons) │
└──────┬───────────┘            └──────────┬───────────────┘
       │ LocalBroadcast/                   │ AIDL Binder
       │ SharedFlow                        │
       ▼                                   ▼
┌──────────────────────────────────────────────┐
│          PhysicalInputRouter                  │
│  • Deduplication (same event from 2 sources) │
│  • Source priority (Accessibility > Shizuku   │
│    for keys; Shizuku only for mouse rel.)    │
│  • Device identification & filtering         │
│  • Timestamp validation                      │
│  • Event normalization to PhysicalInputEvent  │
│  • Session gate (only forward if CAPTURING)   │
└──────────────────┬───────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────────┐
│       KeymapTranslationEngine                 │ (existing)
│       MouseLookEngine                         │ (existing)
│       MultiTouchDispatcher                    │ (existing)
│       → InputBridge (Shizuku/ADB)            │ (existing)
└──────────────────────────────────────────────┘
```

### Deduplication Strategy

When both AccessibilityService and Shizuku getevent are active:
- Both may report the same keyboard key event
- The `PhysicalInputRouter` uses a sliding window deduplicator:
  - Key: `(keyCode, isDown, deviceId)`
  - Window: 50ms
  - First event is forwarded; duplicate within window is dropped
  - Priority: AccessibilityService key events preferred (lower latency, no SELinux risk)

---

## 11. Input Event Correctness

### WASD Movement
- Key-down → `KeymapTranslationEngine.handleKeyEvent(keyCode, isDown=true)` → WASD stick vector update
- Multiple simultaneous keys → `HeldInputRegistry` tracks all held keys; stick vector computed from W+A, W+D, etc.
- Key-up → stick returns to center when all WASD keys released

### Key Down/Up Pairing
- Every `KEY_DOWN` must eventually have a `KEY_UP`
- `HeldInputRegistry` tracks all held keys
- On session stop / focus loss / device disconnect → `keymapEngine.neutralizeAll()` releases all

### Key Repeat
- Accessibility: `KeyEvent.getRepeatCount() > 0` on subsequent ACTION_DOWN
- Shizuku getevent: `EV_KEY value=2` (repeat)
- **Policy:** Key repeat events are **ignored** (not forwarded to mapping engine). Only initial down and final up matter. Repeats would cause unintended rapid tap-repeat.

### Mouse Relative Movement
- Shizuku getevent provides `EV_REL REL_X <delta>` and `EV_REL REL_Y <delta>`
- Accumulated per `SYN_REPORT` frame → single `(dx, dy)` pair
- Normalized: `dxNorm = dx / screenWidth`, `dyNorm = dy / screenHeight`
- Fed to `MouseLookEngine.onMouseDelta(dxNorm, dyNorm)` — existing sub-pixel engine

### Mouse Buttons
- Left click → `KeymapTranslationEngine.handleKeyEvent(KEYCODE_MOUSE_LCLICK, isDown)`
- Right click → mapped to configured action
- Middle click → optional mapping

### Mouse Wheel
- `REL_WHEEL` → mapped to scroll action or zoom

### Sensitivity Scaling
- Existing `MouseLookConfig.sensitivityX/Y` applied in `MouseLookEngine`
- No changes needed

### Device Disconnection While Key Held
1. `DeviceManager.onInputDeviceRemoved()` fires
2. `HeldInputRegistry.releaseAll()` clears all held keys and touches
3. `keymapEngine.neutralizeAll()` lifts all injected pointers
4. Capture state → `DEVICE_DISCONNECTED`
5. No stuck virtual buttons

### Duplicate Physical Events
- Deduplication in `PhysicalInputRouter` (see §10)
- Same event from AccessibilityService and Shizuku within 50ms → only first processed

### Key Consumption vs Pass-Through
- **AccessibilityService:** `onKeyEvent()` returning `true` **consumes** the event (game doesn't see it)
- **Policy decision:** For **mapped keys**, return `true` (consume) to prevent double-input. For **unmapped keys**, return `false` (pass through to game).
- **Shizuku getevent:** Reading `/dev/input/` does NOT consume events. The game receives the raw key regardless. Since touch games don't have keyboard bindings, this is harmless. If a game has keyboard bindings, the user should use AccessibilityService mode where events can be consumed.

### Session Lifecycle Safety
| Event | Action |
|---|---|
| Play Mode starts | Capture state → CAPTURING |
| Play Mode pauses | `neutralizeAll()`, capture state → PAUSED |
| Play Mode stops | `neutralizeAll()`, capture → READY |
| Emergency Safe Stop | `neutralizeAll()`, capture → NOT_CONFIGURED |
| Focus loss | `neutralizeAll()`, capture → PAUSED |
| Permission revoked | `neutralizeAll()`, capture → PERMISSION_REVOKED |
| Device disconnected | `neutralizeAll()`, capture → DEVICE_DISCONNECTED |

---

## 12. File Changes Required

### New Files

| File | Purpose |
|---|---|
| `runtime/capture/VitualEmuAccessibilityService.kt` | AccessibilityService subclass with `onKeyEvent()` and optional `onMotionEvent()` |
| `runtime/capture/ShizukuInputCaptureService.kt` | AIDL-based Shizuku UserService that runs `getevent` and parses events |
| `runtime/capture/IInputCaptureCallback.aidl` | AIDL interface for Shizuku→app Binder IPC |
| `runtime/capture/PhysicalInputRouter.kt` | Deduplication, normalization, device filtering, session gating |
| `runtime/capture/CaptureStateManager.kt` | State machine for capture lifecycle |
| `runtime/capture/GeteventParser.kt` | Linux input event binary/text parser |
| `res/xml/accessibility_service_config.xml` | AccessibilityService XML configuration |
| `res/values/strings.xml` additions | Accessibility service description string |

### Modified Files

| File | Changes |
|---|---|
| `AndroidManifest.xml` | Add AccessibilityService declaration with `BIND_ACCESSIBILITY_SERVICE` permission |
| `VitualEmuViewModel.kt` | Wire `PhysicalInputRouter` outputs to `KeymapTranslationEngine` and `MouseLookEngine`; replace current `PlayModeOverlayRuntimeBridge.onPhysicalKeyCaptured` wiring |
| `PhysicalInputEventSource.kt` | Refactor interface to match new `PhysicalInputEvent` type; `OverlayInputEventSource` replaced by actual capture sources |
| `SessionManager.kt` | Add capture state tracking; integrate `CaptureStateManager` |
| `PreflightCheckUseCase.kt` | Check for at least one active capture source |
| `PlayModeOverlayRuntimeBridge` (in PlayModeOverlayService.kt) | Add capture state flow; remove unused `onPhysicalKeyCaptured` lambda |
| `ui/screens/activation/ActivationWizardScreen.kt` | Add accessibility service activation step |
| `ui/screens/home/HomeScreen.kt` | Display capture source status |
| `RuntimeDiagnostics.kt` | Add capture event logging |
| `domain/model/Models.kt` | Add `CaptureSource` enum, extend `SessionState` |

### Files NOT Changed
- `KeymapTranslationEngine.kt` — no changes needed, receives same `(keyCode, isDown)` interface
- `MultiTouchDispatcher.kt` — no changes
- `MouseLookEngine.kt` — no changes (receives same `onMouseDelta(dx, dy)`)
- `ShizukuBridgeAdapter.kt` — no changes (output injection, not capture)
- `RootlessWirelessAdbBridgeAdapter.kt` — no changes
- Any UI screens not listed above

---

## 13. Dependencies

### New Dependencies

| Dependency | Purpose | Already in project? |
|---|---|---|
| Shizuku API (`shizuku-api`) | UserService for getevent | ✅ Yes |
| Shizuku Provider | Content provider | ✅ Yes |
| AIDL compiler | Generate Binder stubs for IInputCaptureCallback | ✅ Built into AGP |

**No new third-party dependencies required.**

### Build Configuration

- Enable AIDL in `build.gradle.kts`: `buildFeatures { aidl = true }` (add alongside existing `compose = true`)
- Add AIDL source directory: `src/main/aidl/com/dsc/virtualemu/runtime/capture/`

---

## 14. Capture State Machine & Failure Handling

### States

```
NOT_CONFIGURED ──→ PERMISSION_REQUIRED ──→ INITIALIZING ──→ READY
                                                              │
                                                              ↓
                               PAUSED ←── CAPTURING ──→ ERROR
                                 │            │
                                 ↓            ↓
                              READY    DEVICE_DISCONNECTED
                                              │
                                              ↓
                                           PAUSED

PERMISSION_REVOKED ←── (any active state)
CAPTURE_UNAVAILABLE ←── INITIALIZING (SELinux block)
```

### UI Display Rules

| State | What the UI shows |
|---|---|
| NOT_CONFIGURED | "⚠ Input capture not configured — open Activation" |
| PERMISSION_REQUIRED | "🔑 Grant [Accessibility/Shizuku] permission to enable capture" |
| INITIALIZING | "⏳ Starting capture service..." |
| READY | "✅ Capture ready — start Play Mode" |
| CAPTURING | "🎮 Capturing keyboard: [source] • Mouse: [source]" |
| PAUSED | "⏸ Capture paused" |
| DEVICE_DISCONNECTED | "🔌 Input device disconnected — reconnect to resume" |
| PERMISSION_REVOKED | "⚠ [Service] permission was revoked — re-enable in Settings" |
| CAPTURE_UNAVAILABLE | "❌ Input capture blocked by system security policy" |
| ERROR | "❌ Capture error: [details]" |

### Safety Invariants

1. **No false positives:** `CAPTURING` state is set only AFTER the first real hardware event is received
2. **No stuck inputs:** Every state transition triggers `keymapEngine.neutralizeAll()`
3. **No silent failures:** Errors are logged to `RuntimeDiagnostics` and shown in UI
4. **Graceful degradation:** If Shizuku dies, keyboard capture via AccessibilityService continues; only mouse is lost
5. **Bridge independence:** Capture (input) is fully independent from injection (output bridge). Both must be operational for Play Mode.

---

## 15. Testing Plan

### 15.1 Automated Tests (JVM/Robolectric)

| Test | Setup | Expected | Pass Criteria |
|---|---|---|---|
| GeteventParser unit test | Feed raw getevent output lines | Correct PhysicalInputEvent objects | All event types parsed correctly |
| PhysicalInputRouter dedup test | Send same event twice within 50ms | Only first forwarded | Second event dropped |
| PhysicalInputRouter dedup expiry | Send same event twice with 100ms gap | Both forwarded | No false dedup |
| CaptureStateManager transitions | Invoke state transitions | Correct state flow | All transitions valid |
| Device filter test | Feed events with different deviceIds | Only configured device events forwarded | Correct filtering |
| Neutralize on state change | Transition CAPTURING→PAUSED | neutralizeAll called | HeldInputRegistry empty |

### 15.2 Physical Device Tests

**Target Device:** Redmi Note 13 5G, Android 15, USB-C hub, wired keyboard, wired mouse

| # | Test Case | Setup | Action | Expected | Observable | Pass/Fail |
|---|---|---|---|---|---|---|
| 1 | Connect keyboard before VitualEmu | Plug keyboard, then launch app | Open DeviceManager | Keyboard appears in device list | DeviceInfo with type=KEYBOARD | Keyboard listed |
| 2 | Connect keyboard after VitualEmu | Launch app, then plug keyboard | Check hotplug log | "CONNECTED" entry, keyboard in list | HotPlug log entry | Entry appears |
| 3 | Capture key-down (Accessibility) | Enable AccessibilityService, start Play Mode, game foreground | Press 'W' on keyboard | PhysicalInputEvent(KEY_DOWN, keyCode=51) received | Diagnostics log shows "KEY_DOWN (51)" | Event logged |
| 4 | Capture key-up (Accessibility) | Same as above | Release 'W' | PhysicalInputEvent(KEY_UP, keyCode=51) | Diagnostics log shows "KEY_UP (51)" | Event logged |
| 5 | Sustained key press | Same | Hold 'W' for 2 seconds | Single KEY_DOWN, no repeats forwarded, then KEY_UP | Exactly 2 events in diagnostics | Only down+up |
| 6 | Simultaneous keys | Same | Hold W+A simultaneously | Both KEY_DOWN events, WASD vector = NW | Pointer slot 0 active at NW position | Diagonal movement |
| 7 | Mouse button (Shizuku) | Enable Shizuku, connect mouse, start Play Mode | Left click | PhysicalInputEvent(MOUSE_BUTTON_DOWN, BTN_LEFT) | Diagnostics log | Button event |
| 8 | Mouse wheel (Shizuku) | Same | Scroll wheel up | PhysicalInputEvent(MOUSE_WHEEL, wheelDelta>0) | Diagnostics log | Wheel event |
| 9 | Mouse relative movement | Same | Move mouse 5cm right | PhysicalInputEvent(MOUSE_MOVE, deltaX>0, deltaY≈0) | MouseLookEngine state updates | Camera moves right |
| 10 | Game in foreground | Launch Free Fire, VitualEmu overlay visible | Press mapped key | Key captured, touch injected into game | Game character responds | Game reacts |
| 11 | Events during game focus | Same as 10 | Multiple keys + mouse | All events captured and mapped | Diagnostics shows event stream | Continuous capture |
| 12 | Mapped events reach engine | Map 'F' to Fire position | Press 'F' | KeymapTranslationEngine processes as FIRE_WEAPON | Touch down at fire position | Touch injected |
| 13 | Keyboard disconnect while held | Hold 'W', then unplug keyboard | Unplug USB keyboard | KEY_UP synthesized, neutralizeAll(), DEVICE_DISCONNECTED | All pointers released, status shows disconnect | No stuck inputs |
| 14 | Mouse disconnect/reconnect | Unplug mouse, then replug | Unplug, wait 3s, replug | DEVICE_DISCONNECTED → device reappears in list | Capture resumes after reconnect | Mouse works again |
| 15 | Revoke permission during session | In Settings, disable AccessibilityService | Disable service | PERMISSION_REVOKED state, neutralizeAll() | UI shows warning | Graceful degradation |
| 16 | Pause and resume Play Mode | In Play Mode, open overlay menu | Tap pause | PAUSED, all held inputs released; resume → CAPTURING | Pointers released then re-enabled | Clean pause/resume |
| 17 | Stop Play Mode | In Play Mode, stop session | Tap stop / emergency stop | STOPPED, all held inputs released, capture stops | No events processed after stop | Clean stop |
| 18 | No capture when inactive | Close Play Mode entirely | Press keys on keyboard | No events forwarded to mapping engine | Diagnostics empty | No leakage |

### 15.3 Cross-Version Testing

| Android Version | Device | Keyboard Capture | Mouse Capture | Notes |
|---|---|---|---|---|
| Android 15 (API 35) | Redmi Note 13 5G | Primary test | Primary test | Target device |
| Android 11 (API 30) | Emulator / secondary | Verify | Verify | Minimum SDK |
| Android 13 (API 33) | If available | Verify | Verify | FLAG_SEND_MOTION_EVENTS introduced |

---

## 16. Known Limitations

| Limitation | Impact | Mitigation |
|---|---|---|
| **Relative mouse movement requires Shizuku** | Users without Shizuku cannot use FPS mouse-look | Clear UI messaging; keyboard-only mode still works |
| **SELinux may block `/dev/input/` on some OEMs** | Mouse capture unavailable on restricted devices | Detect and show CAPTURE_UNAVAILABLE; fall back to buttons-only via Accessibility |
| **AccessibilityService requires manual activation** | Users must navigate to Settings | Clear wizard with instructions |
| **Google Play Accessibility policy** | Risk of policy review flags | Optional; clear disclosure; Shizuku-only mode available |
| **Some OEMs kill background services** | Capture may stop on aggressive battery optimization | Request ignore battery optimization; use foreground service |
| **Bluetooth keyboard latency** | BT keyboards have ~10-30ms additional latency | Document; recommend wired USB for competitive gaming |
| **No mouse capture on API 30–32 without Shizuku** | AccessibilityService doesn't provide motion events pre-API 33 | Shizuku provides full coverage on all API levels |
| **ADB pairing not implemented** | Wireless ADB bridge requires manual ADB pair | Document as known gap; Shizuku is the primary path |
| **Key consumption only via Accessibility** | Shizuku getevent can't prevent game from seeing the key | For touch-only games, irrelevant; for keyboard-aware games, use Accessibility mode |

---

## 17. Milestones & Acceptance Criteria

### Milestone 1: Shizuku Mouse Capture (getevent)
**Files:** `ShizukuInputCaptureService.kt`, `IInputCaptureCallback.aidl`, `GeteventParser.kt`
**Acceptance:**
- [ ] Shizuku UserService starts, enumerates `/dev/input/` devices
- [ ] Mouse EV_REL events parsed and delivered via AIDL
- [ ] Mouse button and wheel events parsed
- [ ] Events received while game is in foreground
- [ ] Device disconnect detected

### Milestone 2: AccessibilityService Keyboard Capture
**Files:** `VitualEmuAccessibilityService.kt`, `accessibility_service_config.xml`, `AndroidManifest.xml`
**Acceptance:**
- [ ] Service declared in manifest with correct permissions and config
- [ ] `onKeyEvent()` receives physical keyboard events while game is foreground
- [ ] Mapped keys consumed (return true); unmapped keys passed through
- [ ] Key down, key up, repeat correctly identified
- [ ] Service status detectable from app (is it enabled?)

### Milestone 3: PhysicalInputRouter
**Files:** `PhysicalInputRouter.kt`, `CaptureStateManager.kt`
**Acceptance:**
- [ ] Merges events from both sources
- [ ] Deduplication works within 50ms window
- [ ] Source priority correct (Accessibility for keys, Shizuku for mouse rel.)
- [ ] Session gating: no events forwarded when not CAPTURING
- [ ] Device identification attached to events

### Milestone 4: ViewModel & UI Integration
**Files:** `VitualEmuViewModel.kt`, `SessionManager.kt`, `PreflightCheckUseCase.kt`, activation screens
**Acceptance:**
- [ ] Capture state displayed in UI accurately
- [ ] Preflight checks include capture source status
- [ ] Activation wizard includes accessibility step
- [ ] `neutralizeAll()` called on every state transition
- [ ] End-to-end: physical key press → injected touch in game

### Milestone 5: Testing & Hardening
**Acceptance:**
- [ ] All automated tests pass
- [ ] Physical device tests 1–18 pass on Redmi Note 13 5G
- [ ] No stuck virtual buttons after any failure scenario
- [ ] Diagnostics show accurate capture event stream

---

## 18. Final Verdict

### Classification: B — Feasible with limitations and additional user activation

#### Keyboard Capture
**✅ Fully feasible** via AccessibilityService on Android 11–16, no root required. User must manually enable the service in Settings. Google Play policy compliance requires proper disclosure but is manageable.

#### Mouse Capture (buttons, wheel)
**✅ Fully feasible** via Shizuku getevent (all API levels) or AccessibilityService (API 33+ only for motion events).

#### Mouse Relative Movement (FPS mouse-look)
**✅ Feasible with Shizuku** — raw EV_REL events from `/dev/input/`. This is the **only** viable approach without root. Requires Shizuku to be running. **Device-dependent** due to SELinux restrictions on some OEMs.

#### Conditions
1. **Shizuku must be installed and running** for mouse relative movement
2. **AccessibilityService must be manually enabled** for keyboard capture (or Shizuku fallback)
3. **SELinux policy** on the target device must allow shell to read `/dev/input/eventN` (most devices allow this)
4. **Google Play** requires proper Accessibility API disclosure
5. **Both capture (input) and injection (output) bridges must be independently operational**

#### What is NOT feasible
- Mouse relative movement without Shizuku or root: **Not possible**
- Global input capture via standard Android APIs alone: **Not possible**
- Input capture that works identically on every Android OEM: **Not possible** (SELinux varies)
- Pointer capture while another app has focus: **Not possible**

> **Keyboard and mouse capture require DIFFERENT mechanisms. This is a dual-source architecture by necessity, not by choice.**
