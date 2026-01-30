package com.example.dutycaller

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ManageNumbersActivity : AppCompatActivity() {

    private lateinit var etNewNumber: EditText
    private lateinit var btnAddNumber: Button
    private lateinit var rvNumbers: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnEditRaw: Button
    
    private val numbersList = mutableListOf<String>()
    private lateinit var adapter: NumbersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_numbers)
        
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        initViews()
        loadNumbers()
        setupListeners()
    }

    private fun initViews() {
        etNewNumber = findViewById(R.id.etNewNumber)
        btnAddNumber = findViewById(R.id.btnAddNumber)
        rvNumbers = findViewById(R.id.rvNumbers)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnEditRaw = findViewById(R.id.btnEditRaw)
        
        rvNumbers.layoutManager = LinearLayoutManager(this)
        adapter = NumbersAdapter()
        rvNumbers.adapter = adapter
    }

    private fun loadNumbers() {
        numbersList.clear()
        numbersList.addAll(Prefs.getPhoneNumbers(this))
        updateUI()
    }

    private fun saveNumbers() {
        val raw = numbersList.joinToString("\n")
        Prefs.setPhoneNumbers(this, raw)
        // Notify Service
        if (Prefs.isAutoCallEnabled(this)) {
            val intent = Intent(this, AutoClickService::class.java)
            intent.action = AutoClickService.ACTION_UPDATE_CONFIG
            startService(intent)
        }
    }

    private fun updateUI() {
        if (numbersList.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvNumbers.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvNumbers.visibility = View.VISIBLE
        }
        adapter.notifyDataSetChanged()
    }

    private fun setupListeners() {
        btnAddNumber.setOnClickListener {
            val num = etNewNumber.text.toString().trim()
            if (num.isNotEmpty()) {
                numbersList.add(0, num)
                etNewNumber.text.clear()
                saveNumbers()
                updateUI()
            }
        }

        btnEditRaw.setOnClickListener {
            showRawEditDialog()
        }
    }

    private fun showRawEditDialog() {
        val et = EditText(this)
        et.setText(numbersList.joinToString("\n"))
        et.setPadding(40, 40, 40, 40)
        
        AlertDialog.Builder(this)
            .setTitle("텍스트로 일괄 편집")
            .setMessage("한 줄에 번호 하나씩 입력하세요.")
            .setView(et)
            .setPositiveButton("저장") { _, _ ->
                val raw = et.text.toString()
                Prefs.setPhoneNumbers(this, raw)
                loadNumbers()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    inner class NumbersAdapter : RecyclerView.Adapter<NumbersAdapter.Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_number, parent, false)
            return Holder(v)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val num = numbersList[position]
            holder.tvNumber.text = num
            holder.btnDelete.setOnClickListener {
                numbersList.removeAt(position)
                saveNumbers()
                updateUI()
            }
        }
        override fun getItemCount() = numbersList.size

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val tvNumber: TextView = v.findViewById(R.id.tvNumber)
            val btnDelete: ImageButton = v.findViewById(R.id.btnDelete)
        }
    }
}
