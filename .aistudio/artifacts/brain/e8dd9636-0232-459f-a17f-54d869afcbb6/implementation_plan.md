# VitualEmu — Complete Product Correction & Gaming Input-Mapping Implementation Plan

> **Role & Objective:** Transform VitualEmu into a genuine, production-grade Android keyboard and mouse mapping utility for compatible mobile games (GGMOUSE PRO category), pivoting away from an isolated feasibility dashboard into the actual core gaming workflow.  
> **Status:** Plan Mode Only. All source code changes are staged for review and explicit approval.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Category Realignment:** VitualEmu is not a benchmark dashboard or emulator. It is a **tactical gaming input-mapping utility**. The user experience must center on:
> 1. Adding/selecting a game.
> 2. Opening the canvas editor to place virtual controls (WASD, Action Buttons, Aim Region).
> 3. Capturing real physical keyboard/mouse inputs via interactive event listeners.
> 4. Saving profiles to Room SQLite with versioned JSON backup and snapshots.
> 5. Executing preflight and starting Play Mode with a compact floating HUD.
> 6. Safely neutralizing all held inputs on stop, focus loss, or device disconnect.

- **Confirmed Decision 1 (Identity & Clean Namespace):** All source files, tests, and Gradle configurations are unified under `com.vitualemu.app` with the official app name `VitualEmu`.
- **Confirmed Decision 2 (Eradication of Simulated States):** Remove all hardcoded "DETECTED" peripheral fallbacks, fake 20-trial loops, and synthetic latency metrics. State must always be honest: `NOT_TESTED`, `RUNNING`, `PASS`, `FAIL`, or `BLOCKED`.
- **Confirmed Decision 3 (Physical Key Capture):** The editor must feature an interactive "Press any key on physical keyboard or click mouse button" capture modal listening to real Android `KeyEvent`s and `MotionEvent`s rather than hardcoded string dropdowns.
- **Open Decision (FPS Mouse-Look Fallback):** While global pointer capture is restricted when a 3rd-party game owns foreground focus, VitualEmu implements an active transparent overlay touch-region aim drag delta engine as the primary verified fallback.

```
+-----------------------------------------------------------------------------------------+
|                              VITUALEMU GAMING USER JOURNEY                              |
|                                                                                         |
|  [Games Library] ──> Select Game ──> [Profiles Hub] ──> Create/Open Control Profile    |
|                                                                    │                    |
|                                                                    ▼                    |
|  [Mapping Editor Screen (Canvas-First)] <──────────────────────────┘                    |
|    - 16:9 Landscape/Portrait Game Canvas                                                |
|    - Drag & Drop Virtual Controls (WASD, Tap, Hold, Aim Look, Swipe)                    |
|    - Interactive Physical Key Capture Dialog (Press real key to bind)                   |
|    - Normalized Coordinates (X, Y, W, H) & Snap-to-Grid                                 |
|    - Conflict Detection & Warnings (e.g. Duplicate Key Assignments)                     |
|    - Room Persistence + Autosave + Rollback Snapshots                                   |
|                                     │                                                   |
|                                     ▼                                                   |
|  [Play Mode Preflight Engine] ──> Validates Bridge, Permissions, Devices & Profile      |
|                                     │                                                   |
|                                     ▼                                                   |
|  [Active Play Mode & Floating Pill Overlay] ──> Game Foregrounded                       |
|    - Real-time Input Translation (Keys -> Touch Injections)                            |
|    - Emergency Safe Stop (Instant Zero-Stuck-Input Neutralization)                      |
+-----------------------------------------------------------------------------------------+
```

---

## 1. Repository Audit: Reusable Assets vs. Incomplete / Simulated Code

### What Exists & Is Highly Reusable:
- **Local Room Database (`data/local/room/`):** Full relational schema (`games`, `profiles`, `mappings`, `macros`, `macro_steps`, `profile_snapshots`) with Room DAOs and database pre-population logic.
- **Data Repository (`data/repository/VitualEmuRepository.kt`):** Flow-based reactive queries, atomic JSON serialization, SHA-256 snapshot checksums, rollback, and duplicate profile handling.
- **Domain Models & Validation (`domain/`):** Clean domain entities, normalized coordinate clamping `[0.0, 1.0]`, duplicate key conflict detection, and preflight rule engine.
- **Central Held-Input Registry (`HeldInputRegistry.kt`):** Thread-safe tracking of depressed keycodes and active virtual pointers with guaranteed atomic release on stop or focus loss.
- **Visual Design System (`ui/theme/`):** Established tokens: Midnight Blue (`#111827`), Slate (`#1E293B`), Electric Blue (`#38BDF8`), Status Emerald (`#10B981`), Amber (`#F59E0B`), and Crimson (`#EF4444`).
- **Clean Namespace:** Consolidated under `com.vitualemu.app` with passing unit and Robolectric tests.

### What Is Incomplete, Misleading, or Simulated (To Be Removed / Replaced):
- **Feasibility Dashboard as Main Screen:** The app currently boots into a testing dashboard rather than the gaming input-mapper home screen.
- **Simulated Hardware Fallbacks in `DeviceManager`:** Hardcoded fallback strings ("Standard USB-C Mechanical Keyboard", "Optical High-DPI Gaming Mouse") when no physical device is attached. Must report actual device presence truthfully.
- **Fake 20-Trial Loops in `FeasibilityTestRunner`:** Synthetic delays used to generate fake p50/p95 latency metrics. Must only show genuine measurements when physical actions are dispatched.
- **Lack of Physical Key Capture in Editor:** The mapping editor previously relied on typing key names into a text field instead of listening for real hardware `KeyEvent` triggers.

---

## 2. Target User Journey & Core Product Behavior

VitualEmu's interface will be re-anchored around the genuine gaming utility workflow:

1. **Home (Dashboard & Quick Launch):**
   - Active Game Banner: Quick-start button launching the last played game with its active mapping profile.
   - Peripherals & Bridge Status Card: Live pill indicators for physical keyboard, mouse, and bridge readiness.
   - Preflight Summary: Displays whether controls are ready to inject, partially supported, or blocked.
   - Emergency Safe-Stop: High-visibility global escape action that kills all active virtual pointers instantly.
2. **Games Library:**
   - Visual catalog of installed/configured Android games.
   - Add game manually via package identifier (preserving privacy and avoiding restricted broad inventory scanning).
   - Card showing associated profiles count and last played timestamp.
3. **Profiles Hub:**
   - Multiple named profiles per game (e.g., "Competitive Sniping", "Vehicles / Flying", "Casual WASD").
   - Favorites, duplication ("Save Copy"), versioned JSON import/export, and snapshot rollback history.
4. **Mapping Editor (The Core Canvas):**
   - 16:9 aspect ratio game preview canvas.
   - Visual markers: WASD Movement Joystick, Aim Look Region, Action Buttons (Tap/Hold/Swipe).
   - Interactive Key-Capture Modal: User taps a control marker, taps "Bind Key", and presses a key on their physical keyboard or clicks a mouse button. The key code is captured and assigned immediately.
   - Real-time Normalized Coordinate Inspector `(X, Y, W, H)` with numeric fine-tuning nudges (`±0.01`).
   - Conflict Warning System: Highlights colliding duplicate bindings in amber with configurable policy.
5. **Activation Wizard (Dedicated Flow):**
   - Step-by-step guidance for Shizuku (recommended) and Rootless Wireless Debugging (ADB pairing).
   - Truthful state detection: `NOT_CONNECTED`, `CONNECTING`, `AUTHORIZED`, `PERMISSION_REVOKED`.
6. **Play Mode Floating Overlay:**
   - Compact floating pill that stays on top of the foreground game.
   - Quick pause/resume, profile switcher, and emergency stop button.
   - Automatic neutralization of all held inputs if the user switches apps or unplugs a peripheral.

---

## 3. Architecture & Package Structure

```
com.vitualemu.app/
├── MainActivity.kt                 // Main Compose Activity, WindowInsets & focus listeners
├── domain/
│   ├── model/                     // Game, Profile, Mapping, Macro, BridgeStatus, DeviceInfo
│   ├── validation/                // Coordinate clamping, conflict detection, macro bounds
│   └── usecase/                   // PreflightCheckUseCase, HeldInputRegistry, KeyBindingUseCase
├── data/
│   ├── local/room/                // VitualEmuDatabase, Entities (Game, Profile, Mapping, Macro), DAOs
│   ├── repository/                // VitualEmuRepository (Room + atomic JSON + snapshots)
│   └── settings/                  // DataStore preferences (theme, bridge mode, overlay settings)
├── runtime/
│   ├── bridge/                    // InputBridgeAdapter (ShizukuBridge & RootlessAdbBridge)
│   ├── session/                   // SessionManager (PlayMode state machine, safe stop)
│   └── input/                     // PhysicalInputReceiver (KeyEvent & MotionEvent normalizer)
├── platform/
│   ├── devices/                   // Real InputManager listener (zero fake fallbacks)
│   └── overlay/                   // WindowManager floating pill service & permissions
└── ui/
    ├── navigation/                // 4 Bottom Tabs (Home, Games, Profiles, Settings) + Dedicated Screens
    ├── screens/
    │   ├── home/                  // HomeScreen (Gaming dashboard, quick play, bridge status)
    │   ├── games/                 // GamesScreen (Manual package addition, game library)
    │   ├── profiles/              // ProfilesScreen (Profile CRUD, JSON import/export, snapshots)
    │   ├── settings/              // SettingsScreen (Bridge setup, real devices, theme, privacy)
    │   ├── editor/                // MappingEditorScreen (Canvas, drag/drop, real key capture)
    │   ├── activation/            // ActivationWizardScreen (Pairing & authorization flow)
    │   └── testmode/              // TestModeScreen (Sandbox touch injection & safe release verification)
    ├── components/                // Status badges, gaming buttons, key cap chips
    └── theme/                     // VitualEmuTheme (Midnight Blue, Slate, Electric Blue)
```

---

## 4. Key Implementation Steps & Corrective Changes

### Stage 1: Removal of Simulated Code & Real Hardware Detection
- Update `DeviceManager.kt`: Query Android's `InputDevice.getDeviceIds()` directly; remove hardcoded dummy keyboard/mouse entries. If no physical keyboard is connected, honestly display "No Physical Keyboard Detected".
- Eradicate hardcoded latency and synthetic 20-trial loops from testing modules.
- Ensure all capability flags reflect device reality rather than static true values.

### Stage 2: Production Navigation & Gaming UI Overhaul
- Implement the 4-tab bottom navigation shell in `MainActivity.kt`:
  - Tab 1: **Home** (Tactical gaming dashboard, active game/profile, preflight check, emergency stop).
  - Tab 2: **Games** (Game cards, package identity management, add game modal).
  - Tab 3: **Profiles** (Per-game mappings, favorites, backup, JSON import/export, snapshots).
  - Tab 4: **Settings** (Bridge configuration, real device monitor, local diagnostics, privacy).
- Dedicated full-screen destinations:
  - **Mapping Editor**: Loaded from Home or Profiles with the active profile.
  - **Activation Wizard**: Contextual Shizuku and Wireless Debugging setup.
  - **Dedicated Test Mode**: Isolated sandbox to test mapping responsiveness.

### Stage 3: Canvas-First Mapping Editor with Real Physical Key Capture
- Visual 16:9 canvas with draggable markers for WASD, Aim Region, Tap, Hold, and Swipe buttons.
- Real Physical Key Capture Dialog:
  - When editing a control, tapping "Bind Physical Input" opens a modal listening to `Modifier.onKeyEvent`.
  - Pressing any key on the connected USB/Bluetooth keyboard (e.g. `Space`, `Shift`, `C`, `R`, `1`, `2`) immediately captures the `KeyEvent.keyCode` and human-readable key label (`KEYCODE_SPACE` -> `"Space"`).
  - Mouse button clicks (Left, Right, Middle, Back, Forward) are captured via pointer input and assigned.
- Coordinate Inspector: Real-time numeric `(X, Y, W, H)` normalization in range `[0.0, 1.0]` with `±0.01` fine-tuning nudges.
- Snap-to-Grid toggle (5% grid lines).
- Duplicate assignment conflict warnings in amber with resolution rules.
- Direct save to Room with automatic recovery snapshot.

### Stage 4: Play Mode & Runtime Safety Engine
- Session State Machine: `STOPPED` -> `PREFLIGHT` -> `READY`/`BLOCKED` -> `ACTIVE` -> `PAUSED_RESET`/`SAFE_STOP`.
- Floating Play Mode Pill: Compact status widget staying above the screen with quick pause and emergency safe-stop.
- `HeldInputRegistry`: Centralized registry ensuring that if focus is lost or a peripheral is unplugged, every held virtual pointer is cleared with zero orphaned touches.

---

## 5. Phased Verification & Quality Gates

| Phase | Milestone Deliverables | Acceptance Criteria |
| :--- | :--- | :--- |
| **Phase 1: Cleanup & Devices** | Remove dummy mocks, bind real `InputManager` events. | Real keyboards/mice detected; zero hardcoded fallback peripherals. |
| **Phase 2: Gaming Shell & Library** | 4-tab navigation, Game addition, Room profile persistence. | Game library persists; profiles survive process death and app restart. |
| **Phase 3: Interactive Mapping Editor** | 16:9 canvas, drag-and-drop, physical key capture modal. | Pressing physical keyboard key binds immediately; coordinates clamp cleanly. |
| **Phase 4: Preflight & Safety Engine** | State machine, preflight validation, emergency safe stop. | Unplugging keyboard or switching apps neutralizes all inputs in $\le 5\text{ms}$. |
| **Phase 5: Automated Testing** | Robolectric and unit test suite for key capture & safety. | All unit tests pass; `compile_applet` succeeds cleanly. |

---

## 6. Actionable Next Step

Upon your approval of this plan, we will execute **Phase 1 and Phase 2**:
1. Cleanse all mock/dummy hardware fallbacks in `DeviceManager` and establish real Android `InputDevice` tracking.
2. Implement the primary 4-tab gaming navigation shell (`HomeScreen`, `GamesScreen`, `ProfilesScreen`, `SettingsScreen`) backed by the existing Room database.
3. Build the canvas-first `MappingEditorScreen` with real hardware key-capture listeners.

*Awaiting your approval to proceed.*
