package com.example.shortsblocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.preference.PreferenceManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 유튜브 앱과 주요 브라우저를 감시하다가 "쇼츠" 화면 특유의 UI 요소가
 * 감지되면 자동으로 뒤로가기(GLOBAL_ACTION_BACK)를 눌러 차단한다.
 *
 * 주의: shortsResourceIdHints 는 유튜브 앱 버전이 바뀌면 함께 바뀔 수 있다.
 * 차단이 갑자기 안 되면 이 목록을 최신 유튜브 UI 계층 구조에 맞춰 갱신해야 한다.
 */
class ShortsBlockerService : AccessibilityService() {

    private val youtubePackage = "com.google.android.youtube"
    private val browserPackages = setOf(
        "com.android.chrome",
        "com.sec.android.app.sbrowser",
        "org.mozilla.firefox",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.opera.browser"
    )

    // 유튜브 쇼츠 화면에서 흔히 쓰이는 view id 조각들
    private val shortsResourceIdHints = listOf(
        "reel_recycler",
        "reel_player_page_container",
        "reel_watch_player",
        "shorts_container",
        "reel_progress_bar",
        "shorts_shelf",
        "reel_dyn_remix"
    )

    private var lastBlockTime = 0L
    private val blockCooldownMs = 800L

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("blocking_enabled", true)) return

        when {
            pkg == youtubePackage -> checkYoutubeForShorts()
            pkg in browserPackages && prefs.getBoolean("block_in_browser", true) ->
                checkBrowserForShorts()
        }
    }

    private fun checkYoutubeForShorts() {
        val root = rootInActiveWindow ?: return
        if (containsShortsNode(root)) {
            blockCurrentScreen()
        }
    }

    private fun containsShortsNode(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > 40) return false
        val viewId = node.viewIdResourceName
        if (viewId != null && shortsResourceIdHints.any { viewId.contains(it, ignoreCase = true) }) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (containsShortsNode(child, depth + 1)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun checkBrowserForShorts() {
        val root = rootInActiveWindow ?: return
        val addressText = findAddressBarText(root)
        if (addressText != null && addressText.contains("youtube.com/shorts", ignoreCase = true)) {
            blockCurrentScreen()
        }
    }

    private fun findAddressBarText(node: AccessibilityNodeInfo, depth: Int = 0): String? {
        if (depth > 25) return null
        val viewId = node.viewIdResourceName
        if (viewId != null &&
            (viewId.contains("url_bar") || viewId.contains("location_bar") || viewId.contains("edit_url"))
        ) {
            node.text?.let { return it.toString() }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val result = findAddressBarText(child, depth + 1)
                if (result != null) return result
            } finally {
                child.recycle()
            }
        }
        return null
    }

    private fun blockCurrentScreen() {
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < blockCooldownMs) return
        lastBlockTime = now
        Log.d("ShortsBlocker", "쇼츠 감지됨 -> 차단")
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun onInterrupt() {}
}
