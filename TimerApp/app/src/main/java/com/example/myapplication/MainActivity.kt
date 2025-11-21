package com.example.myapplication

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TimerAdapter
    private lateinit var fab: FloatingActionButton
    
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
        
        recyclerView = findViewById(R.id.recyclerView)
        fab = findViewById(R.id.fabAddTimer)
        
        // Load saved timers
        timers.addAll(TimerStorage.loadTimers(this))
        
        adapter = TimerAdapter(
            timers,
            onStartPause = { timer -> toggleTimer(timer) },
            onReset = { timer -> resetTimer(timer) },
            onDelete = { timer -> deleteTimer(timer) }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        fab.setOnClickListener {
            showAddTimerDialog()
        }
        
        handler.post(updateRunnable)
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add -> {
                showAddTimerDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showAddTimerDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_timer, null)
        val timerInput = dialogView.findViewById<EditText>(R.id.timerInput)
        val timerName = dialogView.findViewById<EditText>(R.id.timerName)
        val previewText = dialogView.findViewById<TextView>(R.id.previewText)
        
        timerInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = timerInput.text.toString()
                if (input.isEmpty()) {
                    previewText.text = "Format: 5d3h2m30s"
                } else {
                    previewText.text = "Preview: ${TimeParser.formatInputPreview(input)}"
                }
            }
        })
        
        AlertDialog.Builder(this)
            .setTitle("Add Timer")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                addTimer(timerName.text.toString(), timerInput.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun addTimer(name: String, input: String) {
        val timerName = name.ifEmpty { "Timer ${timers.size + 1}" }
        val millis = TimeParser.parseTimeInput(input)
        
        if (millis <= 0) {
            Toast.makeText(this, "Invalid time format", Toast.LENGTH_SHORT).show()
            return
        }
        
        val timer = Timer(name = timerName, totalMillis = millis)
        timers.add(0, timer)
        adapter.notifyItemInserted(0)
        recyclerView.smoothScrollToPosition(0)
        saveTimers()
        
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
        saveTimers()
    }
    
    private fun resetTimer(timer: Timer) {
        timer.remainingMillis = timer.totalMillis
        timer.isRunning = false
        timer.isPaused = false
        adapter.updateTimer(timer)
        saveTimers()
    }
    
    private fun deleteTimer(timer: Timer) {
        val index = timers.indexOf(timer)
        if (index != -1) {
            timers.removeAt(index)
            adapter.notifyItemRemoved(index)
            saveTimers()
        }
    }
    
    private fun saveTimers() {
        TimerStorage.saveTimers(this, timers)
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
    
    override fun onPause() {
        super.onPause()
        saveTimers()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }
}
