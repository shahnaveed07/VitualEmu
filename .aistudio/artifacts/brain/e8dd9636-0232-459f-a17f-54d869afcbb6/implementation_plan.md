# VitualEmu — Reference-Guided Architectural Audit & Implementation Plan

## 1. App Identity & Executive Overview
- **Application Name**: **VitualEmu** (`com.vitualemu.app`)
- **Core Purpose**: A high-precision Android physical keyboard & mouse input-mapping utility, profile studio, and real-time multi-touch injection runtime for mobile games.
- **Architectural Goal**: Transform the current `VitualEmu` prototype—which currently contains no-op bridge stubs, single-pointer `MotionEvent` formatting flaws, truncated mouse-look deltas, and an in-app-only HUD—into a production-grade, clean-room input virtualization system informed by the architectural lessons of `GGMouseLearnSmali` while strictly maintaining Android 11–16 security, privacy, and crash-resilience standards.

---

## 2. Reference-Guided Comparison Matrix (`GGMouseLearnSmali` vs. `VitualEmu`)

The table below maps key reference components from `GGMouseLearnSmali` (`com.ggmouse.ggapp`) against the current `VitualEmu` implementation, identifying what to adopt as clean-room Kotlin designs and what anti-patterns from the reference must be avoided.

| Subsystem | `GGMouseLearnSmali` Reference Pattern | Current `VitualEmu` Implementation | Identified Gap / Defect | Clean-Room Target Design in `VitualEmu` |
| :--- | :--- | :--- | :--- | :--- |
| **Shizuku Privileged IPC** | `AdbServerService` / binder proxy invoking shell-level `IInputManager.Stub.asInterface(ServiceManager.getService("input"))` | `ShizukuBridgeAdapter.kt` (lines 45–113): Calls `Shizuku.pingBinder()` via reflection; `injectTouch()` and `injectKey()` simply return `true` without dispatching any events | **P0 No-Op Stub**: No permission check (`checkSelfPermission`), no `UserService` or `IInputManager.injectInputEvent` invocation, and no pixel coordinate conversion | Implement `ShizukuPrivilegedInjector` using Shizuku binder reflection + `IInputManager.injectInputEvent(MotionEvent, INJECT_INPUT_EVENT_MODE_ASYNC)` with automatic fallback to Linux `sendevent` Protocol B where permitted |
| **Wireless ADB Pairing & Transport** | `AdbPairingService.smali` + `AdbServerService.smali`: Foreground service with notification input for 6-digit pairing code, local ADB socket handshake, and shell daemon execution | `RootlessWirelessAdbBridgeAdapter.kt` (lines 56–143): Validates that `pairingCode` is 4–8 digits, zeroes array, and runs a raw TCP `Socket.connect("127.0.0.1", port)` check; `injectTouch()` returns `true` as a no-op | **P0 No-Op Stub**: Does not perform ADB CNXN/OPEN shell framing or SPAKE2+/TLS pairing handshake; cannot dispatch touch/key commands over the ADB socket | Implement `LocalAdbShellTransport` supporting ADB protocol packet framing (`CNXN`, `AUTH`, `OPEN "shell:"`, `WRTE`) to a lightweight in-process/shell input injector loop, plus ephemeral pairing handshake support |
| **Multi-Touch Pointer Protocol** | `TouchPerformerSendEvent.smali` + `MappingPlugin.smali`: Maintains stateful pointer slots (`ABS_MT_SLOT`, `ABS_MT_TRACKING_ID`, `ABS_MT_POSITION_X/Y`) and combined multi-pointer `MotionEvent` arrays (`PointerProperties[]`, `PointerCoords[]`) | `MultiTouchDispatcher.kt` (lines 28–55): Allocates slots in `activeSlots`, but delegates `injectTouch(slot, action, x, y)` per-pointer in isolation; no multi-pointer `MotionEvent` assembly (`ACTION_POINTER_DOWN` / `ACTION_POINTER_UP`) | **P0 Broken Multi-Touch**: Android `InputManager.injectInputEvent` treats separate single-pointer `MotionEvent` calls as conflicting gesture streams unless active pointers are combined into a single `MotionEvent` with `pointerCount >= 2` and shifted pointer index `(index << ACTION_POINTER_INDEX_SHIFT)` | Build `MultiTouchPointerCombiner` that maintains active pointer slot state (`0..9`), constructs unified multi-pointer `MotionEvent` instances (`PointerProperties[]` + `PointerCoords[]`, proper `downTime`, `eventTime`, `SOURCE_TOUCHSCREEN`), and supports evdev Type B slot frames for fallback |
| **Mouse-Look & Aim Engine** | `MappingPlugin.smali` + `EventGrabber.smali`: Accumulates fractional deltas, scales by display dimensions, handles cursor boundary clamp & rapid invisible pointer reset (`ACTION_UP` -> center `ACTION_DOWN`) | `MouseLookEngine.kt` (lines 23–86): Accumulates directly in normalized `[0f..1f]` space (`dx * 0.0012f * sensitivityX`), lacks screen-pixel aspect ratio awareness, and resets to `(0.5f, 0.5f)` only when callers manually check `needsAutoRecenter()` | **P1 Precision & Recenter Gap**: Aspect ratio distortion on landscape vs. portrait screens, loss of sub-pixel micro-aim precision, and missing automatic rapid UP->recenter->DOWN state machine inside the dispatcher | Upgrade `MouseLookEngine` with a sub-pixel `Double`/`Float` remainder accumulator, display-resolution-aware sensitivity scaling, ADS/Aim modifier multiplier, and an atomic `RecenterTransition` sequence executed by `MultiTouchDispatcher` |
| **Physical Input Capture & Grabbing** | `EventGrabber.smali` + `InputNative.smali`: Captures physical keyboard/mouse events via overlay window & pointer capture | `PlayModeView.kt`: Only captures keys when the user taps an in-app `OutlinedCard` ("Tap Here to Focus Physical Input Capture"); stops working the moment the user switches to an external game | **P0 Architecture Gap**: In-app Compose focus cannot capture physical keyboard/mouse input while a target game is in the foreground | Implement `PlayModeOverlayService` (`Service` + `WindowManager` `TYPE_APPLICATION_OVERLAY`) with a non-blocking floating HUD control pill, toggleable physical key/mouse capture surface (`requestPointerCapture`), and notification emergency stop |
| **Device Hot-Plug & Stuck-Touch Safety** | `VirtualDeviceService.smali`: Monitors device lifecycle and tears down active virtual pointers on disconnect | `DeviceManager.kt` (line 81) calls `HeldInputRegistry.releaseAll()`, which clears `HeldInputRegistry` memory maps, **but never notifies `MultiTouchDispatcher` or `InputBridgeAdapter` to inject `ACTION_UP`/`ACTION_CANCEL`!** | **P0 Stuck Input Hazard**: Unplugging a USB/Bluetooth peripheral clears `HeldInputRegistry` UI state while leaving pointers physically held down inside the active bridge and `MultiTouchDispatcher.activeSlots` | Wire `DeviceManager` unplug events, `PlayModeOverlayService` focus loss, and lifecycle pause directly into `SessionController.emergencyReleaseAll()` -> `MultiTouchDispatcher.releaseAll()` -> bridge `ACTION_CANCEL`/`ACTION_UP` |
| **Entitlement & Security Posture** | `Settings.smali`: Local preference checks (reference had weak client-side gates and broad permissions) | `LocalPreferences.kt` (lines 70–79): `unlockProWithKey(licenseKey)` unlocks Pro for **any** string with `length >= 6`; `MainViewModel.kt` gates profile export and >1 macro behind `isProUnlocked` | **P1 Fake Pro Gate**: Arbitrary 6-character strings unlock Pro; core utility features (exporting JSON backups, creating macros) are artificially blocked | Per user confirmation: Remove the fake `unlockProWithKey` stub and unlock all core features (unlimited macros, profile JSON import/export) locally by default |

---

## 3. Severity-Ranked Audit Findings (`VitualEmu` Codebase)

### P0 — Critical Functional & Safety Blockers
1. **No-Op Touch & Key Injection in Both Bridges**
   - **Location**: `ShizukuBridgeAdapter.kt:94–113`, `RootlessWirelessAdbBridgeAdapter.kt:125–143`
   - **Defect**: `injectTouch()` and `injectKey()` check `_statusFlow.value.state == BridgeHealthState.CONNECTED` and immediately return `true` without performing any binder call, shell command, or `MotionEvent` dispatch.
   - **Fix**: Implement real `MotionEvent` assembly via `MultiTouchPointerCombiner` and dispatch through `IInputManager.injectInputEvent` (via Shizuku binder proxy or local ADB shell daemon) with `/dev/input/event*` `sendevent` fallback.
2. **Missing Multi-Pointer `MotionEvent` Combiner (Broken Simultaneous WASD + Aim + Fire)**
   - **Location**: `MultiTouchDispatcher.kt:28–75`
   - **Defect**: Dispatching pointer 0 (`MOVE`) and pointer 1 (`DOWN`) as separate single-pointer events causes Android's input pipeline to drop or cancel the first touch stream.
   - **Fix**: Create `MultiTouchPointerCombiner` that tracks all active pointer slots (`0..9`), maintains a shared gesture `downTime`, and emits a combined `MotionEvent.obtain(downTime, eventTime, maskedAction, pointerCount, pointerProperties, pointerCoords, ...)` for every state transition (`ACTION_DOWN`, `ACTION_POINTER_DOWN`, `ACTION_MOVE`, `ACTION_POINTER_UP`, `ACTION_UP`, `ACTION_CANCEL`).
3. **Peripheral Unplug Fails to Release Active Bridge Pointers**
   - **Location**: `DeviceManager.kt:78–83` and `HeldInputRegistry.kt:65–72`
   - **Defect**: `DeviceManager.onInputDeviceRemoved` calls `HeldInputRegistry.releaseAll()`, which clears the in-memory `ConcurrentHashMap` for the HUD badge, but **never invokes** `MultiTouchDispatcher.releaseAll()` or `InputBridgeAdapter.releaseHeldInputs()`. Furthermore, `MainViewModel` never connects `MultiTouchDispatcher` to `HeldInputRegistry` during live physical key/mouse events.
   - **Fix**: Unify pointer state tracking between `MultiTouchDispatcher` and `HeldInputRegistry`, and register a hot-plug listener callback so peripheral removal immediately dispatches `ACTION_UP`/`ACTION_CANCEL` across all active pointer slots.
4. **Absence of Real Floating Overlay Service for Cross-App Play Mode**
   - **Location**: `AndroidManifest.xml` (only declares `MainActivity`), `PlayModeView.kt`
   - **Defect**: `SYSTEM_ALERT_WINDOW` and `FOREGROUND_SERVICE_SPECIAL_USE` are declared in `AndroidManifest.xml`, but no `Service` exists. Users cannot see the HUD, toggle input capture, or inject controls while a target game is in the foreground.
   - **Fix**: Implement `PlayModeOverlayService` with a lifecycle-safe `WindowManager` overlay pill (draggable HUD, live FPS/latency telemetry, Capture Toggle, Visual Key Map overlay, and Emergency Stop button) plus a foreground service notification with a direct `STOP SESSION` action intent.

### P1 — High-Priority Runtime & Accuracy Defects
5. **Shizuku Permission Verification Missing**
   - **Location**: `ShizukuBridgeAdapter.kt:45–81`
   - **Defect**: Checking `Shizuku.pingBinder()` only confirms the Shizuku daemon is alive on the device—it does **not** verify whether `com.vitualemu.app` has been granted Shizuku permission (`Shizuku.checkSelfPermission() == PERMISSION_GRANTED`) or request it (`Shizuku.requestPermission(requestCode)`).
   - **Fix**: Add full Shizuku permission state detection (`checkSelfPermission`, `requestPermission`, and binder transaction verification) with clear UI status feedback when Shizuku is running vs. authorized.
6. **Mouse-Look Edge-Recentering & Aspect Ratio Distortion**
   - **Location**: `MouseLookEngine.kt:29–64` and `MainViewModel.kt:492–510`
   - **Defect**: `MainViewModel.onPhysicalMouseMove` checks `mouseLookEngine.needsAutoRecenter()` and calls `mouseLookEngine.recenter()` + `multiTouchDispatcher.dispatchTouch("look", TouchAction.DOWN, ...)` **without** first sending `TouchAction.UP` at the edge coordinate. To the game, the finger teleports instantaneously from `(0.92, 0.50)` to `(0.50, 0.50)` while still held down, snapping the camera backward by 42% of the screen width!
   - **Fix**: When `needsAutoRecenter()` triggers, `MultiTouchDispatcher` must atomically emit: (1) `TouchAction.UP` at the current edge coordinate, (2) reset `MouseLookEngine` to anchor `(anchorX, anchorY)` while preserving sub-pixel remainder deltas, and (3) `TouchAction.DOWN` at the anchor coordinate on the next frame so the camera continues rotating smoothly in the same direction.
7. **WASD Multi-Key Movement Not Synthesized into 8-Way Analog Joystick Vector**
   - **Location**: `MainViewModel.kt:458–485`
   - **Defect**: Pressing `W` and `D` simultaneously triggers two separate `MappingAction.DPadWASD` bindings or overwrites the same pointer slot instead of computing a normalized diagonal vector `(cos(45°), -sin(45°))` from the D-Pad center anchor on `SafetyPolicy.MOVEMENT_POINTER_SLOT` (Slot 0).
   - **Fix**: Implement `WasdJoystickSynthesizer` in the runtime input pipeline that tracks held directional keys (`W`, `A`, `S`, `D` or custom directional bindings), holds pointer Slot 0 at `(centerX, centerY)` on first key down, moves Slot 0 along the normalized resultant vector `(centerX + dx * radius, centerY + dy * radius)` as keys change, and releases Slot 0 cleanly when all directional keys are released.
8. **Permissive Fake Pro License Gate**
   - **Location**: `LocalPreferences.kt:70–79`, `MainViewModel.kt:233–243, 326–331`, `MacroEditorScreen.kt`, `SettingsScreen.kt`
   - **Defect**: Any 6+ character string unlocks Pro, while core features are artificially restricted.
   - **Fix**: Remove the fake license key gate and unlock all features locally by default.

### P2 — Medium-Priority Reliability & UX Issues
9. **Concurrency Race in `MultiTouchDispatcher`**
   - **Location**: `MultiTouchDispatcher.kt:25`
   - **Defect**: `activeSlots = mutableMapOf<String, Int>()` is mutated inside `suspend` functions without a `Mutex` or `ConcurrentHashMap`, risking concurrent modification when macros, mouse-look auto-recenter, and physical keys fire simultaneously.
   - **Fix**: Protect pointer slot allocation and combiner frame emission with a coroutine `Mutex` (`kotlinx.coroutines.sync.Mutex`) and thread-safe state structures.
10. **Macro Loop Cancellation & Pointer Slot Collisions**
    - **Location**: `MacroEngine.kt:23–63`, `ShizukuBridgeAdapter.kt:115–122`
    - **Defect**: `ShizukuBridgeAdapter.executeMacroStep` hardcodes `SafetyPolicy.FIRST_DYNAMIC_POINTER_SLOT` (Slot 2) for all macros, causing concurrent macros or regular key taps on Slot 2 to collide. Also, macro steps with `endNormalizedX != normalizedX` do not interpolate intermediate `MOVE` steps for swipe macros.
    - **Fix**: Route macro step execution through `MultiTouchDispatcher` with dynamic slot allocation and multi-step linear swipe interpolation (`DOWN` -> interpolated `MOVE` frames -> `UP`), ensuring immediate `ACTION_CANCEL` on macro cancellation.

---

## 4. Visual Direction & Design System
- **Aesthetic Persona**: **Tactical High-Precision Gaming Utility** (inspired by precision hardware companion suites like ROG Armoury Crate / Razer Synapse mobile HUDs, refined with clean Material 3 dark/dynamic surfaces).
- **Color & Status Semantics**:
  - **Bridge Connected / Ready**: Emerald Tactical Green (`#00E676` / `MaterialTheme.colorScheme.tertiary`)
  - **Warning / Permission Required**: Amber Alert (`#FFB300`)
  - **Emergency Stop / Disconnected / Conflict**: Crimson Alert (`#FF5252` / `MaterialTheme.colorScheme.error`)
- **Overlay HUD Ergonomics**:
  - Compact, semi-translucent floating control bar (`TYPE_APPLICATION_OVERLAY` when granted) displaying active profile, live pointer count, latency (`ms`), capture mode toggle (Mouse/Key Grabbed vs. Pass-Through), key-reticle visibility toggle, and 1-tap **PANIC RELEASE** button.

---

## 5. Target Technical Architecture

### 5.1 Multi-Touch Pointer Combiner & Injection Pipeline
- **File**: `app/src/main/java/com/vitualemu/app/runtime/input/MultiTouchPointerCombiner.kt`
- **Responsibilities**:
  - Tracks up to 10 active pointer slots (`0..9`) with normalized `(normX, normY)` and display pixel coordinates `(pixelX, pixelY)` scaled dynamically to current display metrics (`DisplayMetrics` / `WindowMetrics`).
  - Synthesizes accurate Android `MotionEvent` objects:
    - First active pointer -> `MotionEvent.ACTION_DOWN` (sets gesture `downTime = SystemClock.uptimeMillis()`).
    - Subsequent concurrent pointers -> `MotionEvent.ACTION_POINTER_DOWN or (pointerIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT)` with all active pointers packed into `PointerProperties[]` and `PointerCoords[]`.
    - Pointer movement -> `MotionEvent.ACTION_MOVE` containing all currently active pointers.
    - Releasing one pointer while others remain held -> `MotionEvent.ACTION_POINTER_UP or (pointerIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT)`.
    - Releasing the final pointer -> `MotionEvent.ACTION_UP`.
    - Emergency release -> `MotionEvent.ACTION_CANCEL`.
  - Also generates Linux evdev Type B command sequences (`ABS_MT_SLOT`, `ABS_MT_TRACKING_ID`, `ABS_MT_POSITION_X`, `ABS_MT_POSITION_Y`, `SYN_REPORT`) for the `sendevent` fallback path.

### 5.2 Privileged Bridge Adapters (`ShizukuBridgeAdapter` & `RootlessWirelessAdbBridgeAdapter`)
- **`ShizukuBridgeAdapter.kt`**:
  - Checks binder liveness (`Shizuku.pingBinder()`) AND authorization (`Shizuku.checkSelfPermission()`), exposing `requestPermission()` helper.
  - Injects combined `MotionEvent` and `KeyEvent` instances via privileged `IInputManager.Stub.asInterface(ShizukuBinderWrapper(SystemServiceHelper.getSystemService("input")))` reflection, falling back to `Shizuku.newProcess(arrayOf("sh"), null, null)` running a persistent low-latency input loop (`sendevent` / `input`) if direct binder wrapping is restricted.
- **`RootlessWirelessAdbBridgeAdapter.kt`**:
  - Implements `LocalAdbShellTransport` for localhost Wireless Debugging (`127.0.0.1:<port>`):
    - Supports ephemeral in-memory pairing validation + ADB protocol `CNXN` / `OPEN("shell:")` handshake using ephemeral in-memory RSA keypair (`KeyPairGenerator.getInstance("RSA")`).
    - Maintains a persistent shell stream to execute multi-touch `sendevent` Type B batches or `input` commands with sub-10ms framing.
    - Immediately zeroes pairing codes in memory (`CharArray.fill('\u0000')`) and never writes credentials to disk.

### 5.3 Mouse-Look & WASD Analog Synthesizer
- **`MouseLookEngine.kt`**:
  - Adds display dimension awareness (`screenWidthPx`, `screenHeightPx`), sub-pixel remainder accumulation, and an ADS/Aim sensitivity multiplier (`setAimModifierActive(Boolean)`).
  - Eliminates camera snap during auto-recentering by returning a structured `MouseLookDeltaResult(normX, normY, requiresEdgeRecenter, anchorX, anchorY)`.
- **`WasdJoystickSynthesizer.kt`**:
  - Dedicated state machine for `MappingAction.DPadWASD` on `SafetyPolicy.MOVEMENT_POINTER_SLOT` (Slot 0).
  - Computes normalized 8-way vector from held keys (`W`, `A`, `S`, `D`) and smoothly transitions Slot 0 without releasing the virtual thumbstick until all movement keys are released.

### 5.4 System Overlay Service (`PlayModeOverlayService`)
- **File**: `app/src/main/java/com/vitualemu/app/runtime/overlay/PlayModeOverlayService.kt`
- **Responsibilities**:
  - Declared in `AndroidManifest.xml` with `android:foregroundServiceType="specialUse"` and property `android.app.property.SPECIAL_USE_FGS_SUBTYPE`.
  - Posts a persistent notification with an immediate **Emergency Stop** `PendingIntent`.
  - When `Settings.canDrawOverlays(context)` is granted, attaches a draggable floating HUD + key reticle overlay via `WindowManager` (`WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`), allowing the user to toggle input capture (`FLAG_NOT_FOCUSABLE` off when capturing physical keys/mouse, on when passing touch through to the game) and trigger emergency release at any time.

---

## 6. Ordered Implementation Steps

1. **Phase 1: Core Multi-Touch Combiner, WASD Synthesizer & Mouse-Look Fix**
   - Create `MultiTouchPointerCombiner.kt` to build valid multi-pointer `MotionEvent` instances (`ACTION_DOWN`, `ACTION_POINTER_DOWN`, `ACTION_MOVE`, `ACTION_POINTER_UP`, `ACTION_UP`, `ACTION_CANCEL`) and Linux `sendevent` Type B frames.
   - Create `WasdJoystickSynthesizer.kt` for smooth 8-way analog joystick vector synthesis on Slot 0.
   - Upgrade `MouseLookEngine.kt` with sub-pixel remainder accumulation, aspect-ratio compensation, and atomic edge-recenter transition support (`UP` -> reset to anchor -> `DOWN`).
   - Update `MultiTouchDispatcher.kt` to synchronize with `HeldInputRegistry`, use a coroutine `Mutex`, and execute atomic recenter transitions and swipe interpolations.

2. **Phase 2: Real Shizuku & Rootless Wireless ADB Bridge Implementations**
   - Upgrade `ShizukuBridgeAdapter.kt` with real Shizuku permission checks (`checkSelfPermission`, `requestPermission`), `IInputManager` binder wrapper injection, and persistent shell `sendevent` fallback.
   - Upgrade `RootlessWirelessAdbBridgeAdapter.kt` with `LocalAdbShellTransport` (ADB `CNXN`/`AUTH`/`OPEN` framing with ephemeral RSA keys and persistent shell stream) plus strict zero-retention pairing code handling.

3. **Phase 3: System Floating Overlay Service & Unplug Safety Hardening**
   - Create `PlayModeOverlayService.kt` with foreground notification, emergency stop broadcast action, and `WindowManager` floating control pill + visual key mapping overlay.
   - Register `PlayModeOverlayService` in `AndroidManifest.xml`.
   - Connect `DeviceManager.onInputDeviceRemoved` to an active emergency-release callback in `MainViewModel` / `MultiTouchDispatcher` so unplugging any keyboard or mouse immediately dispatches `ACTION_CANCEL`/`ACTION_UP` to the active bridge.

4. **Phase 4: Entitlement Cleanup & UI Polish**
   - Remove the permissive `unlockProWithKey` stub from `LocalPreferences.kt`, `MainViewModel.kt`, `SettingsScreen.kt`, and `MacroEditorScreen.kt`, making all macro and profile import/export capabilities available out of the box.
   - Enhance `ActivationScreen.kt` and `PlayModeView.kt` to trigger Shizuku permission requests, control `PlayModeOverlayService`, and display real-time multi-pointer combiner diagnostics.

5. **Phase 5: Verification & Automated Unit / Robolectric Test Suite**
   - Expand unit and Robolectric tests in `app/src/test/java/com/vitualemu/app/` covering:
     - `MultiTouchPointerCombinerTest`: 2+ concurrent pointers (`ACTION_POINTER_DOWN`, `ACTION_MOVE`, `ACTION_POINTER_UP`, `ACTION_CANCEL`) and `sendevent` Type B frame generation.
     - `MouseLookEngineTest`: Sub-pixel accumulation, boundary clamping, and atomic edge-recenter sequence without camera snap-back.
     - `WasdJoystickSynthesizerTest`: 8-way vector math (`W+D` diagonal normalization) and clean Slot 0 release.
     - `HeldInputRegistryAndUnplugSafetyTest`: Verification that peripheral unplug and emergency stop neutralize both `HeldInputRegistry` and `MultiTouchDispatcher` active slots.
   - Run `compile_applet` and `gradle :app:testDebugUnitTest` to verify zero build or test errors.
