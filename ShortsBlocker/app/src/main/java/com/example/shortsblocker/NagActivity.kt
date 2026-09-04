package com.example.shortsblocker

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class NagActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nag)

        val countText = findViewById<TextView>(R.id.countText)
        val messageText = findViewById<TextView>(R.id.messageText)
        val photoView = findViewById<ImageView>(R.id.goalPhoto)

        val todayCount = Prefs.getTodayBlockCount(this)
        countText.text = "${todayCount}회 차단했어요"

        val messages = Prefs.getMessages(this)
        val message = if (messages.isNotEmpty()) {
            val idx = Prefs.getMessageCycleIndex(this) % messages.size
            Prefs.advanceMessageCycleIndex(this, messages.size)
            messages[idx]
        } else {
            "잠깐, 이 시간에 다른 걸 할 수 있어요."
        }
        messageText.text = message

        val photos = Prefs.getPhotoPaths(this)
        if (photos.isNotEmpty()) {
            val path = photos.random()
            try {
                photoView.setImageBitmap(BitmapFactory.decodeFile(path))
                photoView.visibility = android.view.View.VISIBLE
            } catch (e: Exception) {
                photoView.visibility = android.view.View.GONE
            }
        }

        findViewById<Button>(R.id.confirmButton).setOnClickListener {
            finish()
        }
    }

    override fun onBackPressed() {
        // 뒤로가기로 무시하고 넘어가지 못하게, 확인 버튼을 눌러야 닫히게 한다
    }
}
