package com.example.shortsblocker

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AppListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_list)

        val container = findViewById<LinearLayout>(R.id.appListContainer)
        val saveButton = findViewById<Button>(R.id.saveAppListButton)
        val blocked = Prefs.getBlockedApps(this)

        val pm = packageManager
        val launchableApps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }
            .distinct()
            .filter { it != packageName } // 자기 자신은 목록에서 제외
            .sortedBy { pkg ->
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().lowercase()
            }

        val checkBoxes = mutableMapOf<String, CheckBox>()

        for (pkg in launchableApps) {
            val appInfo: ApplicationInfo = try {
                pm.getApplicationInfo(pkg, 0)
            } catch (e: Exception) {
                continue
            }
            val label = pm.getApplicationLabel(appInfo).toString()

            val cb = CheckBox(this).apply {
                text = "$label\n$pkg"
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_light, theme))
                isChecked = blocked.contains(pkg)
                setPadding(8, 20, 8, 20)
            }
            checkBoxes[pkg] = cb
            container.addView(cb)
        }

        saveButton.setOnClickListener {
            val selected = checkBoxes.filterValues { it.isChecked }.keys
            Prefs.setBlockedApps(this, selected)
            Toast.makeText(this, "저장했어요 (${selected.size}개 앱 차단)", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
