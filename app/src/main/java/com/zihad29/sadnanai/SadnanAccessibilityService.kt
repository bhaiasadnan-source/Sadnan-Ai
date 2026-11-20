package com.zihad29.sadnanai

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * AccessibilityService that reads on-screen text from other apps
 * and pushes it to the WebView via SadnanBridge.
 */
class SadnanAccessibilityService : AccessibilityService() {

    private var lastText = ""
    private var lastPush = 0L
    private val DEBOUNCE_MS = 900L
    private val MAX_CHARS = 4000

    override fun onServiceConnected() {
        super.onServiceConnected()
        SadnanBridge.serviceReady = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastPush < DEBOUNCE_MS) return

        val pkg = event.packageName?.toString() ?: ""
        if (pkg == packageName) return

        val root = rootInActiveWindow ?: return
        val text = collectText(root).trim()
        root.recycle()

        if (text.isBlank() || text == lastText) return

        lastText = text
        lastPush = now
        SadnanBridge.pushScreen(text.take(MAX_CHARS), pkg)
    }

    private fun collectText(root: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addFirst(root to 0)
        var chars = 0

        while (stack.isNotEmpty() && chars < MAX_CHARS) {
            val (node, depth) = stack.removeFirst()
            if (depth > 30) continue

            node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
                sb.append(it).append(' ')
                chars += it.length
            }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let {
                sb.append(it).append(' ')
                chars += it.length
            }
            node.hintText?.toString()?.takeIf { it.isNotBlank() }?.let {
                sb.append(it).append(' ')
                chars += it.length
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.addFirst(it to (depth + 1)) }
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ")
    }

    override fun onInterrupt() {}
}
