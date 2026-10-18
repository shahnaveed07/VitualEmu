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
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

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
    var onPhysicalKeyCaptured: ((keyCode: Int, isDown: Boolean) -> Unit)? = null

    @Volatile
    var onPhysicalMouseDeltaCaptured: ((dxNorm: Float, dyNorm: Float) -> Unit)? = null

    @Volatile
    var onToggleShootingModeRequested: (() -> Unit)? = null

    @Volatile
    var onEmergencyStopRequested: ((reason: String) -> Unit)? = null

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
 * physical keyboard/mouse capture surface over target games, and a 1-tap notification Emergency Stop.
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
    private var grabToggleBtn: TextView? = null
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
            ACTION_TOGGLE_INPUT_GRAB -> {
                val nextGrab = !PlayModeOverlayRuntimeBridge.isInputGrabEnabled.value
                updateOverlayFocusableMode(nextGrab)
                return START_STICKY
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

    private fun attachOverlayWindowIfNeeded() {
        if (overlayRootView != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
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

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = (10 * density).roundToInt()
            val padV = (6 * density).roundToInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f * density
                setColor(Color.parseColor("#E60F172A"))
                setStroke((1.5f * density).roundToInt(), Color.parseColor("#38BDF8"))
            }
            isFocusable = true
            isFocusableInTouchMode = true
        }

        // Drag handle title
        val titleView = TextView(this).apply {
            text = "VitualEmu HUD"
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, (8 * density).roundToInt(), 0)
        }
        container.addView(titleView)

        // Input Grab Toggle Button
        val grabBtn = TextView(this).apply {
            text = "GRAB: OFF"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            val ph = (8 * density).roundToInt()
            val pv = (4 * density).roundToInt()
            setPadding(ph, pv, ph, pv)
            background = GradientDrawable().apply {
                cornerRadius = 8f * density
                setColor(Color.parseColor("#38BDF8"))
            }
            setOnClickListener {
                val next = !PlayModeOverlayRuntimeBridge.isInputGrabEnabled.value
                updateOverlayFocusableMode(next)
            }
        }
        grabToggleBtn = grabBtn
        container.addView(grabBtn)

        // Aim Mode Toggle Button
        val aimBtn = TextView(this).apply {
            text = "360° AIM"
            setTextColor(Color.WHITE)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            val ph = (8 * density).roundToInt()
            val pv = (4 * density).roundToInt()
            setPadding(ph, pv, ph, pv)
            setOnClickListener {
                PlayModeOverlayRuntimeBridge.onToggleShootingModeRequested?.invoke()
            }
        }
        container.addView(aimBtn)

        // Emergency Stop Button
        val stopBtn = TextView(this).apply {
            text = "SAFE STOP"
            setTextColor(Color.WHITE)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            val ph = (8 * density).roundToInt()
            val pv = (4 * density).roundToInt()
            setPadding(ph, pv, ph, pv)
            background = GradientDrawable().apply {
                cornerRadius = 8f * density
                setColor(Color.parseColor("#EF4444"))
            }
            setOnClickListener {
                PlayModeOverlayRuntimeBridge.onEmergencyStopRequested?.invoke("Overlay Floating HUD Safe Stop")
                updateOverlayFocusableMode(false)
            }
        }
        container.addView(stopBtn)

        // Allow dragging the HUD pill by dragging the title label
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        titleView.setOnTouchListener { _, event ->
            val lp = overlayLayoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = initialX + (event.rawX - touchStartX).roundToInt()
                    lp.y = initialY + (event.rawY - touchStartY).roundToInt()
                    try {
                        wm.updateViewLayout(container, lp)
                    } catch (_: Throwable) {
                    }
                    true
                }
                else -> false
            }
        }

        // Physical Key & Mouse capture listeners when Grab Mode is active
        container.setOnKeyListener { _, keyCode, keyEvent ->
            if (!PlayModeOverlayRuntimeBridge.isInputGrabEnabled.value) return@setOnKeyListener false
            val isDown = keyEvent.action == KeyEvent.ACTION_DOWN
            val isUp = keyEvent.action == KeyEvent.ACTION_UP
            if (isDown || isUp) {
                PlayModeOverlayRuntimeBridge.onPhysicalKeyCaptured?.invoke(keyCode, isDown)
                true
            } else {
                false
            }
        }

        container.setOnGenericMotionListener { _, motionEvent ->
            if (!PlayModeOverlayRuntimeBridge.isInputGrabEnabled.value) return@setOnGenericMotionListener false
            val isMouse = (motionEvent.source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                (motionEvent.source and InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE
            if (!isMouse) return@setOnGenericMotionListener false

            val screenW = resources.displayMetrics.widthPixels.coerceAtLeast(320).toFloat()
            val screenH = resources.displayMetrics.heightPixels.coerceAtLeast(240).toFloat()

            when (motionEvent.actionMasked) {
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                    val relX = motionEvent.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                    val relY = motionEvent.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                    val dxPx: Float
                    val dyPx: Float
                    if (relX != 0f || relY != 0f) {
                        dxPx = relX
                        dyPx = relY
                    } else {
                        val curX = motionEvent.x
                        val curY = motionEvent.y
                        dxPx = if (lastMouseX.isNaN()) 0f else (curX - lastMouseX)
                        dyPx = if (lastMouseY.isNaN()) 0f else (curY - lastMouseY)
                        lastMouseX = curX
                        lastMouseY = curY
                    }
                    if (dxPx != 0f || dyPx != 0f) {
                        PlayModeOverlayRuntimeBridge.onPhysicalMouseDeltaCaptured?.invoke(
                            dxPx / screenW,
                            dyPx / screenH
                        )
                    }
                    true
                }
                MotionEvent.ACTION_BUTTON_PRESS -> {
                    val btn = motionEvent.actionButton
                    if (btn == MotionEvent.BUTTON_PRIMARY) {
                        PlayModeOverlayRuntimeBridge.onPhysicalKeyCaptured?.invoke(102, true)
                    } else if (btn == MotionEvent.BUTTON_SECONDARY) {
                        PlayModeOverlayRuntimeBridge.onPhysicalKeyCaptured?.invoke(103, true)
                    }
                    true
                }
                MotionEvent.ACTION_BUTTON_RELEASE -> {
                    val btn = motionEvent.actionButton
                    if (btn == MotionEvent.BUTTON_PRIMARY) {
                        PlayModeOverlayRuntimeBridge.onPhysicalKeyCaptured?.invoke(102, false)
                    } else if (btn == MotionEvent.BUTTON_SECONDARY) {
                        PlayModeOverlayRuntimeBridge.onPhysicalKeyCaptured?.invoke(103, false)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(container, params)
            overlayRootView = container
        } catch (_: Throwable) {
        }
    }

    private fun updateOverlayFocusableMode(grabEnabled: Boolean) {
        PlayModeOverlayRuntimeBridge.setInputGrabEnabled(grabEnabled)
        val view = overlayRootView ?: return
        val lp = overlayLayoutParams ?: return
        val wm = windowManager ?: return

        lp.flags = if (grabEnabled) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        grabToggleBtn?.apply {
            text = if (grabEnabled) "GRAB: ON" else "GRAB: OFF"
            (background as? GradientDrawable)?.setColor(
                Color.parseColor(if (grabEnabled) "#22C55E" else "#38BDF8")
            )
        }

        try {
            wm.updateViewLayout(view, lp)
            if (grabEnabled) {
                view.requestFocus()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    view.requestPointerCapture()
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    view.releasePointerCapture()
                }
            }
        } catch (_: Throwable) {
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
        grabToggleBtn = null
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
