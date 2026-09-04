package com.example.shortsblocker

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.UUID

class AdminSettingsActivity : AppCompatActivity() {

    private lateinit var messageListContainer: LinearLayout
    private lateinit var photoListContainer: LinearLayout
    private lateinit var domainListContainer: LinearLayout
    private lateinit var thresholdInput: EditText

    private val messages = mutableListOf<String>()
    private val photoPaths = mutableListOf<String>()
    private val domains = mutableListOf<String>()

    private val pickPhotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val input = contentResolver.openInputStream(uri) ?: return@registerForActivityResult
            val dir = File(filesDir, "photos").apply { mkdirs() }
            val outFile = File(dir, "${UUID.randomUUID()}.jpg")
            input.use { inp -> outFile.outputStream().use { out -> inp.copyTo(out) } }
            photoPaths.add(outFile.absolutePath)
            renderPhotoList()
        } catch (e: Exception) {
            Toast.makeText(this, "사진을 불러오지 못했어요: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_settings)
        // PIN 검증이 끝나기 전까지는 내용이 안 보이게 화면을 비워둔다
        findViewById<android.view.View>(android.R.id.content).visibility = android.view.View.INVISIBLE

        verifyPin { success ->
            if (!success) {
                finish()
                return@verifyPin
            }
            findViewById<android.view.View>(android.R.id.content).visibility = android.view.View.VISIBLE
            initScreen()
        }
    }

    private fun initScreen() {
        messageListContainer = findViewById(R.id.messageListContainer)
        photoListContainer = findViewById(R.id.photoListContainer)
        domainListContainer = findViewById(R.id.domainListContainer)
        thresholdInput = findViewById(R.id.thresholdInput)

        messages.addAll(Prefs.getMessages(this))
        photoPaths.addAll(Prefs.getPhotoPaths(this))
        domains.addAll(Prefs.getBlockedDomains(this))
        thresholdInput.setText(Prefs.getMessageThreshold(this).toString())

        renderMessageList()
        renderPhotoList()
        renderDomainList()

        val messageInput = findViewById<EditText>(R.id.messageInput)
        findViewById<Button>(R.id.addMessageButton).setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                messages.add(text)
                messageInput.setText("")
                renderMessageList()
            }
        }

        findViewById<Button>(R.id.addPhotoButton).setOnClickListener {
            pickPhotoLauncher.launch("image/*")
        }

        val domainInput = findViewById<EditText>(R.id.domainInput)
        findViewById<Button>(R.id.addDomainButton).setOnClickListener {
            val text = domainInput.text.toString().trim()
            if (text.isNotEmpty()) {
                domains.add(text)
                domainInput.setText("")
                renderDomainList()
            }
        }

        findViewById<Button>(R.id.saveAllButton).setOnClickListener {
            Prefs.setMessages(this, messages)
            Prefs.setPhotoPaths(this, photoPaths)
            Prefs.setBlockedDomains(this, domains)
            val threshold = thresholdInput.text.toString().toIntOrNull() ?: 5
            Prefs.setMessageThreshold(this, threshold.coerceAtLeast(1))
            Toast.makeText(this, "저장했어요", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /** PIN이 없으면 새로 설정, 있으면 입력받아 검증한다. 결과는 콜백으로 전달. */
    private fun verifyPin(onResult: (Boolean) -> Unit) {
        if (!Prefs.hasPin(this)) {
            promptPin(
                title = "관리자 PIN 설정",
                message = "앞으로 이 화면에 들어올 때 필요한 4자리 이상 PIN을 만들어주세요.",
                onSubmit = { input ->
                    if (input.length >= 4) {
                        Prefs.setPin(this, input)
                        true
                    } else {
                        Toast.makeText(this, "4자리 이상으로 설정해주세요", Toast.LENGTH_SHORT).show()
                        false
                    }
                },
                onResult = onResult
            )
        } else {
            promptPin(
                title = "PIN 입력",
                message = "관리자 PIN을 입력하세요.",
                onSubmit = { input -> input == Prefs.getPin(this) },
                onResult = onResult
            )
        }
    }

    /** 확인을 누르면 onSubmit 검증 후 통과 시 닫고 onResult(true), 취소하면 onResult(false) */
    private fun promptPin(
        title: String,
        message: String,
        onSubmit: (String) -> Boolean,
        onResult: (Boolean) -> Unit
    ) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            gravity = Gravity.CENTER
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(input)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setView(container)
            .setCancelable(false)
            .setNegativeButton("취소") { d, _ ->
                d.dismiss()
                onResult(false)
            }
            .setPositiveButton("확인", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val ok = onSubmit(input.text.toString().trim())
                if (ok) {
                    dialog.dismiss()
                    onResult(true)
                } else {
                    if (Prefs.hasPin(this) || input.text.isNotEmpty()) {
                        Toast.makeText(this, "PIN이 틀렸어요", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun renderMessageList() {
        messageListContainer.removeAllViews()
        messages.forEachIndexed { index, msg ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val tv = TextView(this).apply {
                text = msg
                setTextColor(resources.getColor(R.color.text_light, theme))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val del = TextView(this).apply {
                text = "삭제"
                setTextColor(resources.getColor(R.color.brand_red, theme))
                setPadding(24, 0, 0, 0)
                setOnClickListener {
                    messages.removeAt(index)
                    renderMessageList()
                }
            }
            row.addView(tv)
            row.addView(del)
            messageListContainer.addView(row)
        }
    }

    private fun renderPhotoList() {
        photoListContainer.removeAllViews()
        photoPaths.forEachIndexed { index, path ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val img = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(160, 160)
                try {
                    setImageBitmap(BitmapFactory.decodeFile(path))
                } catch (e: Exception) { }
            }
            val del = TextView(this).apply {
                text = "  삭제"
                setTextColor(resources.getColor(R.color.brand_red, theme))
                setOnClickListener {
                    photoPaths.removeAt(index)
                    renderPhotoList()
                }
            }
            row.addView(img)
            row.addView(del)
            photoListContainer.addView(row)
        }
    }

    private fun renderDomainList() {
        domainListContainer.removeAllViews()
        domains.forEachIndexed { index, d ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val tv = TextView(this).apply {
                text = d
                setTextColor(resources.getColor(R.color.text_light, theme))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val del = TextView(this).apply {
                text = "삭제"
                setTextColor(resources.getColor(R.color.brand_red, theme))
                setPadding(24, 0, 0, 0)
                setOnClickListener {
                    domains.removeAt(index)
                    renderDomainList()
                }
            }
            row.addView(tv)
            row.addView(del)
            domainListContainer.addView(row)
        }
    }
}
