package com.example.dutycaller

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

object Prefs {
    private const val PREF_NAME = "duty_caller_prefs"
    private const val KEY_NUMBERS = "phone_numbers"
    private const val KEY_AUTO_CALL_ENABLED = "auto_call_enabled"
    private const val KEY_AUTO_HANGUP_ENABLED = "auto_hangup_enabled"
    private const val KEY_AUTO_ANSWER_ENABLED = "auto_answer_enabled"
    private const val KEY_INTERVAL_MIN = "interval_min"
    private const val KEY_INTERVAL_MAX = "interval_max"
    private const val KEY_HANGUP_MIN = "hangup_min"
    private const val KEY_HANGUP_MAX = "hangup_max"
    private const val KEY_NO_ANSWER_TIMEOUT = "no_answer_timeout"
    private const val KEY_MIN_SUCCESS_DURATION = "min_success_duration"
    
    private const val KEY_STATS_COUNT = "stats_call_count"
    private const val KEY_STATS_DURATION = "stats_call_duration"
    private const val KEY_LAST_MONTH = "last_stats_month"
    private const val KEY_GOAL_COUNT = "goal_call_count"
    private const val KEY_GOAL_DURATION = "goal_call_duration"
    private const val KEY_GOAL_DATA = "goal_data_mb"
    private const val KEY_STATS_DATA = "stats_data_usage_mb"
    private const val KEY_CALL_DAYS = "call_days" 
    private const val KEY_PAUSE_START = "pause_start"
    private const val KEY_PAUSE_END = "pause_end" 
    private const val KEY_PAUSE_FEATURES = "pause_features"
    private const val KEY_AUTO_DATA_ENABLED = "auto_data_enabled"
    private const val KEY_DATA_TURBO_ENABLED = "data_turbo_enabled"
    private const val KEY_NEXT_CALL_TIMESTAMP = "next_call_timestamp"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun setNextCallTimestamp(context: Context, timestamp: Long) {
        getPrefs(context).edit().putLong(KEY_NEXT_CALL_TIMESTAMP, timestamp).apply()
    }

    fun getNextCallTimestamp(context: Context): Long {
        return getPrefs(context).getLong(KEY_NEXT_CALL_TIMESTAMP, 0L)
    }

    fun isDataTurboEnabled(context: Context) = getPrefs(context).getBoolean(KEY_DATA_TURBO_ENABLED, false)
    fun setDataTurboEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_DATA_TURBO_ENABLED, enabled).apply()

    fun getPhoneNumbers(context: Context): List<String> {
        val raw = getPrefs(context).getString(KEY_NUMBERS, "") ?: ""
        return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun setPhoneNumbers(context: Context, numbers: String) {
        getPrefs(context).edit().putString(KEY_NUMBERS, numbers).apply()
    }

    fun isAutoCallEnabled(context: Context) = getPrefs(context).getBoolean(KEY_AUTO_CALL_ENABLED, false)
    fun setAutoCallEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_AUTO_CALL_ENABLED, enabled).apply()

    fun isAutoHangupEnabled(context: Context) = getPrefs(context).getBoolean(KEY_AUTO_HANGUP_ENABLED, false)
    fun setAutoHangupEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_AUTO_HANGUP_ENABLED, enabled).apply()

    fun isAutoAnswerEnabled(context: Context) = getPrefs(context).getBoolean(KEY_AUTO_ANSWER_ENABLED, false)
    fun setAutoAnswerEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_AUTO_ANSWER_ENABLED, enabled).apply()

    fun isAutoDataEnabled(context: Context) = getPrefs(context).getBoolean(KEY_AUTO_DATA_ENABLED, false)
    fun setAutoDataEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_AUTO_DATA_ENABLED, enabled).apply()

    fun getCallInterval(context: Context): Pair<Int, Int> {
        val min = getPrefs(context).getInt(KEY_INTERVAL_MIN, 17); val max = getPrefs(context).getInt(KEY_INTERVAL_MAX, 30); return Pair(min, max)
    }
    fun setCallInterval(context: Context, min: Int, max: Int) {
        getPrefs(context).edit().putInt(KEY_INTERVAL_MIN, min).putInt(KEY_INTERVAL_MAX, max).apply()
    }

    fun getHangupInterval(context: Context): Pair<Int, Int> {
        val min = getPrefs(context).getInt(KEY_HANGUP_MIN, 7); val max = getPrefs(context).getInt(KEY_HANGUP_MAX, 10); return Pair(min, max)
    }
    fun setHangupInterval(context: Context, min: Int, max: Int) {
        getPrefs(context).edit().putInt(KEY_HANGUP_MIN, min).putInt(KEY_HANGUP_MAX, max).apply()
    }
    
    fun getNoAnswerTimeout(context: Context) = getPrefs(context).getInt(KEY_NO_ANSWER_TIMEOUT, 30)
    fun setNoAnswerTimeout(context: Context, timeout: Int) = getPrefs(context).edit().putInt(KEY_NO_ANSWER_TIMEOUT, timeout).apply()

    fun getMinSuccessDuration(context: Context) = getPrefs(context).getInt(KEY_MIN_SUCCESS_DURATION, 5) // Default 5s
    fun setMinSuccessDuration(context: Context, duration: Int) = getPrefs(context).edit().putInt(KEY_MIN_SUCCESS_DURATION, duration).apply()
    
    fun checkAndResetMonthlyStats(context: Context) {
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        val savedMonth = getPrefs(context).getInt(KEY_LAST_MONTH, -1)
        if (currentMonth != savedMonth) {
            getPrefs(context).edit().putInt(KEY_STATS_COUNT, 0).putLong(KEY_STATS_DURATION, 0L).putFloat(KEY_STATS_DATA, 0f).putInt(KEY_LAST_MONTH, currentMonth).apply()
        }
    }
    
    fun incrementCallCount(context: Context) {
        checkAndResetMonthlyStats(context); val current = getPrefs(context).getInt(KEY_STATS_COUNT, 0); getPrefs(context).edit().putInt(KEY_STATS_COUNT, current + 1).apply()
    }
    
    fun addCallDuration(context: Context, durationSec: Long) {
        checkAndResetMonthlyStats(context); val current = getPrefs(context).getLong(KEY_STATS_DURATION, 0L); getPrefs(context).edit().putLong(KEY_STATS_DURATION, current + durationSec).apply()
    }

    fun addDataUsage(context: Context, mb: Float) {
        checkAndResetMonthlyStats(context); val current = getPrefs(context).getFloat(KEY_STATS_DATA, 0f); getPrefs(context).edit().putFloat(KEY_STATS_DATA, current + mb).apply()
    }
    
    fun getCallCount(context: Context) = getPrefs(context).getInt(KEY_STATS_COUNT, 0)
    fun getCallDuration(context: Context) = getPrefs(context).getLong(KEY_STATS_DURATION, 0L)
    fun getDataUsage(context: Context) = getPrefs(context).getFloat(KEY_STATS_DATA, 0f)
    
    fun getGoalCount(context: Context) = getPrefs(context).getInt(KEY_GOAL_COUNT, 0)
    fun setGoalCount(context: Context, count: Int) = getPrefs(context).edit().putInt(KEY_GOAL_COUNT, count).apply()
    
    fun getGoalDuration(context: Context) = getPrefs(context).getInt(KEY_GOAL_DURATION, 0)
    fun setGoalDuration(context: Context, minutes: Int) = getPrefs(context).edit().putInt(KEY_GOAL_DURATION, minutes).apply()

    fun getGoalData(context: Context) = getPrefs(context).getInt(KEY_GOAL_DATA, 0)
    fun setGoalData(context: Context, mb: Int) = getPrefs(context).edit().putInt(KEY_GOAL_DATA, mb).apply()
    
    fun getCallDays(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_CALL_DAYS, setOf("1","2","3","4","5","6","7"))!!
    }
    fun setCallDays(context: Context, days: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_CALL_DAYS, days).apply()
    }
    
    fun getPauseTime(context: Context): Pair<String, String> {
        val start = getPrefs(context).getString(KEY_PAUSE_START, "21:00")!!; val end = getPrefs(context).getString(KEY_PAUSE_END, "07:40")!!; return Pair(start, end)
    }
    fun setPauseTime(context: Context, start: String, end: String) {
        getPrefs(context).edit().putString(KEY_PAUSE_START, start).putString(KEY_PAUSE_END, end).apply()
    }
    
    fun getPauseFeatures(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_PAUSE_FEATURES, emptySet())!!
    }
    fun setPauseFeatures(context: Context, features: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_PAUSE_FEATURES, features).apply()
    }
    
    fun exportToJson(context: Context): String {
        val json = JSONObject()
        json.put(KEY_NUMBERS, getPrefs(context).getString(KEY_NUMBERS, ""))
        json.put(KEY_AUTO_CALL_ENABLED, isAutoCallEnabled(context))
        json.put(KEY_AUTO_HANGUP_ENABLED, isAutoHangupEnabled(context))
        json.put(KEY_AUTO_ANSWER_ENABLED, isAutoAnswerEnabled(context))
        json.put(KEY_AUTO_DATA_ENABLED, isAutoDataEnabled(context))
        json.put(KEY_INTERVAL_MIN, getPrefs(context).getInt(KEY_INTERVAL_MIN, 17))
        json.put(KEY_INTERVAL_MAX, getPrefs(context).getInt(KEY_INTERVAL_MAX, 30))
        json.put(KEY_HANGUP_MIN, getPrefs(context).getInt(KEY_HANGUP_MIN, 7))
        json.put(KEY_HANGUP_MAX, getPrefs(context).getInt(KEY_HANGUP_MAX, 10))
        json.put(KEY_NO_ANSWER_TIMEOUT, getNoAnswerTimeout(context))
        json.put(KEY_MIN_SUCCESS_DURATION, getMinSuccessDuration(context))
        json.put(KEY_GOAL_COUNT, getGoalCount(context))
        json.put(KEY_GOAL_DURATION, getGoalDuration(context))
        json.put(KEY_GOAL_DATA, getGoalData(context))
        json.put(KEY_PAUSE_START, getPrefs(context).getString(KEY_PAUSE_START, "21:00"))
        json.put(KEY_PAUSE_END, getPrefs(context).getString(KEY_PAUSE_END, "07:40"))
        val days = JSONArray(); getCallDays(context).forEach { days.put(it) }; json.put(KEY_CALL_DAYS, days)
        val features = JSONArray(); getPauseFeatures(context).forEach { features.put(it) }; json.put(KEY_PAUSE_FEATURES, features)
        return json.toString(2)
    }
    
    fun importFromJson(context: Context, jsonStr: String) {
        val json = JSONObject(jsonStr); val editor = getPrefs(context).edit()
        editor.putString(KEY_NUMBERS, json.optString(KEY_NUMBERS, ""))
        editor.putBoolean(KEY_AUTO_CALL_ENABLED, json.optBoolean(KEY_AUTO_CALL_ENABLED))
        editor.putBoolean(KEY_AUTO_HANGUP_ENABLED, json.optBoolean(KEY_AUTO_HANGUP_ENABLED))
        editor.putBoolean(KEY_AUTO_ANSWER_ENABLED, json.optBoolean(KEY_AUTO_ANSWER_ENABLED))
        editor.putBoolean(KEY_AUTO_DATA_ENABLED, json.optBoolean(KEY_AUTO_DATA_ENABLED))
        editor.putInt(KEY_INTERVAL_MIN, json.optInt(KEY_INTERVAL_MIN, 17))
        editor.putInt(KEY_INTERVAL_MAX, json.optInt(KEY_INTERVAL_MAX, 30))
        editor.putInt(KEY_HANGUP_MIN, json.optInt(KEY_HANGUP_MIN, 7))
        editor.putInt(KEY_HANGUP_MAX, json.optInt(KEY_HANGUP_MAX, 10))
        editor.putInt(KEY_NO_ANSWER_TIMEOUT, json.optInt(KEY_NO_ANSWER_TIMEOUT, 30))
        editor.putInt(KEY_MIN_SUCCESS_DURATION, json.optInt(KEY_MIN_SUCCESS_DURATION, 5))
        editor.putInt(KEY_GOAL_COUNT, json.optInt(KEY_GOAL_COUNT, 0))
        editor.putInt(KEY_GOAL_DURATION, json.optInt(KEY_GOAL_DURATION, 0))
        editor.putInt(KEY_GOAL_DATA, json.optInt(KEY_GOAL_DATA, 0))
        editor.putString(KEY_PAUSE_START, json.optString(KEY_PAUSE_START, "21:00"))
        editor.putString(KEY_PAUSE_END, json.optString(KEY_PAUSE_END, "07:40"))
        val daysArr = json.optJSONArray(KEY_CALL_DAYS); val daysSet = mutableSetOf<String>(); if (daysArr != null) { for (i in 0 until daysArr.length()) daysSet.add(daysArr.getString(i)) }; editor.putStringSet(KEY_CALL_DAYS, daysSet)
        val featArr = json.optJSONArray(KEY_PAUSE_FEATURES); val featSet = mutableSetOf<String>(); if (featArr != null) { for (i in 0 until featArr.length()) featSet.add(featArr.getString(i)) }; editor.putStringSet(KEY_PAUSE_FEATURES, featSet)
        editor.apply()
    }
}
