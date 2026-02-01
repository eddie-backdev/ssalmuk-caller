package com.example.dutycaller

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var switchAutoCall: SwitchCompat
    private lateinit var etIntervalMin: EditText
    private lateinit var etIntervalMax: EditText
    private lateinit var switchAutoHangup: SwitchCompat
    private lateinit var etHangupMin: EditText
    private lateinit var etHangupMax: EditText
    private lateinit var etNoAnswerTimeout: EditText
    private lateinit var switchAutoAnswer: SwitchCompat
    private lateinit var btnSetAccessibility: Button
    private lateinit var btnSetBattery: Button // Added
    private lateinit var btnSetAlarm: Button
    private lateinit var btnBackupRestore: Button
    private lateinit var tvCallCount: TextView
    private lateinit var tvCallDuration: TextView
    private lateinit var tvDataUsage: TextView
    private lateinit var pbCallCount: android.widget.ProgressBar
    private lateinit var pbCallDuration: android.widget.ProgressBar
    private lateinit var pbDataUsage: android.widget.ProgressBar
    private lateinit var tvIntervalLabel: TextView 
    private lateinit var btnManageNumbers: Button
    private lateinit var tvNumberCount: TextView
    private lateinit var etGoalCount: EditText
    private lateinit var etGoalDuration: EditText
    private lateinit var etGoalData: EditText
    private lateinit var switchAutoData: SwitchCompat
    private lateinit var cbDataTurbo: AppCompatCheckBox
    private lateinit var layoutWeekDays: LinearLayout
    private lateinit var etPauseStart: EditText
    private lateinit var etPauseEnd: EditText
    private lateinit var cbPauseCall: AppCompatCheckBox
    private lateinit var cbPauseHangup: AppCompatCheckBox
    private lateinit var cbPauseAnswer: AppCompatCheckBox
    private lateinit var etMinSuccessDuration: EditText

    private var countDownTimer: CountDownTimer? = null 

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 100
    }
    
    private val mainReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AutoClickService.ACTION_NEXT_CALL_SCHEDULED -> {
                    val delay = intent.getLongExtra(AutoClickService.EXTRA_DELAY_MILLIS, 0)
                    startCountdown(delay)
                }
                CallStateReceiver.ACTION_STATS_UPDATED -> { updateStats() }
                AutomationService.ACTION_DATA_UPDATE -> { updateStats() }
            }
        }
    }
    
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) { result.data?.data?.let { uri -> saveSettingsToFile(uri) } }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) { result.data?.data?.let { uri -> restoreSettingsFromFile(uri) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            setSupportActionBar(findViewById(R.id.toolbar))
            initViews()
            loadPrefs()
            setupListeners() 
            checkPermissions()
            val filter = IntentFilter().apply { 
                addAction(AutoClickService.ACTION_NEXT_CALL_SCHEDULED)
                addAction(CallStateReceiver.ACTION_STATS_UPDATED)
                addAction(AutomationService.ACTION_DATA_UPDATE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { registerReceiver(mainReceiver, filter, Context.RECEIVER_EXPORTED) } else { registerReceiver(mainReceiver, filter) }
        } catch (e: Exception) { Log.e("MainActivity", "Error in onCreate", e); Toast.makeText(this, "앱 초기화 오류: ${e.message}", Toast.LENGTH_LONG).show() }
    }
    
    override fun onDestroy() { super.onDestroy(); try { unregisterReceiver(mainReceiver); countDownTimer?.cancel() } catch (e: Exception) {} }
    override fun onResume() {
        super.onResume()
        Prefs.checkAndResetMonthlyStats(this)
        updateStats()
        updateNumberCount()
        checkAlarmPermission()
        updateCountdownFromPrefs()
    }

    private fun updateCountdownFromPrefs() {
        if (!Prefs.isAutoCallEnabled(this)) {
            startCountdown(0)
            return
        }
        val nextCallTimestamp = Prefs.getNextCallTimestamp(this)
        if (nextCallTimestamp > System.currentTimeMillis()) {
            val delay = nextCallTimestamp - System.currentTimeMillis()
            startCountdown(delay)
        } else if (nextCallTimestamp == 0L && tvIntervalLabel.text.contains("다음 발신까지")) {
            // A call might have just finished, and the next one isn't scheduled yet.
            // Reset to default text if it's showing a countdown.
            startCountdown(0)
        }
        // If timestamp is in the past but not 0, a call is likely overdue. 
        // The service will handle it, so we don't need to change the UI,
        // which might be showing "Calling...".
    }

    
    private fun checkAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                btnSetAlarm.visibility = View.VISIBLE
            } else {
                btnSetAlarm.visibility = View.GONE
            }
        } else {
            btnSetAlarm.visibility = View.GONE
        }
    }
    
    private fun saveSettingsToFile(uri: Uri) {
        try { val json = Prefs.exportToJson(this); contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }; Toast.makeText(this, "설정 저장 완료", Toast.LENGTH_SHORT).show() } catch (e: Exception) { Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show() }
    }
    
    private fun restoreSettingsFromFile(uri: Uri) {
        try { val sb = StringBuilder(); contentResolver.openInputStream(uri)?.use { inputStream -> BufferedReader(InputStreamReader(inputStream)).use { reader -> var line: String? = reader.readLine(); while (line != null) { sb.append(line); line = reader.readLine() } } }; Prefs.importFromJson(this, sb.toString()); loadPrefs(); Toast.makeText(this, "설정 복원 완료", Toast.LENGTH_SHORT).show(); if (switchAutoCall.isChecked) startAutoClickService(AutoClickService.ACTION_UPDATE_CONFIG) } catch (e: Exception) { Toast.makeText(this, "복원 실패: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    private fun initViews() {
        switchAutoCall = findViewById(R.id.switchAutoCall); etIntervalMin = findViewById(R.id.etIntervalMin); etIntervalMax = findViewById(R.id.etIntervalMax); switchAutoHangup = findViewById(R.id.switchAutoHangup); etHangupMin = findViewById(R.id.etHangupMin); etHangupMax = findViewById(R.id.etHangupMax); etNoAnswerTimeout = findViewById(R.id.etNoAnswerTimeout); switchAutoAnswer = findViewById(R.id.switchAutoAnswer); btnSetAccessibility = findViewById(R.id.btnSetAccessibility); btnSetBattery = findViewById(R.id.btnSetBattery); btnSetAlarm = findViewById(R.id.btnSetAlarm)
        val btnSetOverlay = findViewById<Button>(R.id.btnSetOverlay)
        btnBackupRestore = findViewById(R.id.btnBackupRestore)
        tvCallCount = findViewById(R.id.tvCallCount); tvCallDuration = findViewById(R.id.tvCallDuration); tvDataUsage = findViewById(R.id.tvDataUsage)
        pbCallCount = findViewById(R.id.pbCallCount); pbCallDuration = findViewById(R.id.pbCallDuration); pbDataUsage = findViewById(R.id.pbDataUsage)
        tvIntervalLabel = findViewById(R.id.tvIntervalLabel); btnManageNumbers = findViewById(R.id.btnManageNumbers); tvNumberCount = findViewById(R.id.tvNumberCount); etGoalCount = findViewById(R.id.etGoalCount); etGoalDuration = findViewById(R.id.etGoalDuration); etGoalData = findViewById(R.id.etGoalData); switchAutoData = findViewById(R.id.switchAutoData); cbDataTurbo = findViewById(R.id.cbDataTurbo)
        layoutWeekDays = findViewById(R.id.layoutWeekDays); etPauseStart = findViewById(R.id.etPauseStart); etPauseEnd = findViewById(R.id.etPauseEnd); cbPauseCall = findViewById(R.id.cbPauseCall); cbPauseHangup = findViewById(R.id.cbPauseHangup); cbPauseAnswer = findViewById(R.id.cbPauseAnswer); etMinSuccessDuration = findViewById(R.id.etMinSuccessDuration)
    }

    private fun loadPrefs() {
        switchAutoCall.isChecked = Prefs.isAutoCallEnabled(this); updateNumberCount(); val cI = Prefs.getCallInterval(this); etIntervalMin.setText(cI.first.toString()); etIntervalMax.setText(cI.second.toString()); switchAutoHangup.isChecked = Prefs.isAutoHangupEnabled(this); val hI = Prefs.getHangupInterval(this); etHangupMin.setText(hI.first.toString()); etHangupMax.setText(hI.second.toString()); etNoAnswerTimeout.setText(Prefs.getNoAnswerTimeout(this).toString()); switchAutoAnswer.isChecked = Prefs.isAutoAnswerEnabled(this); switchAutoData.isChecked = Prefs.isAutoDataEnabled(this); cbDataTurbo.isChecked = Prefs.isDataTurboEnabled(this)
        etGoalCount.setText(Prefs.getGoalCount(this).let { if(it==0) "" else it.toString() }); etGoalDuration.setText(Prefs.getGoalDuration(this).let { if(it==0) "" else it.toString() }); etGoalData.setText(Prefs.getGoalData(this).let { if(it==0) "" else it.toString() })
        val days = Prefs.getCallDays(this); for (i in 0 until layoutWeekDays.childCount) { val v = layoutWeekDays.getChildAt(i); if (v is AppCompatCheckBox) { val t = v.tag?.toString(); if (t != null) v.isChecked = days.contains(t) } }
        val pT = Prefs.getPauseTime(this); etPauseStart.setText(pT.first); etPauseEnd.setText(pT.second); val f = Prefs.getPauseFeatures(this); cbPauseCall.isChecked = f.contains("CALL"); cbPauseHangup.isChecked = f.contains("HANGUP"); cbPauseAnswer.isChecked = f.contains("ANSWER"); updateStats()
        etMinSuccessDuration.setText(Prefs.getMinSuccessDuration(this).toString())
        if (switchAutoData.isChecked) startAutomationService(AutomationService.ACTION_START)
    }
    
    private fun updateNumberCount() { val count = Prefs.getPhoneNumbers(this).size; tvNumberCount.text = "(${count}개)" }
    private fun updateStats() { 
        val c = Prefs.getCallCount(this); val d = Prefs.getCallDuration(this); val data = Prefs.getDataUsage(this)
        val gC = Prefs.getGoalCount(this); val gD = Prefs.getGoalDuration(this); val gData = Prefs.getGoalData(this)
        
        val cT = if (gC > 0) "$c / $gC" else "$c"; tvCallCount.text = "발신콜수 : $cT"
        pbCallCount.max = if (gC > 0) gC else 100; pbCallCount.progress = c

        val dH = Utils.formatTime(d * 1000); val gDT = if (gD > 0) " / ${gD}분" else ""; tvCallDuration.text = "발신시간 : $dH$gDT"
        pbCallDuration.max = if (gD > 0) gD * 60 else 3600; pbCallDuration.progress = d.toInt()

        val dataT = if (gData > 0) String.format("%.2f / %d MB", data, gData) else String.format("%.2f MB", data); tvDataUsage.text = "데이터 사용 : $dataT"
        pbDataUsage.max = if (gData > 0) gData * 100 else 10000; pbDataUsage.progress = (data * 100).toInt()
    }
    
    private fun startCountdown(millis: Long) {
        countDownTimer?.cancel(); if (millis == -1L) { tvIntervalLabel.text = "대기 중 (오늘 요일 제외됨)"; tvIntervalLabel.setTextColor(getColor(android.R.color.holo_orange_dark)); return }; if (millis == -2L) { tvIntervalLabel.text = "일시중지 시간대 (대기 중)"; tvIntervalLabel.setTextColor(getColor(android.R.color.holo_orange_dark)); return }; if (millis == -3L) { tvIntervalLabel.text = "통화 중 (발신 일시정지)"; tvIntervalLabel.setTextColor(getColor(android.R.color.holo_green_dark)); return }; if (millis <= 0) { tvIntervalLabel.text = "걸기간격 설정 (초)"; return }
        countDownTimer = object : CountDownTimer(millis, 1000) { override fun onTick(m: Long) { val s = m / 1000; tvIntervalLabel.text = "다음 발신까지: ${s}초"; tvIntervalLabel.setTextColor(getColor(android.R.color.holo_red_dark)) }; override fun onFinish() { tvIntervalLabel.text = "발신 중..."; tvIntervalLabel.setTextColor(getColor(android.R.color.black)) } }.start()
    }

    private fun savePrefs() {
        Prefs.setCallInterval(this, etIntervalMin.text.toString().toIntOrNull() ?: 17, etIntervalMax.text.toString().toIntOrNull() ?: 30); Prefs.setHangupInterval(this, etHangupMin.text.toString().toIntOrNull() ?: 7, etHangupMax.text.toString().toIntOrNull() ?: 10); Prefs.setNoAnswerTimeout(this, etNoAnswerTimeout.text.toString().toIntOrNull() ?: 30); Prefs.setGoalCount(this, etGoalCount.text.toString().toIntOrNull() ?: 0); Prefs.setGoalDuration(this, etGoalDuration.text.toString().toIntOrNull() ?: 0); Prefs.setGoalData(this, etGoalData.text.toString().toIntOrNull() ?: 0)
        Prefs.setMinSuccessDuration(this, etMinSuccessDuration.text.toString().toIntOrNull() ?: 5)
        val d = mutableSetOf<String>(); for (i in 0 until layoutWeekDays.childCount) { val v = layoutWeekDays.getChildAt(i); if (v is AppCompatCheckBox) { val t = v.tag?.toString(); if (t != null && v.isChecked) d.add(t) } }; Prefs.setCallDays(this, d); Prefs.setPauseTime(this, etPauseStart.text.toString(), etPauseEnd.text.toString()); val f = mutableSetOf<String>(); if (cbPauseCall.isChecked) f.add("CALL"); if (cbPauseHangup.isChecked) f.add("HANGUP"); if (cbPauseAnswer.isChecked) f.add("ANSWER"); Prefs.setPauseFeatures(this, f); if (switchAutoCall.isChecked) startAutoClickService(AutoClickService.ACTION_UPDATE_CONFIG)
    }

    private fun setupListeners() {
        btnManageNumbers.setOnClickListener { startActivity(Intent(this, ManageNumbersActivity::class.java)) }
        val tW = object : TextWatcher { override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}; override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}; override fun afterTextChanged(s: Editable?) { savePrefs(); updateStats() } }
        etIntervalMin.addTextChangedListener(tW); etIntervalMax.addTextChangedListener(tW); etHangupMin.addTextChangedListener(tW); etHangupMax.addTextChangedListener(tW); etNoAnswerTimeout.addTextChangedListener(tW); etPauseStart.addTextChangedListener(tW); etPauseEnd.addTextChangedListener(tW); etGoalCount.addTextChangedListener(tW); etGoalDuration.addTextChangedListener(tW); etGoalData.addTextChangedListener(tW); etMinSuccessDuration.addTextChangedListener(tW)
        val dL = { _: android.widget.CompoundButton, _: Boolean -> savePrefs() }
        for (i in 0 until layoutWeekDays.childCount) { val v = layoutWeekDays.getChildAt(i); if (v is AppCompatCheckBox) v.setOnCheckedChangeListener(dL) }; cbPauseCall.setOnCheckedChangeListener(dL); cbPauseHangup.setOnCheckedChangeListener(dL); cbPauseAnswer.setOnCheckedChangeListener(dL)
        switchAutoCall.setOnCheckedChangeListener { _, iC -> Prefs.setAutoCallEnabled(this, iC); if (iC) { if (Prefs.getPhoneNumbers(this).isEmpty()) { Toast.makeText(this, "전화번호를 먼저 등록해주세요.", Toast.LENGTH_SHORT).show(); switchAutoCall.isChecked = false; return@setOnCheckedChangeListener }; if (!isAccessibilityServiceEnabled()) { Toast.makeText(this, "접근성 권한이 필요합니다.", Toast.LENGTH_LONG).show(); startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); switchAutoCall.isChecked = false; return@setOnCheckedChangeListener }; startAutoClickService(AutoClickService.ACTION_START_AUTO) } else { startAutoClickService(AutoClickService.ACTION_STOP_AUTO); Prefs.setNextCallTimestamp(this, 0L); countDownTimer?.cancel(); tvIntervalLabel.text = "걸기간격 설정 (초)"; tvIntervalLabel.setTextColor(getColor(android.R.color.black)) } }
        switchAutoHangup.setOnCheckedChangeListener { _, iC -> Prefs.setAutoHangupEnabled(this, iC) }
        switchAutoAnswer.setOnCheckedChangeListener { _, iC -> Prefs.setAutoAnswerEnabled(this, iC); val msg = if(iC) "자동 받기 켜짐" else "자동 받기 꺼짐"; Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
        switchAutoData.setOnCheckedChangeListener { _, iC -> Prefs.setAutoDataEnabled(this, iC); if (iC) startAutomationService(AutomationService.ACTION_START) else startAutomationService(AutomationService.ACTION_STOP) }
        cbDataTurbo.setOnCheckedChangeListener { _, iC -> Prefs.setDataTurboEnabled(this, iC) }
        btnSetAccessibility.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        btnSetBattery.setOnClickListener { requestIgnoreBatteryOptimization() }
        btnSetAlarm.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }
        btnBackupRestore.setOnClickListener { showBackupRestoreDialog() }
        findViewById<Button>(R.id.btnSetOverlay).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }
        findViewById<View>(R.id.btnResetStats).setOnClickListener { androidx.appcompat.app.AlertDialog.Builder(this).setTitle("통계 초기화").setMessage("이번 달 발신콜수와 발신시간, 데이터 사용량을 모두 초기화하시겠습니까?").setPositiveButton("초기화") { _, _ -> val ed = getSharedPreferences("duty_caller_prefs", Context.MODE_PRIVATE).edit(); ed.putInt("stats_call_count", 0); ed.putLong("stats_call_duration", 0L); ed.putFloat("stats_data_usage_mb", 0f); ed.apply(); updateStats(); Toast.makeText(this, "통계가 초기화되었습니다.", Toast.LENGTH_SHORT).show() }.setNegativeButton("취소", null).show() }
    }

    private fun showBackupRestoreDialog() {
        val options = arrayOf<CharSequence>("설정 내보내기 (백업)", "설정 가져오기 (복원)")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("설정 관리")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> { // Export
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/json"
                            putExtra(Intent.EXTRA_TITLE, "ssalmuk_settings.json")
                        }
                        exportLauncher.launch(intent)
                    }
                    1 -> { // Import
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/json"
                        }
                        importLauncher.launch(intent)
                    }
                }
            }
            .show()
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            } else {
                Toast.makeText(this, "이미 배터리 최적화에서 제외되어 있습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startAutoClickService(a: String) { val i = Intent(this, AutoClickService::class.java); i.action = a; startService(i) }
    private fun startAutomationService(a: String) { val i = Intent(this, AutomationService::class.java); i.action = a; startService(i) }
    private fun checkPermissions() { val p = mutableListOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.POST_NOTIFICATIONS); val m = p.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }; if (m.isNotEmpty()) ActivityCompat.requestPermissions(this, m.toTypedArray(), REQUEST_CODE_PERMISSIONS) }
    private fun isAccessibilityServiceEnabled(): Boolean { val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES); return s?.contains("$packageName/${AutoClickService::class.java.name}") ?: false }
}
