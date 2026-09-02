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
    private lateinit var enableButton: Button
    private lateinit var blockingSwitch: SwitchCompat
    private lateinit var browserSwitch: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        enableButton = findViewById(R.id.enableButton)
        blockingSwitch = findViewById(R.id.blockingSwitch)
        browserSwitch = findViewById(R.id.browserSwitch)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        blockingSwitch.isChecked = prefs.getBoolean("blocking_enabled", true)
        browserSwitch.isChecked = prefs.getBoolean("block_in_browser", true)

        blockingSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("blocking_enabled", checked).apply()
        }
        browserSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("block_in_browser", checked).apply()
        }

        enableButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val enabled = isAccessibilityServiceEnabled()
        statusText.text = if (enabled) {
            "✅ 접근성 서비스가 켜져 있어요. 쇼츠가 차단됩니다."
        } else {
            "⚠️ 접근성 서비스가 꺼져 있어요. 아래 버튼을 눌러 켜주세요."
        }
        enableButton.text = if (enabled) "접근성 설정 열기" else "차단 기능 켜기"
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
