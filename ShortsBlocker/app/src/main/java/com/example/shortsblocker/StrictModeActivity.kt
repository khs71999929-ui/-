package com.example.shortsblocker

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.TimeUnit

class StrictModeActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tickRunnable: Runnable

    private lateinit var startSection: LinearLayout
    private lateinit var activeSection: LinearLayout
    private lateinit var waitSection: LinearLayout
    private lateinit var hoursLabel: TextView
    private lateinit var hoursSeekBar: SeekBar
    private lateinit var remainingTimeText: TextView
    private lateinit var waitCountdownText: TextView
    private lateinit var tryUnlockButton: Button
    private lateinit var finalUnlockButton: Button

    private val waitDurationMs = 60_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_strict_mode)

        startSection = findViewById(R.id.startSection)
        activeSection = findViewById(R.id.activeSection)
        waitSection = findViewById(R.id.waitSection)
        hoursLabel = findViewById(R.id.hoursLabel)
        hoursSeekBar = findViewById(R.id.hoursSeekBar)
        remainingTimeText = findViewById(R.id.remainingTimeText)
        waitCountdownText = findViewById(R.id.waitCountdownText)
        tryUnlockButton = findViewById(R.id.tryUnlockButton)
        finalUnlockButton = findViewById(R.id.finalUnlockButton)

        hoursSeekBar.progress = 12
        hoursLabel.text = "12시간"
        hoursSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val h = progress.coerceAtLeast(1)
                hoursLabel.text = "${h}시간"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        findViewById<Button>(R.id.startStrictButton).setOnClickListener {
            val hours = hoursSeekBar.progress.coerceIn(1, 24)
            val endTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(hours.toLong())
            Prefs.setStrictModeEndTime(this, endTime)
            Prefs.clearUnlockSurvey(this)
            Prefs.setBlockingEnabled(this, true)
            refreshUi()
        }

        tryUnlockButton.setOnClickListener { showSurvey() }

        finalUnlockButton.setOnClickListener {
            Prefs.clearStrictMode(this)
            Prefs.clearUnlockSurvey(this)
            refreshUi()
        }

        tickRunnable = object : Runnable {
            override fun run() {
                refreshUi()
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunnable)
    }

    private fun refreshUi() {
        val active = Prefs.isStrictModeActive(this)
        startSection.visibility = if (active) android.view.View.GONE else android.view.View.VISIBLE
        activeSection.visibility = if (active) android.view.View.VISIBLE else android.view.View.GONE
        if (!active) return

        val remainingMs = Prefs.getStrictModeEndTime(this) - System.currentTimeMillis()
        remainingTimeText.text = formatDuration(remainingMs.coerceAtLeast(0))

        val surveyTime = Prefs.getUnlockSurveyPassedTime(this)
        if (surveyTime <= 0L) {
            waitSection.visibility = android.view.View.GONE
            tryUnlockButton.visibility = android.view.View.VISIBLE
            return
        }

        tryUnlockButton.visibility = android.view.View.GONE
        waitSection.visibility = android.view.View.VISIBLE
        val elapsed = System.currentTimeMillis() - surveyTime
        val remainWait = waitDurationMs - elapsed
        if (remainWait > 0) {
            waitCountdownText.text = "${(remainWait / 1000) + 1}초"
            finalUnlockButton.isEnabled = false
        } else {
            waitCountdownText.text = "지금 해제할 수 있어요"
            finalUnlockButton.isEnabled = true
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    /** 조기 종료 전 짧은 설문. 답변 내용으로 막지는 않고, 스스로 돌아볼 시간을 준 뒤 대기 타이머로 넘어간다. */
    private fun showSurvey() {
        askYesNo(
            "정말 급한 일이 생겼나요?",
            "지금 꼭 꺼야 하는 급한 상황(연락, 업무 등)이 있나요?"
        ) {
            askYesNo(
                "다시 생각해보기",
                "1분 뒤에도 정말 그만두고 싶다면 해제할 수 있어요. 계속할까요?"
            ) {
                Prefs.setUnlockSurveyPassedTime(this, System.currentTimeMillis())
                refreshUi()
            }
        }
    }

    private fun askYesNo(title: String, message: String, onDone: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("예") { d, _ -> d.dismiss(); onDone() }
            .setNegativeButton("아니오") { d, _ -> d.dismiss(); onDone() }
            .show()
    }
}
