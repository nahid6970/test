package com.example.myapplication

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var timerInput: EditText
    private lateinit var timerName: EditText
    private lateinit var previewText: TextView
    private lateinit var addButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TimerAdapter
    
    private val timers = mutableListOf<Timer>()
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateTimers()
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        timerInput = findViewById(R.id.timerInput)
        timerName = findViewById(R.id.timerName)
        previewText = findViewById(R.id.previewText)
        addButton = findViewById(R.id.btnAddTimer)
        recyclerView = findViewById(R.id.recyclerView)
        
        adapter = TimerAdapter(
            timers,
            onStartPause = { timer -> toggleTimer(timer) },
            onReset = { timer -> resetTimer(timer) },
            onDelete = { timer -> deleteTimer(timer) }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        timerInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updatePreview()
            }
        })
        
        addButton.setOnClickListener {
            addTimer()
        }
        
        handler.post(updateRunnable)
    }
    
    private fun updatePreview() {
        val input = timerInput.text.toString()
        if (input.isEmpty()) {
            previewText.text = "Format: 5d3h2m30s (days, hours, minutes, seconds)"
        } else {
            previewText.text = "Preview: ${TimeParser.formatInputPreview(input)}"
        }
    }
    
    private fun addTimer() {
        val input = timerInput.text.toString()
        val name = timerName.text.toString().ifEmpty { "Timer ${timers.size + 1}" }
        val millis = TimeParser.parseTimeInput(input)
        
        if (millis <= 0) {
            Toast.makeText(this, "Invalid time format", Toast.LENGTH_SHORT).show()
            return
        }
        
        val timer = Timer(name = name, totalMillis = millis)
        timers.add(0, timer)
        adapter.notifyItemInserted(0)
        recyclerView.smoothScrollToPosition(0)
        
        timerInput.text.clear()
        timerName.text.clear()
        Toast.makeText(this, "Timer added", Toast.LENGTH_SHORT).show()
    }
    
    private fun toggleTimer(timer: Timer) {
        if (timer.isRunning) {
            timer.isRunning = false
            timer.isPaused = true
        } else {
            timer.isRunning = true
            timer.isPaused = false
        }
        adapter.updateTimer(timer)
    }
    
    private fun resetTimer(timer: Timer) {
        timer.remainingMillis = timer.totalMillis
        timer.isRunning = false
        timer.isPaused = false
        adapter.updateTimer(timer)
    }
    
    private fun deleteTimer(timer: Timer) {
        val index = timers.indexOf(timer)
        if (index != -1) {
            timers.removeAt(index)
            adapter.notifyItemRemoved(index)
        }
    }
    
    private fun updateTimers() {
        timers.forEach { timer ->
            if (timer.isRunning && !timer.isFinished()) {
                timer.remainingMillis -= 100
                if (timer.remainingMillis <= 0) {
                    timer.remainingMillis = 0
                    timer.isRunning = false
                    Toast.makeText(this, "${timer.name} finished!", Toast.LENGTH_SHORT).show()
                }
                adapter.updateTimer(timer)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }
}
