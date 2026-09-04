package com.example.shortsblocker

import android.content.Context
import android.preference.PreferenceManager
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 앱 전체에서 쓰는 설정/데이터 저장소.
 * SharedPreferences + JSON 배열만으로 단순하게 구현 (별도 DB 없음).
 */
object Prefs {

    private fun sp(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx)

    // ---------- 기본 on/off ----------
    fun isBlockingEnabled(ctx: Context) = sp(ctx).getBoolean("blocking_enabled", true)
    fun setBlockingEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("blocking_enabled", v).apply()

    fun isBrowserBlockEnabled(ctx: Context) = sp(ctx).getBoolean("block_in_browser", true)
    fun setBrowserBlockEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("block_in_browser", v).apply()

    // ---------- 관리자 PIN ----------
    fun hasPin(ctx: Context) = sp(ctx).contains("admin_pin")
    fun getPin(ctx: Context) = sp(ctx).getString("admin_pin", "") ?: ""
    fun setPin(ctx: Context, pin: String) = sp(ctx).edit().putString("admin_pin", pin).apply()

    // ---------- 커스텀 문구 (독설/명언 대신 팩트 자각형 문구) ----------
    fun getMessages(ctx: Context): MutableList<String> = getJsonList(ctx, "custom_messages")
    fun setMessages(ctx: Context, list: List<String>) = setJsonList(ctx, "custom_messages", list)

    // ---------- 목표 사진 (내부 저장소 파일 경로 목록) ----------
    fun getPhotoPaths(ctx: Context): MutableList<String> = getJsonList(ctx, "goal_photo_paths")
    fun setPhotoPaths(ctx: Context, list: List<String>) = setJsonList(ctx, "goal_photo_paths", list)

    // ---------- 전체 차단 대상 앱 (패키지명 목록) ----------
    fun getBlockedApps(ctx: Context): MutableSet<String> = getJsonList(ctx, "blocked_apps_full").toMutableSet()
    fun setBlockedApps(ctx: Context, set: Set<String>) = setJsonList(ctx, "blocked_apps_full", set.toList())

    // ---------- 브라우저에서 차단할 도메인/키워드 목록 ----------
    fun getBlockedDomains(ctx: Context): MutableList<String> = getJsonList(ctx, "blocked_domains")
    fun setBlockedDomains(ctx: Context, list: List<String>) = setJsonList(ctx, "blocked_domains", list)

    // ---------- 문구 등장 주기 (몇 번 차단마다 문구를 보여줄지) ----------
    fun getMessageThreshold(ctx: Context) = sp(ctx).getInt("message_threshold", 5)
    fun setMessageThreshold(ctx: Context, v: Int) = sp(ctx).edit().putInt("message_threshold", v).apply()

    // ---------- 사용량(차단 횟수) 통계 ----------
    private fun todayKey(): String = "usage_" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun incrementBlockCount(ctx: Context): Int {
        val key = todayKey()
        val newTotal = sp(ctx).getInt(key, 0) + 1
        val newAllTime = sp(ctx).getInt("usage_all_time", 0) + 1
        sp(ctx).edit()
            .putInt(key, newTotal)
            .putInt("usage_all_time", newAllTime)
            .apply()
        return newTotal
    }

    fun getTodayBlockCount(ctx: Context): Int = sp(ctx).getInt(todayKey(), 0)
    fun getAllTimeBlockCount(ctx: Context): Int = sp(ctx).getInt("usage_all_time", 0)

    fun getMessageCycleIndex(ctx: Context): Int = sp(ctx).getInt("message_cycle_index", 0)
    fun advanceMessageCycleIndex(ctx: Context, size: Int) {
        if (size <= 0) return
        val next = (getMessageCycleIndex(ctx) + 1) % size
        sp(ctx).edit().putInt("message_cycle_index", next).apply()
    }

    // ---------- 엄격 모드 ----------
    fun getStrictModeEndTime(ctx: Context): Long = sp(ctx).getLong("strict_end_time", 0L)
    fun setStrictModeEndTime(ctx: Context, endTimeMillis: Long) =
        sp(ctx).edit().putLong("strict_end_time", endTimeMillis).apply()

    fun isStrictModeActive(ctx: Context): Boolean = System.currentTimeMillis() < getStrictModeEndTime(ctx)

    fun clearStrictMode(ctx: Context) = sp(ctx).edit().putLong("strict_end_time", 0L).apply()

    // 조기 해제 설문을 통과한 시각 (이 시각 + 60초가 지나야 실제 해제 버튼 활성화)
    fun getUnlockSurveyPassedTime(ctx: Context): Long = sp(ctx).getLong("unlock_survey_time", 0L)
    fun setUnlockSurveyPassedTime(ctx: Context, t: Long) =
        sp(ctx).edit().putLong("unlock_survey_time", t).apply()
    fun clearUnlockSurvey(ctx: Context) = sp(ctx).edit().putLong("unlock_survey_time", 0L).apply()

    // ---------- 공통 JSON 리스트 유틸 ----------
    private fun getJsonList(ctx: Context, key: String): MutableList<String> {
        val raw = sp(ctx).getString(key, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list.add(arr.getString(i))
        return list
    }

    private fun setJsonList(ctx: Context, key: String, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        sp(ctx).edit().putString(key, arr.toString()).apply()
    }
}
