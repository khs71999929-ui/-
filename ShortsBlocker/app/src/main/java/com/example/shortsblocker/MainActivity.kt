package com.example.shortsblocker

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var usageText: TextView
    private lateinit var enableButton: Button
    private lateinit var blockingSwitch: SwitchCompat
    private lateinit var browserSwitch: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        usageText = findViewById(R.id.usageText)
        enableButton = findViewById(R.id.enableButton)
        blockingSwitch = findViewById(R.id.blockingSwitch)
        browserSwitch = findViewById(R.id.browserSwitch)

        blockingSwitch.isChecked = Prefs.isBlockingEnabled(this)
        browserSwitch.isChecked = Prefs.isBrowserBlockEnabled(this)

        blockingSwitch.setOnCheckedChangeListener { switchView, checked ->
            if (Prefs.isStrictModeActive(this)) {
                // 엄격 모드 중엔 스위치로 끌 수 없게 되돌린다
                switchView.isChecked = true
                return@setOnCheckedChangeListener
            }
            Prefs.setBlockingEnabled(this, checked)
        }
        browserSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.setBrowserBlockEnabled(this, checked)
        }

        enableButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.appListButton).setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
        }
        findViewById<Button>(R.id.strictModeButton).setOnClickListener {
            startActivity(Intent(this, StrictModeActivity::class.java))
        }
        findViewById<Button>(R.id.adminSettingsButton).setOnClickListener {
            startActivity(Intent(this, AdminSettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val accessibilityOn = isAccessibilityServiceEnabled()
        val strict = Prefs.isStrictModeActive(this)

        statusText.text = when {
            !accessibilityOn -> "⚠️ 접근성 서비스가 꺼져 있어요. 아래 버튼을 눌러 켜주세요."
            strict -> "🔒 엄격 모드 진행 중 — 설정한 시간 동안은 끌 수 없어요."
            else -> "✅ 접근성 서비스가 켜져 있어요. 차단이 작동해요."
        }
        enableButton.text = if (accessibilityOn) "접근성 설정 열기" else "차단 기능 켜기"

        usageText.text = "오늘 ${Prefs.getTodayBlockCount(this)}회 차단 · 누적 ${Prefs.getAllTimeBlockCount(this)}회"

        blockingSwitch.isChecked = Prefs.isBlockingEnabled(this) || strict
        blockingSwitch.isEnabled = !strict
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/${ShortsBlockerService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(service, ignoreCase = true)) return true
        }
        return false
    }
}
