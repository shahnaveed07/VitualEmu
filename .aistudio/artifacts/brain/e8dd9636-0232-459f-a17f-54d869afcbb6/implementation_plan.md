# VitualEmu — Master Engineering Implementation Plan

> **Planning Baseline Reference:** VitualEmu SRS, DRD, ERD, and System Architecture Baseline v1.0 (16 October 2026).  
> **Status:** Plan Mode Only. All source code and build modification is blocked pending user review and Gate 0 feasibility sign-off.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Implementation Gate Warning:** In accordance with Baseline Section 7 and Appendix C, **Gate 0 is mandatory**. Touch injection and global mouse-look across arbitrary foreground third-party games on Android 15 (Redmi Note 13 5G / HyperOS) are not standard OS APIs. Full MVP mapping development is blocked until a reproducible proof-of-concept proves safe touch injection and clean input release.

- **Confirmed Decision 1 (Baseline Invariant):** Local-first architecture. Core key-mapping, bounded macros, single-profile JSON import/export, local backup/rollback, and safety mechanisms are 100% free and require no account registration.
- **Confirmed Decision 2 (Architecture Style):** Single Android Gradle module (`:app`) with strict logical package boundaries (Domain, Data, Runtime, Platform, UI, Cloud) preserving testability without multi-module overhead.
- **Confirmed Decision 3 (Cloud Boundaries):** Firebase Auth + Firestore + Cloud Functions scope is strictly isolated to user account identity, Pro entitlement verification, purchase tokens, and account deletion audits. No game profiles, macros, or touch coordinates leave the client device in the initial release.
- **Open Decision OD-01 (Pro Commercial Model):** Recommendation: One-time lifetime in-app purchase (IAP) SKU (`pro_lifetime_unlock`) with server-side Google Play Developer API verification, rather than recurring subscriptions, to align with utility tooling expectations.
- **Open Decision OD-02 & OD-03 (Bridge & Mouse-Look):** Dual-candidate evaluation in Phase 0 between Rootless Wireless Debugging (`adb` pairing) and Shizuku (`binder` shell). If pointer capture is restricted when a third-party game owns focus, fall back to a validated floating touch-region delta simulator.

```
+-----------------------------------------------------------------------------------------+
|                               VITUALEMU RUNTIME PIPELINE                                |
|                                                                                         |
|  [Physical Kbd/Mouse] ---> [Input Normalization & Central Held Registry]                |
|                                       │                                                 |
|                                       ▼                                                 |
|                        [Preflight & Session State Machine]                              |
|                                       │                                                 |
|                  ┌────────────────────┴────────────────────┐                            |
|                  ▼                                         ▼                            |
|      [Active Play Mode Session]                [Safety / Fail-Stop Engine]              |
|        - WASD / Joystick                         - Auto-Cancel Macros                   |
|        - Action Buttons (Tap/Hold/Swipe)         - Release Virtual Held Touches         |
|        - FPS Relative/Region Look                - Stale State Invalidation             |
|                  │                                         │                            |
|                  ▼                                         ▼                            |
|        [Candidate Bridge Port]                  [Target Game Remains Open]              |
|        (Wireless Debugging / Shizuku)            (No game crashes / safe prompt)        |
+-----------------------------------------------------------------------------------------+
```

---

## 1. Executive Summary & Critical Technical Risks

VitualEmu is a high-performance, local-first Android utility designed to translate physical keyboard and mouse inputs into precise virtual touch actions for compatible mobile titles. The system is architected as an input translation bridge, strictly adhering to platform security and policy constraints: it contains no game engine emulation, executes no process memory patching, and rejects botting or evasion mechanics.

### Primary Technical Risks & Defense Mechanisms

1. **Risk R-01 (Critical — Input Injection Barrier):** Android security boundaries restrict background applications from dispatching arbitrary `MotionEvent`s into other foreground windows.
   - *Mitigation:* Gate 0 independent feasibility spike evaluating Rootless Wireless Debugging (local TCP ADB) vs. Shizuku privileged binder access. If neither provides stable foreground injection and reliable touch release, the product roadmap halts before building the editor.
2. **Risk R-02 (High — Cross-Window Mouse-Look & Pointer Capture):** Android `View.requestPointerCapture()` delivers relative mouse deltas exclusively to the focused window. When a third-party game acquires foreground focus, the overlay loses pointer capture.
   - *Mitigation:* Validate whether the bridge can intercept physical input events globally, or implement an active transparent input-sink overlay window (`TYPE_APPLICATION_OVERLAY`) that translates relative deltas into continuous touch-swipe drag vectors.
3. **Risk R-05 (High — Stuck Input on Disconnect / App Switch):** Disconnecting a peripheral or switching apps during a held key (e.g., holding 'W' to sprint) leaves an orphaned touch-down state, freezing player movement.
   - *Mitigation:* Centralized Held-Input Registry with monotonic sequence IDs and heartbeat checks. On any focus loss, bridge timeout, or USB detachment, the runtime fires an immediate `releaseAll()` sweep and drops into `SAFE_STOP`.
4. **Risk R-07 / R-08 (High — Billing Fraud & Account Deletion Policy):** Client-side license verification is vulnerable to tampering, and Google Play enforces strict account/data deletion guidelines.
   - *Mitigation:* Backend-authoritative Cloud Functions verifying purchase tokens via Google Play Developer API with token fingerprint hashing. Room databases remain local and independent of cloud account deletion.

---

## 2. Current Repository Audit

A comprehensive inspection of the workspace was performed:

| Repository File / Component | Current State | Audit Assessment & Action Required |
| :--- | :--- | :--- |
| `metadata.json` | Name/Desc empty; `majorCapabilities: ["MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API"]` | Update name to `"VitualEmu"` and provide proper description while strictly preserving required platform capabilities. |
| `settings.gradle.kts` | `rootProject.name = "My Application"` | Must be updated to `"VitualEmu"` upon implementation approval. Repositories include Google, MavenCentral, Gradle Plugin Portal. |
| `app/build.gradle.kts` | `applicationId = "com.example"`, `minSdk = 24`, `targetSdk = 36`, `compileSdk = 36` | Must set unique `applicationId = "com.aistudio.vitualemu.kmpseq"`; minSdk must be set to 30 (Android 11) per baseline FR-005. |
| Signing Configs | `release` and `debugConfig` configured | **Do not touch.** Must preserve existing debug and release signing configurations as mandated by the container environment. |
| Dependencies Catalog (`libs.versions.toml`) | Room 2.7.0, Compose BOM 2024.09.00, Firebase BOM 34.17.0, Retrofit 2.12.0, Moshi 1.15.2, Robolectric 4.16.1 present | Pre-configured with modern libraries. Navigation Compose, Firestore, and Credentials can be activated by uncommenting as needed. |
| Existing Source Code | Default Compose template with `MainActivity.kt` and `Greeting` | Clean skeleton. No existing legacy business logic to preserve, but namespace `com.example` must be retained while structuring internal packages. |
| Unit Test Directory | `ExampleUnitTest.kt` and `ExampleRobolectricTest.kt` | Template tests present. Ready for domain and Robolectric integration test suites. |

---

## 3. Baseline Compliance Report (Locked Decisions & FR/NFR Traceability)

The system design directly conforms to all 38 locked decisions and functional requirements FR-001 through FR-039:

- **Decisions 1–3, FR-001–FR-005 (Navigation & Game Library):** 4 bottom-navigation tabs (Home, Games, Profiles, Settings) with dedicated, full-screen Activation Wizard and Mapping Editor. Games are added manually by package identity; profile associations survive uninstalls.
- **Decisions 6, 20, FR-007–FR-014 (Editor & Canvas):** Canvas-first editor utilizing normalized `(x, y, w, h)` coordinates `[0.0, 1.0]`, snap-to-grid, boundary feedback, numeric micro-adjustments, conflict warnings with configurable resolution policies, debounced autosave, and local snapshot rollback.
- **Decisions 8, 33, FR-015, FR-028 (Local Persistence & Safety):** Local-first Room database; versioned single-profile JSON import/export (free) and batch ZIP import/export (Pro). Safe ZIP parser preventing path traversal (`..` and absolute paths).
- **Decisions 16, 22, 23, 37, FR-018–FR-027 (Bridge, Preflight, & Lifecycle):** Dual-candidate bridge abstraction; preflight validation before Play Mode; explicit state machine (`STOPPED`, `PREFLIGHT`, `BLOCKED`, `READY`, `ACTIVE`, `PAUSED/RESET`, `SAFE_STOP`); hot-plug peripheral handling with neutral-state reset.
- **Decisions 24–31, FR-029–FR-035 (Monetization & Cloud Entitlement):** Free tier includes all gameplay, mapping, macros, single import/export, and diagnostics. Pro unlocks advanced organization (tags, folders, comparison) and batch operations. Entitlement is backend-authoritative via Firebase Cloud Functions and Firestore. In-app and external account deletion.
- **NFR-001–NFR-012 (Performance, Security, Privacy):** Cold launch target $\le 2.5\text{s}$, UI interaction p95 $\le 100\text{ms}$, autosave status $\le 1\text{s}$. Zero telemetry/analytics by default; redacted diagnostics.

---

## 4. Critical Feasibility Plan: Gate 0 (Rootless ADB & Shizuku)

Gate 0 is the prerequisite for all subsequent phases. It determines whether Android 15 on the Xiaomi Redmi Note 13 5G allows non-root touch injection and mouse capture.

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
|                                GATE 0 FEASIBILITY MATRIX                               |
├────────────────────────────┬─────────────────────────────┬─────────────────────────────┤
| Parameter                  | Candidate A: Rootless ADB   | Candidate B: Shizuku        |
├────────────────────────────┼─────────────────────────────┼─────────────────────────────┤
| Mechanism                  | Local TCP ADB Pairing       | Privileged Binder IPC       |
| Setup UX                   | Dev Options -> Pair Code    | One-time Shizuku app grant  |
| Reboot Persistence         | Lost on reboot / IP change  | Lost on reboot              |
| Touch Injection Primitive  | `input tap x y`, `input mot`| `InputManager.injectInput`  |
| Latency Overhead           | Process spawn (~15-40ms)    | Direct Binder (~2-5ms)      |
| Multi-touch Support        | Complex via `sendevent`     | High via synthetic Motion   |
| Safe Stop Reliability      | Kills shell process         | Resets active pointer map   |
└────────────────────────────┴─────────────────────────────┴─────────────────────────────┤
```

### Reproducible Test Matrix & Stop Conditions

1. **Device Identification Logging:** Record Xiaomi HyperOS/MIUI OS build version, Android security patch level, USB-C hub vendor ID/product ID, and input device IDs (`InputDevice.getDeviceIds()`).
2. **Experiment 1 (Touch Injection):** Inject single tap, long press (800ms), and diagonal drag across a canvas view belonging to a separate foreground application.
   - *Measurement:* Success rate over 100 iterations, average injection latency, and jitter.
3. **Experiment 2 (Virtual Joystick / Multi-Touch):** Simultaneously inject WASD directional vector touch while triggering a separate action button tap.
   - *Failure Criteria:* Dropped pointers, ghost touches, or pointer index desynchronization.
4. **Experiment 3 (Mouse-Look Capture):** Test relative pointer movement translation while another window is in the foreground. Test touch-region aim fallback (continuous small delta swipes).
5. **Experiment 4 (Safe Stop & Disconnect Stress Test):** Trigger an active WASD movement touch, then immediately sever the bridge connection or unplug the keyboard.
   - *Pass Requirement:* Input state must be neutralized immediately without leaving an active touch on the display.
6. **Gate 0 Decision Rule:**
   - If Candidate B passes with $\le 16\text{ms}$ latency and reliable safe-stop, select Shizuku as the primary bridge.
   - If Candidate A passes and Candidate B fails, evaluate ADB command pipelining.
   - **Stop Condition:** If neither bridge can inject multi-touch into a foreground game or cancel held touches safely, implementation halts and scope must be revised.

---

## 5. Single-Module Android Architecture & Package Map

VitualEmu follows Clean Architecture principles within a single Gradle module (`:app`), utilizing strictly defined logical package boundaries to prevent tight coupling.

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
|                                   PRESENTATION LAYER                                    |
|   ui.navigation  │  ui.home  │  ui.games  │  ui.profiles  │  ui.settings  │  ui.editor  |
|   ui.testmode    │  ui.playmode (Floating Overlay)        │  ui.activation (Wizard)     |
└───────────────────────────────────────────┬─────────────────────────────────────────────┘
                                            │ calls Use Cases / observes StateFlow
┌───────────────────────────────────────────▼─────────────────────────────────────────────┐
|                                      DOMAIN LAYER                                       |
|   domain.model       - Pure models (Game, Profile, LayoutVariant, Mapping, Macro)       |
|   domain.usecase     - ValidateProfileUseCase, ExecutePreflightUseCase, ImportProfile   |
|   domain.validation  - Coordinate normalization, key conflict resolver, macro bounds   |
|   domain.policy      - Pro entitlement policies, conflict resolution rules              |
└──────────────────────┬────────────────────────────────────────────┬─────────────────────┘
                       │                                            │
┌──────────────────────▼─────────────────────┐  ┌───────────────────▼─────────────────────┐
|                DATA LAYER                  |  |             RUNTIME LAYER               |
|  data.local.room    - Entities, DAOs, DB   |  |  runtime.input     - KeyEvent/Motion    |
|  data.repository    - Profile/Game Repos   |  |  runtime.mapping   - Normalized coords  |
|  data.files         - Atomic JSON/ZIP IO   |  |  runtime.macro     - Bounded engine     |
|  data.snapshot      - Rollback & recovery  |  |  runtime.session   - State machine      |
|  data.settings      - DataStore prefs      |  |  runtime.registry  - Central held keys  |
└────────────────────────────────────────────┘  └───────────────────┬─────────────────────┘
                                                                    │
┌────────────────────────────────────────────┐  ┌───────────────────▼─────────────────────┐
|                CLOUD LAYER                 |  |               PLATFORM LAYER            |
|  cloud.auth        - Firebase Auth & Google|  |  platform.activation.rootless (ADB)    |
|  cloud.purchase    - Play Billing Client   |  |  platform.activation.shizuku (Binder)   |
|  cloud.entitlement - Cloud Functions API   |  |  platform.overlay    - WindowManager    |
|  cloud.deletion    - Account cleanup flow  |  |  platform.devices    - InputDevice Mgr  |
└────────────────────────────────────────────┘  └─────────────────────────────────────────┘
```

### Dependency Rules

- **Zero Reverse Dependencies:** `domain` has no dependencies on Android UI (`androidx.compose`), Room, or Firebase SDK.
- **Repository Abstraction:** ViewModels communicate exclusively with Domain Use Cases; UI components never query Room DAOs or bridge adapters directly.
- **Isolation of Bridge:** Bridge adapters implement `InputBridgeAdapter` interface. Swapping from ADB to Shizuku requires zero modifications to mapping logic.

---

## 6. Proposed Navigation, Screen Contracts, & UI System

### Design Tokens & Palette

- **Primary Background (Midnight Blue):** `#111827`
- **Surface & Card Container (Slate):** `#1E293B`
- **Primary Accent / Brand (Electric Blue):** `#38BDF8`
- **Secondary Surface Accent:** `#334155`
- **Warning / Conflict State:** `#F59E0B` (Amber)
- **Error / Fail-Stop State:** `#EF4444` (Crimson)
- **Success / Active Play Mode:** `#10B981` (Emerald)
- **Typography:** Android Native / Roboto with clear numeric tabular alignment for coordinates and timings.

### Navigation Architecture

Navigation Compose with 4 permanent bottom destinations and 2 dedicated full-screen flows:

```
[Bottom Navigation]
  ├── Tab 1: Home Screen (Dashboard, Activation Quick Status, Last Played Game, Resume Banner)
  ├── Tab 2: Games Library (Manually Added Games, Package Metadata, Add Game Dialog)
  ├── Tab 3: Profiles Hub (Per-Game Mappings, Favorites, Import/Export, Snapshot History)
  └── Tab 4: Settings (Bridge Selection, Devices, Theme, Diagnostics, Pro Status, Account)

[Dedicated Full-Screen Flows]
  ├── Activation Wizard (Step-by-step wireless debugging pairing or Shizuku permission check)
  ├── Mapping Editor (Canvas overlay, control palette, drag-and-drop, numeric inspector)
  └── Dedicated Test Mode (Sandbox testing virtual controls without starting game)

[Floating Runtime Overlay]
  └── Play Mode Floating Pill (Collapsed status dot / Expandable quick pause, stop & profile switch)
```

---

## 7. Runtime Bridge Contracts, State Machine, & Safety Lifecycle

### Session State Machine

```
              ┌──────────────┐
              │   STOPPED    │ ◄────────────────────────┐
              └──────┬───────┘                          │
                     │ User taps "Start Play Mode"      │
                     ▼                                  │
              ┌──────────────┐                          │
              │  PREFLIGHT   │                          │
              └──────┬───────┘                          │
                     │                                  │
         ┌───────────┴───────────┐                      │
         ▼                       ▼                      │
   [Critical Fail]         [Checks Pass]                │
         │                       │                      │
         ▼                       ▼                      │
  ┌──────────────┐        ┌──────────────┐              │
  │   BLOCKED    │        │    READY     │              │
  └──────────────┘        └──────┬───────┘              │
                                 │ Runtime init         │
                                 ▼                      │
                          ┌──────────────┐              │
                          │    ACTIVE    │ ─────────────┤
                          └──────┬───────┘ User taps    │
                                 │         "Stop"       │
              ┌──────────────────┼──────────────────┐   │
              ▼                  ▼                  ▼   │
     [Bridge Error]       [Focus Lost]      [Hot-plug Unplug]
              │                  │                  │
              ▼                  ▼                  │
       ┌─────────────┐    ┌─────────────┐           │
       │  SAFE_STOP  │    │PAUSED/RESET │           │
       └──────┬──────┘    └──────┬──────┘           │
              │                  │ Focus regained   │
              │                  ▼ & bridge healthy │
              └──────────────────┴──────────────────┘
```

### Preflight Checklist

Before transitioning from `PREFLIGHT` to `READY`, the engine evaluates:
1. **Bridge Health:** Bridge daemon ping responds within $250\text{ms}$.
2. **Permissions:** `SYSTEM_ALERT_WINDOW` (Overlay) granted; bridge execution authorized.
3. **Game Association:** Target game package is verified.
4. **Profile Validity:** At least one mapping defined; all coordinates clamped to `[0.0, 1.0]`.
5. **Macro Bounds:** No macro exceeds max duration ($10\text{s}$) or repeat count ($20$).
6. **Device Status:** At least one physical input device detected (or bypass permitted).

---

## 8. Local Entity Model & Persistence Strategy

Room SQLite acts as the sole local source of truth.

```
┌──────────────────┐       1:N       ┌──────────────────┐       1:N       ┌──────────────────┐
|      Game        | ─────────────── |     Profile      | ─────────────── |  LayoutVariant   |
├──────────────────┤                 ├──────────────────┤                 ├──────────────────┤
| gameId (PK)      |                 | profileId (PK)   |                 | variantId (PK)   |
| packageName (UQ) |                 | gameId (FK)      |                 | profileId (FK)   |
| displayName      |                 | name             |                 | orientation      |
| iconUri          |                 | schemaVersion    |                 | aspectRatio      |
| firstAddedAt     |                 | isFavorite       |                 | coordinateVer    |
| lastPlayedAt     |                 | createdAt        |                 └────────┬─────────┘
└──────────────────┘                 | updatedAt        |                          │ 1:N
                                     └────────┬─────────┘                          ▼
                                              │                           ┌──────────────────┐
                 ┌────────────────────────────┼──────────────┐            |     Mapping      |
                 │ 1:N                        │ 1:N          │ 1:N        ├──────────────────┤
                 ▼                            ▼              ▼            | mappingId (PK)   |
       ┌──────────────────┐         ┌──────────────────┐ ┌──────────────┐ | variantId (FK)   |
       |      Macro       |         | ProfileSnapshot  | | Calibration  | | controlType      |
       ├──────────────────┤         ├──────────────────┤ ├──────────────┤ | actionType       |
       | macroId (PK)     |         | snapshotId (PK)  | | calibId (PK) | | keyCode / chord  |
       | profileId (FK)   |         | profileId (FK)   | | profileId(FK)| | normX / normY    |
       | name             |         | reason           | | deviceClass  | | normW / normH    |
       | triggerSpec      |         | schemaVersion    | | resultJson   | | settingsJson     |
       | maxRepeats       |         | checksum         | | updatedAt    | | supportState     |
       | maxDurationMs    |         | payloadJson      | └──────────────┘ └──────────────────┘
       └────────┬─────────┘         | createdAt        |
                │ 1:N               └──────────────────┘
                ▼
       ┌──────────────────┐
       |    MacroStep     |
       ├──────────────────┤
       | stepId (PK)      |
       | macroId (FK)     |
       | sequenceIndex    |
       | actionType       |
       | targetX, targetY |
       | delayMs          |
       | durationMs       |
       └──────────────────┘
```

### Invariants & Data Integrity

- **Foreign Key Safety:** Deleting a `Game` record preserves associated `Profile` records (uninstalls do not wipe configs).
- **Atomic File Writing:** Profile JSON and ZIP export files are written first to a temporary staging file (`.tmp`), validated with SHA-256 checksums, and atomically renamed.
- **Safe Import Parser:** ZIP archives are inspected before extraction. Files with path traversals (`../`), absolute paths, or sizes exceeding $5\text{MB}$ are rejected immediately. Conflict resolution defaults to `KEEP_BOTH`.

---

## 9. Firebase Backend, Google Play Billing, & Pro Entitlement

### Architecture & Security Rules

```
┌─────────────────┐       Token + SKU        ┌────────────────────────┐
|  Android Client │ ───────────────────────> | Cloud Function:        |
|  (VitualEmu)    |                          | verifyAndClaimPurchase |
└─────────────────┘                          └───────────┬────────────┘
         ▲                                               │
         │ Read Pro Status                               ▼
         │ (uid == auth.uid)                 ┌────────────────────────┐
┌────────┴────────┐                          | Google Play Developer  |
| Cloud Firestore | ◄─────────────────────── | API Verification       |
└─────────────────┘   Write Entitlement      └────────────────────────┘
                      (Server-side only)
```

- **Firestore Collections:**
  - `/accounts/{uid}`: Read/write for authenticated owner.
  - `/proEntitlements/{uid}`: Read-only for authenticated owner; server write only.
  - `/purchaseBindings/{tokenFingerprint}`: Server-side only. Enforces 1:1 token-to-user binding.
  - `/purchaseEvents/{eventId}`: Idempotency log for RTDN events.
  - `/deletionAudits/{requestId}`: Minimal anonymized audit trail for compliance.
- **Account Deletion Flow:** Triggered in-app or via external URL. Revokes Firebase Auth sessions, clears Firestore user document, and maintains local profiles untouched on the physical device.

---

## 10. Privacy, Permissions, & Google Play Compliance

- **Package Visibility (`QUERY_ALL_PACKAGES`):** VitualEmu does **NOT** request `QUERY_ALL_PACKAGES`. Games are selected via the standard system app picker or manual package name entry, strictly adhering to Google Play restricted permissions policy.
- **Runtime Permissions:**
  - `SYSTEM_ALERT_WINDOW`: Requested contextually only when enabling the floating Play Mode pill.
  - `POST_NOTIFICATIONS`: Contextual runtime request for session foreground service alerts on Android 13+.
- **Zero Default Telemetry:** No analytics SDKs bundled; local error logs stored in app-private storage; diagnostic sharing is user-initiated with redacted credentials.

---

## 11. Implementation Roadmap & Phase Gates

```
Phase 0: Feasibility Spike (Gate 0)
  ├── Objective: Prove touch injection and mouse-look on Redmi Note 13 5G
  ├── Deliverable: Benchmarked PoC report with latency/jitter & safe-stop logs
  └── Gate 0: Go/No-Go on hardware execution capability
        │
Phase 1: Foundation & Activation (Gate 1)
  ├── Objective: App shell, M3 theme, 4-tab navigation, Room DB, Activation Wizard
  ├── Deliverable: Compiling app with truthful connection status and local persistence
  └── Gate 1: Room migrations & state models verified
        │
Phase 2: First Playable Slice (Gate 2)
  ├── Objective: End-to-end mapping flow for 1 game (WASD + 1 action button)
  ├── Deliverable: Functional input translation with explicit start/stop
  └── Gate 2: Zero ghost touches, reliable safe-stop on focus change
        │
Phase 3: Profile Reliability & Editor (Gate 3)
  ├── Objective: Canvas-first editor, normalized coordinates, autosave, undo/redo
  ├── Deliverable: Interactive layout editor with JSON import/export and rollback
  └── Gate 3: Snapshot recovery test passes with 0 data loss
        │
Phase 4: Mapping Depth & Macros (Gate 4)
  ├── Objective: Bounded macro engine, mouse-look tuning, hot-plug recovery
  ├── Deliverable: Macro scheduler with cancellation tokens and peripheral listener
  └── Gate 4: Hot-plug stress test passes without stuck keys
        │
Phase 5: Backend & Pro Entitlement (Gate 5)
  ├── Objective: Firebase Auth, Play Billing Client, Cloud Functions verification
  ├── Deliverable: Secure Pro unlock and account deletion workflows
  └── Gate 5: Security rules test passes; client cannot self-grant Pro
        │
Phase 6: Release Candidate & Publication (Gate 6)
  ├── Objective: Final optimization, accessibility TalkBack audit, Play Store assets
  ├── Deliverable: Signed release bundle targeting API 36+ with Data Safety sign-off
  └── Gate 6: Production readiness approval
```

---

## 12. Requirement-to-Test Traceability Matrix

| Requirement ID | Domain Area | Verification Method & Test Case | Phase Gate |
| :--- | :--- | :--- | :--- |
| **FR-001** | Navigation | Robolectric UI test: 4 tabs restore state; Wizard/Editor launch full-screen. | Gate 1 |
| **FR-002, FR-004** | Games Library | Add package ID manually; verify persistence across app restarts. | Gate 1 |
| **FR-006** | Layout Variants | Change orientation/aspect ratio; verify normalized coordinate mapping. | Gate 3 |
| **FR-007, FR-008** | Mapping Engine | Configure duplicate keys; assert conflict warning and configured rule. | Gate 3 / 4 |
| **FR-013, FR-014** | History & Autosave | Force-stop during edit; verify recovery of last committed state $\le 1\text{s}$. | Gate 3 |
| **FR-015** | Import / Export | Fuzz test with malformed JSON & path traversal ZIP; assert safe rejection. | Gate 3 |
| **FR-016, FR-017** | Macro Safety | Start macro, trigger focus loss; assert immediate cancellation and input release. | Gate 4 |
| **FR-019, FR-023** | Preflight & Focus | Launch Play Mode with disconnected bridge; verify blocked state. | Gate 2 |
| **FR-026** | Hot-Plug | Disconnect USB mouse while button held; assert held registry cleared. | Gate 4 |
| **FR-031, FR-032** | Pro Entitlement | Attempt client Firestore write to `/proEntitlements`; assert permission denied. | Gate 5 |
| **FR-035** | Account Deletion | Trigger account deletion; assert cloud data purged and local profiles intact. | Gate 5 |

---

## 13. Unresolved Decisions & Implementation Blockers

1. **OD-01 (Pro Commercial Model):** Recommendation is a one-time lifetime IAP ($4.99–$9.99). Need confirmation before writing BillingClient integration in Phase 5.
2. **OD-02 (Exact Injection Path):** Gated on Gate 0 test results on the Redmi Note 13 5G.
3. **OD-03 (Mouse-Look Mode):** Will determine if relative pointer capture is feasible or if touch-region emulation is mandatory.
4. **OD-07 (Macro Numeric Limits):** Proposed defaults: Max 10 steps, max 20 repeats, max 10,000ms duration, minimum step delay 25ms.
5. **OD-08 (Snapshot Retention):** Proposed limits: Maximum 10 snapshots per profile; retain at least one last-known-good.
6. **OD-09 (Application ID):** Proposed unique ID: `com.aistudio.vitualemu.kmpseq`.

---

## 14. Actionable Next Step & Phase 0 Approval

The plan is complete, structured, and strictly isolated within Plan Mode. No code or Gradle files have been altered.

**First Actionable Step upon Approval:**
1. Update `metadata.json` with the application name `"VitualEmu"` and detailed description.
2. Configure Phase 0 feasibility spike harnesses and test scripts to evaluate Rootless ADB and Shizuku touch injection on the target hardware.

*Awaiting user approval to proceed.*
