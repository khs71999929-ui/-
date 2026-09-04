package com.example.shortsblocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 감시 대상:
 *  1) 유튜브 앱 - 쇼츠 화면만 골라서 차단 (view id 휴리스틱)
 *  2) 관리자가 고른 앱 목록 - 앱 자체를 통째로 차단 (진입 즉시 홈으로)
 *  3) 브라우저 - 주소창에 등록된 도메인/키워드가 보이면 차단
 *
 * 추가 보강:
 *  - 이벤트만으로는 놓치는 경우(다른 창이 순간적으로 덮는 경우 등)를 잡기 위해 폴링 병행
 *  - 분할화면(멀티윈도)에서도 모든 창을 훑어서 검사
 *  - 화면이 가려져 뒤로가기가 안 먹히는 상황엔 오디오 포커스를 뺏어 재생을 멈춤
 *  - 엄격 모드가 켜져 있으면 사용자가 토글을 꺼도 무시하고 계속 차단
 */
class ShortsBlockerService : AccessibilityService() {

    private val youtubePackage = "com.google.android.youtube"
    private val settingsPackage = "com.android.settings"
    private val browserPackages = setOf(
        "com.android.chrome",
        "com.sec.android.app.sbrowser",
        "org.mozilla.firefox",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.opera.browser"
    )

    // 유튜브 쇼츠 화면에서 흔히 쓰이는 view id 조각들 (유튜브 업데이트로 바뀔 수 있음)
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
    private val blockCooldownMs = 500L
    private var lastConfirmedShortsTime = 0L
    private var activeFocusRequest: AudioFocusRequest? = null

    private val handler = Handler(Looper.getMainLooper())
    private var currentPackage: String? = null
    private var pollingActive = false
    private val pollIntervalMs = 350L

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!blockingCurrentlyEnabled()) {
                pollingActive = false
                return
            }
            evaluatePackage(currentPackage)
            // 화면이 가려져서 방금 검사가 무언가를 못 찾았더라도, 아주 최근까지
            // 쇼츠였다면(4초 이내) 안전하게 오디오 포커스를 계속 뺏어 소리를 억제한다.
            if (currentPackage == youtubePackage &&
                System.currentTimeMillis() - lastConfirmedShortsTime < 4000
            ) {
                stealAudioFocus()
            }
            if (pollingActive) handler.postDelayed(this, pollIntervalMs)
        }
    }

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
            // packageNames를 지정하지 않아 모든 앱 이벤트를 받는다 (범용 앱 차단 + 설정화면 감시를 위해)
        }
    }

    /** 엄격 모드가 켜져 있으면 사용자가 스위치를 꺼도 무시하고 계속 차단한다 */
    private fun blockingCurrentlyEnabled(): Boolean {
        if (Prefs.isStrictModeActive(this)) return true
        return Prefs.isBlockingEnabled(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        currentPackage = pkg

        if (!blockingCurrentlyEnabled()) {
            stopPolling()
            return
        }

        // 엄격 모드 중 설정 앱에서 우리 접근성 서비스를 끄려는 시도 방지 (최선 노력 수준의 마찰)
        if (pkg == settingsPackage && Prefs.isStrictModeActive(this)) {
            checkAndBlockSettingsTamperAttempt()
            return
        }

        evaluatePackage(pkg)

        if (pkg == youtubePackage || pkg in browserPackages || Prefs.getBlockedApps(this).contains(pkg)) {
            startPollingIfNeeded()
        } else {
            stopPolling()
        }
    }

    private fun evaluatePackage(pkg: String?) {
        pkg ?: return
        when {
            pkg == youtubePackage -> checkYoutubeForShorts()
            Prefs.getBlockedApps(this).contains(pkg) -> blockWholeApp()
            pkg in browserPackages && Prefs.isBrowserBlockEnabled(this) -> checkBrowserForBlockedSite()
        }
    }

    // ---------- 유튜브 쇼츠 전용 감지 ----------

    private fun checkYoutubeForShorts() {
        rootInActiveWindow?.let {
            if (containsShortsNode(it)) {
                lastConfirmedShortsTime = System.currentTimeMillis()
                blockCurrentScreen()
                return
            }
        }
        for (window in windows) {
            val root = window.root ?: continue
            try {
                if (root.packageName == youtubePackage && containsShortsNode(root)) {
                    lastConfirmedShortsTime = System.currentTimeMillis()
                    blockCurrentScreen()
                    return
                }
            } finally {
                root.recycle()
            }
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

    // ---------- 관리자가 고른 앱 전체 차단 ----------

    private fun blockWholeApp() {
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < blockCooldownMs) return
        lastBlockTime = now
        Log.d("ShortsBlocker", "차단 대상 앱 감지 -> 홈으로")
        performGlobalAction(GLOBAL_ACTION_HOME)
        stealAudioFocus()
        registerBlockAndMaybeNag()
    }

    // ---------- 브라우저 사이트 차단 ----------

    private fun checkBrowserForBlockedSite() {
        val root = rootInActiveWindow ?: return
        val addressText = findAddressBarText(root) ?: return
        val defaultBlocked = listOf("youtube.com/shorts")
        val customBlocked = Prefs.getBlockedDomains(this)
        val allBlocked = defaultBlocked + customBlocked
        if (allBlocked.any { addressText.contains(it, ignoreCase = true) }) {
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

    // ---------- 엄격 모드 중 설정 앱 조작 방지 (최선 노력 수준) ----------

    private fun checkAndBlockSettingsTamperAttempt() {
        val root = rootInActiveWindow ?: return
        val appLabel = getString(R.string.app_name)
        if (nodeTreeContainsText(root, appLabel) || nodeTreeContainsText(root, "손쉬운 사용") ||
            nodeTreeContainsText(root, "접근성")
        ) {
            val now = System.currentTimeMillis()
            if (now - lastBlockTime > 300) {
                lastBlockTime = now
                Log.d("ShortsBlocker", "엄격 모드 중 설정 변경 시도 감지 -> 차단")
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    private fun nodeTreeContainsText(node: AccessibilityNodeInfo, text: String, depth: Int = 0): Boolean {
        if (depth > 30) return false
        val nodeText = node.text?.toString()
        if (nodeText != null && nodeText.contains(text, ignoreCase = true)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (nodeTreeContainsText(child, text, depth + 1)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    // ---------- 공통 차단/사용량/오디오 처리 ----------

    private fun blockCurrentScreen() {
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < blockCooldownMs) return
        lastBlockTime = now
        Log.d("ShortsBlocker", "쇼츠/차단대상 화면 감지 -> 차단")
        performGlobalAction(GLOBAL_ACTION_BACK)
        stealAudioFocus()
        registerBlockAndMaybeNag()
    }

    private fun registerBlockAndMaybeNag() {
        val todayCount = Prefs.incrementBlockCount(this)
        val threshold = Prefs.getMessageThreshold(this)
        if (threshold > 0 && todayCount % threshold == 0) {
            val intent = Intent(this, NagActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }

    /**
     * 화면이 가려져서 뒤로가기가 먹히지 않는 경우(다른 창으로 덮거나, 완전히
     * 다른 앱으로 이동해서 백그라운드 재생 중인 경우)에도 소리는 멈추도록,
     * 오디오 포커스를 잠깐 강제로 빼앗는다.
     */
    private fun stealAudioFocus() {
        val am = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener { }
                    .build()
                activeFocusRequest = request
                am.requestAudioFocus(request)
                handler.postDelayed({
                    activeFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                }, 600)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    { },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
        } catch (e: Exception) {
            Log.w("ShortsBlocker", "오디오 포커스 요청 실패: ${e.message}")
        }
    }

    private fun startPollingIfNeeded() {
        if (!pollingActive) {
            pollingActive = true
            handler.postDelayed(pollRunnable, pollIntervalMs)
        }
    }

    private fun stopPolling() {
        pollingActive = false
        handler.removeCallbacks(pollRunnable)
    }

    override fun onInterrupt() {
        stopPolling()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
    }
}
