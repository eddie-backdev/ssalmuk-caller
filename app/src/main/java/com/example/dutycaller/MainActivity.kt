package com.example.dutycaller

import android.Manifest
import android.app.Activity
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
    private lateinit var tvCallCount: TextView
    private lateinit var tvCallDuration: TextView
    private lateinit var tvIntervalLabel: TextView 
    private lateinit var btnManageNumbers: Button
    private lateinit var tvNumberCount: TextView
    private lateinit var etGoalCount: EditText
    private lateinit var etGoalDuration: EditText
    private lateinit var layoutWeekDays: LinearLayout
    private lateinit var etPauseStart: EditText
    private lateinit var etPauseEnd: EditText
    private lateinit var cbPauseCall: AppCompatCheckBox
    private lateinit var cbPauseHangup: AppCompatCheckBox
    private lateinit var cbPauseAnswer: AppCompatCheckBox

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
            val filter = IntentFilter().apply { addAction(AutoClickService.ACTION_NEXT_CALL_SCHEDULED); addAction(CallStateReceiver.ACTION_STATS_UPDATED) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { registerReceiver(mainReceiver, filter, Context.RECEIVER_EXPORTED) } else { registerReceiver(mainReceiver, filter) }
        } catch (e: Exception) { Log.e("MainActivity", "Error in onCreate", e); Toast.makeText(this, "앱 초기화 오류: ${e.message}", Toast.LENGTH_LONG).show() }
    }
    
    override fun onDestroy() { super.onDestroy(); try { unregisterReceiver(mainReceiver); countDownTimer?.cancel() } catch (e: Exception) {} }
    override fun onResume() { super.onResume(); Prefs.checkAndResetMonthlyStats(this); updateStats(); updateNumberCount() }
    override fun onCreateOptionsMenu(menu: Menu): Boolean { menuInflater.inflate(R.menu.menu_main, menu); return true }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export -> { val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"; putExtra(Intent.EXTRA_TITLE, "ssalmuk_settings.json") }; exportLauncher.launch(intent); true }
            R.id.action_import -> { val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json" }; importLauncher.launch(intent); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun saveSettingsToFile(uri: Uri) {
        try { val json = Prefs.exportToJson(this); contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }; Toast.makeText(this, "설정 저장 완료", Toast.LENGTH_SHORT).show() } catch (e: Exception) { Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show() }
    }
    
    private fun restoreSettingsFromFile(uri: Uri) {
        try { val sb = StringBuilder(); contentResolver.openInputStream(uri)?.use { inputStream -> BufferedReader(InputStreamReader(inputStream)).use { reader -> var line: String? = reader.readLine(); while (line != null) { sb.append(line); line = reader.readLine() } } }; Prefs.importFromJson(this, sb.toString()); loadPrefs(); Toast.makeText(this, "설정 복원 완료", Toast.LENGTH_SHORT).show(); if (switchAutoCall.isChecked) startAutoClickService(AutoClickService.ACTION_UPDATE_CONFIG) } catch (e: Exception) { Toast.makeText(this, "복원 실패: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    private fun initViews() {
        switchAutoCall = findViewById(R.id.switchAutoCall); etIntervalMin = findViewById(R.id.etIntervalMin); etIntervalMax = findViewById(R.id.etIntervalMax); switchAutoHangup = findViewById(R.id.switchAutoHangup); etHangupMin = findViewById(R.id.etHangupMin); etHangupMax = findViewById(R.id.etHangupMax); etNoAnswerTimeout = findViewById(R.id.etNoAnswerTimeout); switchAutoAnswer = findViewById(R.id.switchAutoAnswer); btnSetAccessibility = findViewById(R.id.btnSetAccessibility); btnSetBattery = findViewById(R.id.btnSetBattery)
        val btnSetOverlay = findViewById<Button>(R.id.btnSetOverlay) // Added
        tvCallCount = findViewById(R.id.tvCallCount); tvCallDuration = findViewById(R.id.tvCallDuration); tvIntervalLabel = findViewById(R.id.tvIntervalLabel); btnManageNumbers = findViewById(R.id.btnManageNumbers); tvNumberCount = findViewById(R.id.tvNumberCount); etGoalCount = findViewById(R.id.etGoalCount); etGoalDuration = findViewById(R.id.etGoalDuration); layoutWeekDays = findViewById(R.id.layoutWeekDays); etPauseStart = findViewById(R.id.etPauseStart); etPauseEnd = findViewById(R.id.etPauseEnd); cbPauseCall = findViewById(R.id.cbPauseCall); cbPauseHangup = findViewById(R.id.cbPauseHangup); cbPauseAnswer = findViewById(R.id.cbPauseAnswer)
    }

    private fun loadPrefs() {
        switchAutoCall.isChecked = Prefs.isAutoCallEnabled(this); updateNumberCount(); val cI = Prefs.getCallInterval(this); etIntervalMin.setText(cI.first.toString()); etIntervalMax.setText(cI.second.toString()); switchAutoHangup.isChecked = Prefs.isAutoHangupEnabled(this); val hI = Prefs.getHangupInterval(this); etHangupMin.setText(hI.first.toString()); etHangupMax.setText(hI.second.toString()); etNoAnswerTimeout.setText(Prefs.getNoAnswerTimeout(this).toString()); switchAutoAnswer.isChecked = Prefs.isAutoAnswerEnabled(this); etGoalCount.setText(Prefs.getGoalCount(this).let { if(it==0) "" else it.toString() }); etGoalDuration.setText(Prefs.getGoalDuration(this).let { if(it==0) "" else it.toString() })
        val days = Prefs.getCallDays(this); for (i in 0 until layoutWeekDays.childCount) { val v = layoutWeekDays.getChildAt(i); if (v is AppCompatCheckBox) { val t = v.tag?.toString(); if (t != null) v.isChecked = days.contains(t) } }
        val pT = Prefs.getPauseTime(this); etPauseStart.setText(pT.first); etPauseEnd.setText(pT.second); val f = Prefs.getPauseFeatures(this); cbPauseCall.isChecked = f.contains("CALL"); cbPauseHangup.isChecked = f.contains("HANGUP"); cbPauseAnswer.isChecked = f.contains("ANSWER"); updateStats()
    }
    
    private fun updateNumberCount() { val count = Prefs.getPhoneNumbers(this).size; tvNumberCount.text = "(${count}개)" }
    private fun updateStats() { val c = Prefs.getCallCount(this); val d = Prefs.getCallDuration(this); val gC = Prefs.getGoalCount(this); val gD = Prefs.getGoalDuration(this); val cT = if (gC > 0) "$c / $gC" else "$c"; tvCallCount.text = "발신콜수 : $cT"; val dH = Utils.formatTime(d * 1000); val gDT = if (gD > 0) " / ${gD}분" else ""; tvCallDuration.text = "발신시간 : $dH$gDT" }
    
    private fun startCountdown(millis: Long) {
        countDownTimer?.cancel(); if (millis == -1L) { tvIntervalLabel.text = "대기 중 (오늘 요일 제외됨)"; tvIntervalLabel.setTextColor(getColor(android.R.color.holo_orange_dark)); return }; if (millis == -2L) { tvIntervalLabel.text = "일시중지 시간대 (대기 중)"; tvIntervalLabel.setTextColor(getColor(android.R.color.holo_orange_dark)); return }; if (millis == -3L) { tvIntervalLabel.text = "통화 중 (발신 일시정지)"; tvIntervalLabel.setTextColor(getColor(android.R.color.holo_green_dark)); return }; if (millis <= 0) { tvIntervalLabel.text = "걸기간격 설정 (초)"; return }
        countDownTimer = object : CountDownTimer(millis, 1000) { override fun onTick(m: Long) { val s = m / 1000; tvIntervalLabel.text = "다음 발신까지: ${s}초"; tvIntervalLabel.setTextColor(getColor(android.R.color.holo_red_dark)) }; override fun onFinish() { tvIntervalLabel.text = "발신 중..."; tvIntervalLabel.setTextColor(getColor(android.R.color.black)) } }.start()
    }

    private fun savePrefs() {
        Prefs.setCallInterval(this, etIntervalMin.text.toString().toIntOrNull() ?: 17, etIntervalMax.text.toString().toIntOrNull() ?: 30); Prefs.setHangupInterval(this, etHangupMin.text.toString().toIntOrNull() ?: 7, etHangupMax.text.toString().toIntOrNull() ?: 10); Prefs.setNoAnswerTimeout(this, etNoAnswerTimeout.text.toString().toIntOrNull() ?: 30); Prefs.setGoalCount(this, etGoalCount.text.toString().toIntOrNull() ?: 0); Prefs.setGoalDuration(this, etGoalDuration.text.toString().toIntOrNull() ?: 0)
        val d = mutableSetOf<String>(); for (i in 0 until layoutWeekDays.childCount) { val v = layoutWeekDays.getChildAt(i); if (v is AppCompatCheckBox) { val t = v.tag?.toString(); if (t != null && v.isChecked) d.add(t) } }; Prefs.setCallDays(this, d); Prefs.setPauseTime(this, etPauseStart.text.toString(), etPauseEnd.text.toString()); val f = mutableSetOf<String>(); if (cbPauseCall.isChecked) f.add("CALL"); if (cbPauseHangup.isChecked) f.add("HANGUP"); if (cbPauseAnswer.isChecked) f.add("ANSWER"); Prefs.setPauseFeatures(this, f); if (switchAutoCall.isChecked) startAutoClickService(AutoClickService.ACTION_UPDATE_CONFIG)
    }

    private fun setupListeners() {
        btnManageNumbers.setOnClickListener { startActivity(Intent(this, ManageNumbersActivity::class.java)) }
        val tW = object : TextWatcher { override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}; override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}; override fun afterTextChanged(s: Editable?) { savePrefs() } }
        etIntervalMin.addTextChangedListener(tW); etIntervalMax.addTextChangedListener(tW); etHangupMin.addTextChangedListener(tW); etHangupMax.addTextChangedListener(tW); etNoAnswerTimeout.addTextChangedListener(tW); etPauseStart.addTextChangedListener(tW); etPauseEnd.addTextChangedListener(tW); etGoalCount.addTextChangedListener(tW); etGoalDuration.addTextChangedListener(tW)
        val dL = { _: android.widget.CompoundButton, _: Boolean -> savePrefs() }
        for (i in 0 until layoutWeekDays.childCount) { val v = layoutWeekDays.getChildAt(i); if (v is AppCompatCheckBox) v.setOnCheckedChangeListener(dL) }; cbPauseCall.setOnCheckedChangeListener(dL); cbPauseHangup.setOnCheckedChangeListener(dL); cbPauseAnswer.setOnCheckedChangeListener(dL)
        switchAutoCall.setOnCheckedChangeListener { _, iC -> Prefs.setAutoCallEnabled(this, iC); if (iC) { if (Prefs.getPhoneNumbers(this).isEmpty()) { Toast.makeText(this, "전화번호를 먼저 등록해주세요.", Toast.LENGTH_SHORT).show(); switchAutoCall.isChecked = false; return@setOnCheckedChangeListener }; if (!isAccessibilityServiceEnabled()) { Toast.makeText(this, "접근성 권한이 필요합니다.", Toast.LENGTH_LONG).show(); startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); switchAutoCall.isChecked = false; return@setOnCheckedChangeListener }; startAutoClickService(AutoClickService.ACTION_START_AUTO) } else { startAutoClickService(AutoClickService.ACTION_STOP_AUTO); countDownTimer?.cancel(); tvIntervalLabel.text = "걸기간격 설정 (초)"; tvIntervalLabel.setTextColor(getColor(android.R.color.black)) } }
        switchAutoHangup.setOnCheckedChangeListener { _, iC -> Prefs.setAutoHangupEnabled(this, iC) }
        switchAutoAnswer.setOnCheckedChangeListener { _, iC -> Prefs.setAutoAnswerEnabled(this, iC); val msg = if(iC) "자동 받기 켜짐" else "자동 받기 꺼짐"; Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
        btnSetAccessibility.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        btnSetBattery.setOnClickListener { requestIgnoreBatteryOptimization() }
        findViewById<Button>(R.id.btnSetOverlay).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }
        findViewById<View>(R.id.btnResetStats).setOnClickListener { androidx.appcompat.app.AlertDialog.Builder(this).setTitle("통계 초기화").setMessage("이번 달 발신콜수와 발신시간을 모두 초기화하시겠습니까?").setPositiveButton("초기화") { _, _ -> val ed = getSharedPreferences("duty_caller_prefs", Context.MODE_PRIVATE).edit(); ed.putInt("stats_call_count", 0); ed.putLong("stats_call_duration", 0L); ed.apply(); updateStats(); Toast.makeText(this, "통계가 초기화되었습니다.", Toast.LENGTH_SHORT).show() }.setNegativeButton("취소", null).show() }
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
    private fun checkPermissions() { val p = mutableListOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.POST_NOTIFICATIONS); val m = p.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }; if (m.isNotEmpty()) ActivityCompat.requestPermissions(this, m.toTypedArray(), REQUEST_CODE_PERMISSIONS) }
    private fun isAccessibilityServiceEnabled(): Boolean { val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES); return s?.contains("$packageName/${AutoClickService::class.java.name}") ?: false }
}
