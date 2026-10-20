package com.dsc.virtualemu.runtime.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
 import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

sealed class OverlayNavEvent {
    object OpenEditor : OverlayNavEvent()
    object ReturnHome : OverlayNavEvent()
    object OpenSettings : OverlayNavEvent()
    object OpenProfiles : OverlayNavEvent()
}

/**
 * Shared runtime coordinator connecting [PlayModeOverlayService] (system `TYPE_APPLICATION_OVERLAY`
 * floating HUD + physical key/mouse event grabber) with `VitualEmuViewModel` and `KeymapTranslationEngine`.
 */
object PlayModeOverlayRuntimeBridge {
    private val _isOverlayServiceRunning = MutableStateFlow(false)
    val isOverlayServiceRunning: StateFlow<Boolean> = _isOverlayServiceRunning.asStateFlow()

    private val _isInputGrabEnabled = MutableStateFlow(false)
    val isInputGrabEnabled: StateFlow<Boolean> = _isInputGrabEnabled.asStateFlow()

    @Volatile
    var activeGameName: String = "Game"

    @Volatile
    var activeProfileName: String = "Profile"

    @Volatile
    var sensitivityX: Float = 1.2f

    @Volatile
    var sensitivityY: Float = 1.0f

    @Volatile
    var isTestModePreview: Boolean = false

    @Volatile
    var indicatorVisible: Boolean = true

    @Volatile
    var indicatorColorHex: String = "#FFFFFF"

    @Volatile
    var indicatorAlpha: Float = 0.8f

    @Volatile
    var onPhysicalKeyCaptured: ((keyCode: Int, isDown: Boolean) -> Unit)? = null

    @Volatile
    var onPhysicalMouseDeltaCaptured: ((dxNorm: Float, dyNorm: Float) -> Unit)? = null

    @Volatile
    var onToggleShootingModeRequested: (() -> Unit)? = null

    @Volatile
    var onEmergencyStopRequested: ((reason: String) -> Unit)? = null

    @Volatile
    var onUpdateSensitivityRequested: ((sensX: Float, sensY: Float) -> Unit)? = null

    private val _overlayNavEventFlow = MutableSharedFlow<OverlayNavEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val overlayNavEventFlow: SharedFlow<OverlayNavEvent> = _overlayNavEventFlow.asSharedFlow()

    fun emitNavEvent(event: OverlayNavEvent) {
        _overlayNavEventFlow.tryEmit(event)
    }

    fun markServiceRunning(running: Boolean) {
        _isOverlayServiceRunning.value = running
        if (!running) {
            _isInputGrabEnabled.value = false
        }
    }

    fun setInputGrabEnabled(enabled: Boolean) {
        _isInputGrabEnabled.value = enabled
    }
}

/**
 * Foreground Service managing the system-level floating Play Mode HUD (`TYPE_APPLICATION_OVERLAY`),
 * draggable compact icon, GG Mouse Pro side menu, Keymap Configs, and Keymap Settings panels.
 */
class PlayModeOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "vitualemu_playmode_overlay_channel"
        const val NOTIFICATION_ID = 4102

        const val ACTION_START_OVERLAY = "com.dsc.virtualemu.action.START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.dsc.virtualemu.action.STOP_OVERLAY"
        const val ACTION_EMERGENCY_STOP = "com.dsc.virtualemu.action.EMERGENCY_STOP"
        const val ACTION_TOGGLE_INPUT_GRAB = "com.dsc.virtualemu.action.TOGGLE_INPUT_GRAB"

        fun start(context: Context) {
            val intent = Intent(context, PlayModeOverlayService::class.java).apply {
                action = ACTION_START_OVERLAY
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                PlayModeOverlayRuntimeBridge.markServiceRunning(true)
            } catch (_: Throwable) {
                PlayModeOverlayRuntimeBridge.markServiceRunning(false)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PlayModeOverlayService::class.java).apply {
                action = ACTION_STOP_OVERLAY
            }
            try {
                context.stopService(intent)
            } catch (_: Throwable) {
            }
            PlayModeOverlayRuntimeBridge.markServiceRunning(false)
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayRootView: View? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null

    private var lastMouseX: Float = Float.NaN
    private var lastMouseY: Float = Float.NaN

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_EMERGENCY_STOP -> {
                PlayModeOverlayRuntimeBridge.onEmergencyStopRequested?.invoke("Overlay Notification Emergency Stop")
                removeOverlayWindow()
                PlayModeOverlayRuntimeBridge.markServiceRunning(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_OVERLAY -> {
                removeOverlayWindow()
                PlayModeOverlayRuntimeBridge.markServiceRunning(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                try {
                    startForeground(NOTIFICATION_ID, buildNotification())
                } catch (_: Throwable) {
                }
                PlayModeOverlayRuntimeBridge.markServiceRunning(true)
                if (Settings.canDrawOverlays(this)) {
                    attachOverlayWindowIfNeeded()
                }
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        removeOverlayWindow()
        PlayModeOverlayRuntimeBridge.markServiceRunning(false)
        super.onDestroy()
    }

    private fun attachOverlayWindowIfNeeded(): Boolean {
        if (overlayRootView != null) return true
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        windowManager = wm

        val density = resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (24 * density).roundToInt()
            y = (16 * density).roundToInt()
        }
        overlayLayoutParams = params

        val container = FrameLayout(this)
        overlayRootView = container

        showCompactFloatingIconView(container)

        return try {
            wm.addView(container, params)
            PlayModeOverlayRuntimeBridge.markServiceRunning(true)
            true
        } catch (_: Throwable) {
            removeOverlayWindow()
            PlayModeOverlayRuntimeBridge.markServiceRunning(false)
            false
        }
    }

    /**
     * Compact Draggable Floating Icon View (Collapsed Mode)
     */
    private fun showCompactFloatingIconView(container: FrameLayout) {
        container.removeAllViews()
        val density = resources.displayMetrics.density

        val winParams = overlayLayoutParams ?: return
        winParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        winParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        winParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        try {
            windowManager?.updateViewLayout(container, winParams)
        } catch (_: Throwable) {
        }

        val iconBadge = TextView(this).apply {
            text = "VE"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E60F172A"))
                setStroke((2f * density).roundToInt(), Color.parseColor("#38BDF8"))
            }
        }

        val sizePx = (48 * density).roundToInt()
        container.addView(iconBadge, FrameLayout.LayoutParams(sizePx, sizePx))

        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var isClick = true

        iconBadge.setOnTouchListener { _, event ->
            val wm = windowManager ?: return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = winParams.x
                    initialY = winParams.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchStartX
                    val dy = event.rawY - touchStartY
                    if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) {
                        isClick = false
                    }
                    winParams.x = initialX + dx.roundToInt()
                    winParams.y = initialY + dy.roundToInt()
                    try {
                        wm.updateViewLayout(container, winParams)
                    } catch (_: Throwable) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        showGgSideMenu(container)
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * GG Mouse Pro In-Game Side Menu Panel (Image 1)
     */
    private fun showGgSideMenu(container: FrameLayout) {
        container.removeAllViews()
        val density = resources.displayMetrics.density

        val winParams = overlayLayoutParams ?: return
        winParams.width = WindowManager.LayoutParams.MATCH_PARENT
        winParams.height = WindowManager.LayoutParams.MATCH_PARENT
        winParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        try {
            windowManager?.updateViewLayout(container, winParams)
        } catch (_: Throwable) {
        }

        val rootBg = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#80000000"))
            setOnClickListener {
                showCompactFloatingIconView(container)
            }
        }

        val sidePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * density).roundToInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f * density
                setColor(Color.parseColor("#E61E1438"))
                setStroke((2f * density).roundToInt(), Color.parseColor("#6366F1"))
            }
            setOnClickListener { /* Consume clicks */ }
        }

        if (PlayModeOverlayRuntimeBridge.isTestModePreview) {
            val previewBadge = TextView(this).apply {
                text = "DEBUG / PREVIEW — INPUT INJECTION NOT VERIFIED"
                setTextColor(Color.parseColor("#FDE68A"))
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, 0, 0, (8 * density).roundToInt())
            }
            sidePanel.addView(previewBadge)
        }

        val panelLp = FrameLayout.LayoutParams((320 * density).roundToInt(), FrameLayout.LayoutParams.FILL_PARENT).apply {
            gravity = Gravity.START
        }
        rootBg.addView(sidePanel, panelLp)

        // Top Header Row: Home & Settings Icons
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (20 * density).roundToInt())
        }

        val homeBtn = createCircularIconButton("⌂") {
            PlayModeOverlayRuntimeBridge.emitNavEvent(OverlayNavEvent.ReturnHome)
            showCompactFloatingIconView(container)
        }
        topRow.addView(homeBtn)

        val spacer1 = View(this).apply { layoutParams = LinearLayout.LayoutParams((16 * density).roundToInt(), 1) }
        topRow.addView(spacer1)

        val settingsBtn = createCircularIconButton("⚙") {
            PlayModeOverlayRuntimeBridge.emitNavEvent(OverlayNavEvent.OpenSettings)
            showKeymapSettingsPanel(container)
        }
        topRow.addView(settingsBtn)

        sidePanel.addView(topRow)

        // Grid Menu Options (2 Columns)
        val gridLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(createMenuGridCard("▤", "Keymap Configs", (130 * density).roundToInt()) {
            showKeymapConfigsPanel(container)
        })
        row1.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams((12 * density).roundToInt(), 1) })
        row1.addView(createMenuGridCard("✎", "Edit keymap", (130 * density).roundToInt()) {
            PlayModeOverlayRuntimeBridge.emitNavEvent(OverlayNavEvent.OpenEditor)
            showCompactFloatingIconView(container)
        })
        gridLayout.addView(row1)

        val spacerR1 = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, (12 * density).roundToInt()) }
        gridLayout.addView(spacerR1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(createMenuGridCard("⚙", "Keymap Settings", (130 * density).roundToInt()) {
            showKeymapSettingsPanel(container)
        })
        row2.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams((12 * density).roundToInt(), 1) })
        row2.addView(createMenuGridCard("⌨", "Keyboard Typing", (130 * density).roundToInt()) {
            PlayModeOverlayRuntimeBridge.setInputGrabEnabled(!PlayModeOverlayRuntimeBridge.isInputGrabEnabled.value)
            showCompactFloatingIconView(container)
        })
        gridLayout.addView(row2)

        val spacerR2 = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, (12 * density).roundToInt()) }
        gridLayout.addView(spacerR2)

        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row3.addView(createMenuGridCard("⇄", "Macro", (130 * density).roundToInt()) {
            showCompactFloatingIconView(container)
        })
        row3.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams((12 * density).roundToInt(), 1) })
        row3.addView(createMenuGridCard("🎯", "Recoil control", (130 * density).roundToInt()) {
            PlayModeOverlayRuntimeBridge.onToggleShootingModeRequested?.invoke()
            showCompactFloatingIconView(container)
        })
        gridLayout.addView(row3)

        sidePanel.addView(gridLayout)
        container.addView(rootBg)
    }

    /**
     * Keymap Configs Panel (Image 2)
     */
    private fun showKeymapConfigsPanel(container: FrameLayout) {
        container.removeAllViews()
        val density = resources.displayMetrics.density

        val winParams = overlayLayoutParams ?: return
        winParams.width = WindowManager.LayoutParams.MATCH_PARENT
        winParams.height = WindowManager.LayoutParams.MATCH_PARENT
        try {
            windowManager?.updateViewLayout(container, winParams)
        } catch (_: Throwable) {
        }

        val rootBg = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#90000000"))
            setOnClickListener { showGgSideMenu(container) }
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * density).roundToInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f * density
                setColor(Color.parseColor("#222B59"))
            }
            setOnClickListener { /* consume */ }
        }

        val panelLp = FrameLayout.LayoutParams((420 * density).roundToInt(), FrameLayout.LayoutParams.FILL_PARENT).apply {
            gravity = Gravity.END
        }
        rootBg.addView(panel, panelLp)

        val title = TextView(this).apply {
            text = "Keymap Configs"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, (20 * density).roundToInt())
        }
        panel.addView(title)

        // Active Scheme Item Card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padC = (14 * density).roundToInt()
            setPadding(padC, padC, padC, padC)
            background = GradientDrawable().apply {
                cornerRadius = 14f * density
                setColor(Color.parseColor("#3B82F6"))
            }
        }

        val arrow = TextView(this).apply {
            text = "∨"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, 0, (12 * density).roundToInt(), 0)
        }
        card.addView(arrow)

        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val schemeTitle = TextView(this).apply {
            text = PlayModeOverlayRuntimeBridge.activeProfileName
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        infoCol.addView(schemeTitle)

        val schemeSub = TextView(this).apply {
            text = "· Component Count: 19 · Active Scheme"
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 11f
        }
        infoCol.addView(schemeSub)

        card.addView(infoCol)

        val check = TextView(this).apply {
            text = "✓"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(check)

        panel.addView(card)

        val flexSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, 0, 1f)
        }
        panel.addView(flexSpacer)

        // Bottom Actions Row: Create, Import, Close
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val createBtn = createPillButton("Create") {
            showGgSideMenu(container)
        }
        actions.addView(createBtn)

        actions.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams((12 * density).roundToInt(), 1) })

        val importBtn = createPillButton("Import") {
            showGgSideMenu(container)
        }
        actions.addView(importBtn)

        actions.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams((12 * density).roundToInt(), 1) })

        val closeBtn = createPillButton("Close") {
            showGgSideMenu(container)
        }
        actions.addView(closeBtn)

        panel.addView(actions)
        container.addView(rootBg)
    }

    /**
     * Keymap Settings Panel (Image 7)
     */
    private fun showKeymapSettingsPanel(container: FrameLayout) {
        container.removeAllViews()
        val density = resources.displayMetrics.density

        val winParams = overlayLayoutParams ?: return
        winParams.width = WindowManager.LayoutParams.FILL_PARENT
        winParams.height = WindowManager.LayoutParams.FILL_PARENT
        try {
            windowManager?.updateViewLayout(container, winParams)
        } catch (_: Throwable) {
        }

        val rootBg = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#90000000"))
            setOnClickListener { showGgSideMenu(container) }
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * density).roundToInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f * density
                setColor(Color.parseColor("#222B59"))
            }
            setOnClickListener { /* consume */ }
        }

        val panelLp = FrameLayout.LayoutParams((440 * density).roundToInt(), FrameLayout.LayoutParams.FILL_PARENT).apply {
            gravity = Gravity.END
        }
        rootBg.addView(panel, panelLp)

        val title = TextView(this).apply {
            text = "Keymap Settings"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, (20 * density).roundToInt())
        }
        panel.addView(title)

        // 1. Show Indicator Row
        val showRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * density).roundToInt(), 0, (8 * density).roundToInt())
        }
        val showLabel = TextView(this).apply {
            text = "Show Indicator"
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        showRow.addView(showLabel)

        val showToggle = TextView(this).apply {
            val isVis = PlayModeOverlayRuntimeBridge.indicatorVisible
            text = if (isVis) "ON" else "OFF"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            val ph = (12 * density).roundToInt()
            val pv = (6 * density).roundToInt()
            setPadding(ph, pv, ph, pv)
            background = GradientDrawable().apply {
                cornerRadius = 10f * density
                setColor(Color.parseColor(if (isVis) "#3B82F6" else "#475569"))
            }
            setOnClickListener {
                PlayModeOverlayRuntimeBridge.indicatorVisible = !PlayModeOverlayRuntimeBridge.indicatorVisible
                showKeymapSettingsPanel(container)
            }
        }
        showRow.addView(showToggle)
        panel.addView(showRow)

        // 2. Indicator Color Row
        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * density).roundToInt(), 0, (8 * density).roundToInt())
        }
        val colorLabel = TextView(this).apply {
            text = "Indicator Color"
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams((120 * density).roundToInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        colorRow.addView(colorLabel)

        val palette = listOf("#FFFFFF", "#000000", "#FF3B30", "#34C759", "#AF52DE", "#FFCC00", "#FF9500", "#5AC8FA")
        for (hex in palette) {
            val colorDot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 4f * density
                    setColor(Color.parseColor(hex))
                    if (PlayModeOverlayRuntimeBridge.indicatorColorHex.equals(hex, ignoreCase = true)) {
                        setStroke((2f * density).roundToInt(), Color.WHITE)
                    }
                }
                layoutParams = LinearLayout.LayoutParams((22 * density).roundToInt(), (22 * density).roundToInt()).apply {
                    setMargins(0, 0, (6 * density).roundToInt(), 0)
                }
                setOnClickListener {
                    PlayModeOverlayRuntimeBridge.indicatorColorHex = hex
                    showKeymapSettingsPanel(container)
                }
            }
            colorRow.addView(colorDot)
        }
        panel.addView(colorRow)

        // 3. Indicator Alpha Row
        val alphaRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * density).roundToInt(), 0, (8 * density).roundToInt())
        }
        val alphaVal = (PlayModeOverlayRuntimeBridge.indicatorAlpha * 100f).roundToInt()
        val alphaLabel = TextView(this).apply {
            text = "Indicator Alpha: $alphaVal%"
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        alphaRow.addView(alphaLabel)

        val alphaBar = SeekBar(this).apply {
            max = 100
            progress = alphaVal
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val a = (progress / 100f).coerceIn(0.1f, 1.0f)
                    PlayModeOverlayRuntimeBridge.indicatorAlpha = a
                    alphaLabel.text = "Indicator Alpha: ${progress}%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        alphaRow.addView(alphaBar)
        panel.addView(alphaRow)

        // 4. Indicator Example Preview Card
        val previewLabel = TextView(this).apply {
            text = "Indicator example"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, (8 * density).roundToInt(), 0, (8 * density).roundToInt())
        }
        panel.addView(previewLabel)

        val previewBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val padP = (16 * density).roundToInt()
            setPadding(padP, padP, padP, padP)
            background = GradientDrawable().apply {
                cornerRadius = 12f * density
                setColor(Color.parseColor("#1E293B"))
            }
        }

        val curColor = try { Color.parseColor(PlayModeOverlayRuntimeBridge.indicatorColorHex) } catch (_: Throwable) { Color.WHITE }
        val alphaInt = (PlayModeOverlayRuntimeBridge.indicatorAlpha * 255).roundToInt().coerceIn(10, 255)

        val keyA = createSampleIndicatorCircle("A", curColor, alphaInt, density)
        previewBox.addView(keyA)

        previewBox.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams((20 * density).roundToInt(), 1) })

        val keyB = createSampleIndicatorCircle("B", curColor, alphaInt, density)
        previewBox.addView(keyB)

        panel.addView(previewBox)

        val flexSpacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) }
        panel.addView(flexSpacer)

        // Close Button
        val closeBtn = createPillButton("Close") {
            showGgSideMenu(container)
        }
        panel.addView(closeBtn)

        container.addView(rootBg)
    }

    private fun createSampleIndicatorCircle(label: String, tintColor: Int, alpha: Int, density: Float): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.argb(alpha, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor)))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val sizePx = (36 * density).roundToInt()
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb((alpha * 0.2f).roundToInt(), Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor)))
                setStroke((1.5f * density).roundToInt(), Color.argb(alpha, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor)))
            }
        }
    }

    private fun createCircularIconButton(text: String, onClick: () -> Unit): TextView {
        val density = resources.displayMetrics.density
        val sizePx = (44 * density).roundToInt()
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4C51BF"))
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createMenuGridCard(iconStr: String, labelStr: String, widthPx: Int, onClick: () -> Unit): LinearLayout {
        val density = resources.displayMetrics.density
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = (12 * density).roundToInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(widthPx, (80 * density).roundToInt())
            background = GradientDrawable().apply {
                cornerRadius = 12f * density
                setColor(Color.parseColor("#4338CA"))
            }
            setOnClickListener { onClick() }
        }

        val iconView = TextView(this).apply {
            text = iconStr
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(iconView)

        val labelView = TextView(this).apply {
            text = labelStr
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, (4 * density).roundToInt(), 0, 0)
        }
        card.addView(labelView)

        return card
    }

    private fun createPillButton(label: String, onClick: () -> Unit): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#1E1438"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val ph = (24 * density).roundToInt()
            val pv = (10 * density).roundToInt()
            setPadding(ph, pv, ph, pv)
            background = GradientDrawable().apply {
                cornerRadius = 20f * density
                setColor(Color.WHITE)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun removeOverlayWindow() {
        PlayModeOverlayRuntimeBridge.setInputGrabEnabled(false)
        val view = overlayRootView ?: return
        try {
            windowManager?.removeView(view)
        } catch (_: Throwable) {
        }
        overlayRootView = null
        overlayLayoutParams = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VitualEmu Play Mode Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active keyboard and mouse mapping overlay HUD and emergency stop control"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, PlayModeOverlayService::class.java).apply {
            action = ACTION_EMERGENCY_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("VitualEmu Play Mode Active")
            .setContentText("Multi-touch keymapper active. Tap SAFE STOP to neutralize all inputs.")
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_delete,
                    "SAFE STOP",
                    stopPendingIntent
                ).build()
            )
            .build()
    }
}
