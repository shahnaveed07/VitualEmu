package com.dsc.virtualemu.runtime.capture

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Central router that receives [PhysicalInputEvent]s from multiple capture sources
 * (AccessibilityService, Shizuku getevent) and:
 *
 * 1. Deduplicates events that arrive from both sources within a sliding time window.
 * 2. Applies source priority (Accessibility preferred for keys; Shizuku only for mouse rel).
 * 3. Gates events through the capture state (only forwards when [CaptureState.CAPTURING]).
 * 4. Filters out key-repeat events (only initial down and final up matter for mapping).
 * 5. Emits deduplicated events on [events] SharedFlow for the ViewModel to consume.
 */
class PhysicalInputRouter(
    private val captureStateManager: CaptureStateManager
) {
    companion object {
        /** Deduplication window in nanoseconds (50ms) */
        private const val DEDUP_WINDOW_NS = 50_000_000L

        /** Maximum entries in the dedup cache before pruning */
        private const val MAX_DEDUP_ENTRIES = 64
    }

    private val _events = MutableSharedFlow<PhysicalInputEvent>(
        extraBufferCapacity = 64
    )
    val events: SharedFlow<PhysicalInputEvent> = _events.asSharedFlow()

    /** Dedup cache: key → timestamp of last forwarded event */
    private val dedupCache = LinkedHashMap<Long, Long>(32, 0.75f, true)
    private val dedupLock = Any()

    /**
     * Called by capture sources to submit a raw physical input event.
     * The event may be dropped if it is a duplicate, a key-repeat, or if capture is not active.
     *
     * @return true if the event was forwarded, false if dropped
     */
    fun onRawEvent(event: PhysicalInputEvent): Boolean {
        // Gate: only forward events when actively capturing
        if (!captureStateManager.isCapturing()) return false

        // Filter: drop key-repeat events — only initial down and final up matter
        if (event.type == PhysicalInputType.KEY_REPEAT) return false

        // Mouse movement and wheel always come from Shizuku — no dedup needed
        if (event.type == PhysicalInputType.MOUSE_MOVE ||
            event.type == PhysicalInputType.MOUSE_WHEEL
        ) {
            _events.tryEmit(event)
            return true
        }

        // Dedup: for key and button events, check if same event arrived within window
        val dedupKey = computeDedupKey(event)
        val now = event.timestampNs

        synchronized(dedupLock) {
            val lastSeen = dedupCache[dedupKey]
            if (lastSeen != null && (now - lastSeen) < DEDUP_WINDOW_NS) {
                // Duplicate — drop it
                return false
            }
            dedupCache[dedupKey] = now

            // Prune if cache grows too large
            if (dedupCache.size > MAX_DEDUP_ENTRIES) {
                val threshold = now - DEDUP_WINDOW_NS * 2
                dedupCache.entries.removeAll { it.value < threshold }
            }
        }

        _events.tryEmit(event)
        return true
    }

    /**
     * Compute a dedup key that identifies "the same physical event" regardless of source.
     * Uses keyCode + isDown + deviceId to distinguish events.
     */
    private fun computeDedupKey(event: PhysicalInputEvent): Long {
        val typeOrd = event.type.ordinal.toLong()
        val kc = event.keyCode.toLong() and 0xFFFF
        val down = if (event.isDown) 1L else 0L
        // Combine into a single Long for fast HashMap lookup
        return (typeOrd shl 32) or (kc shl 16) or (down shl 8) or (event.deviceId.toLong() and 0xFF)
    }

    /**
     * Clear dedup cache. Called when capture stops or resets.
     */
    fun reset() {
        synchronized(dedupLock) {
            dedupCache.clear()
        }
    }
}
