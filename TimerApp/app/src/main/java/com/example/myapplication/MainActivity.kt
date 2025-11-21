package com.example.myapplication

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
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
            handler.postDelayed(this, 500)  // Update every 500ms instead of 100ms
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide action bar if it exists
        supportActionBar?.hide()
        
        setContentView(R.layout.activity_main)
        
        recyclerView = findViewById(R.id.recyclerView)
        fab = findViewById(R.id.fabAddTimer)
        
        // Load saved timers
        timers.addAll(TimerStorage.loadTimers(this))
        
        adapter = TimerAdapter(
            timers,
            onEdit = { timer -> showEditTimerDialog(timer) },
            onDelete = { timer -> showDeleteConfirmation(timer) }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(20)
        recyclerView.adapter = adapter
        
        fab.setOnClickListener {
            showAddTimerDialog()
        }
        
        handler.post(updateRunnable)
    }
    
    private fun showAddTimerDialog() {
        showTimerDialog(null)
    }
    
    private fun showEditTimerDialog(timer: Timer) {
        showTimerDialog(timer)
    }
    
    private fun showTimerDialog(existingTimer: Timer?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_timer, null)
        val timerInput = dialogView.findViewById<EditText>(R.id.timerInput)
        val timerName = dialogView.findViewById<EditText>(R.id.timerName)
        val previewText = dialogView.findViewById<TextView>(R.id.previewText)
        
        // Pre-fill name only if editing, leave time input empty
        existingTimer?.let {
            timerName.setText(it.name)
            // Leave timerInput empty so user can enter fresh time
        }
        
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
        
        val title = if (existingTimer == null) "Add Timer" else "Edit Timer"
        val buttonText = if (existingTimer == null) "Add" else "Save"
        
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(buttonText) { _, _ ->
                if (existingTimer == null) {
                    addTimer(timerName.text.toString(), timerInput.text.toString())
                } else {
                    editTimer(existingTimer, timerName.text.toString(), timerInput.text.toString())
                }
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
        timer.start()  // Auto-start the timer
        timers.add(0, timer)
        adapter.notifyItemInserted(0)
        recyclerView.smoothScrollToPosition(0)
        saveTimers()
        
        Toast.makeText(this, "Timer started", Toast.LENGTH_SHORT).show()
    }
    
    private fun editTimer(timer: Timer, name: String, input: String) {
        val timerName = name.ifEmpty { timer.name }
        val millis = TimeParser.parseTimeInput(input)
        
        if (millis <= 0) {
            Toast.makeText(this, "Invalid time format", Toast.LENGTH_SHORT).show()
            return
        }
        
        val index = timers.indexOf(timer)
        if (index != -1) {
            val newTimer = Timer(id = timer.id, name = timerName, totalMillis = millis)
            newTimer.start()  // Auto-start after edit
            timers[index] = newTimer
            adapter.notifyItemChanged(index)
            saveTimers()
            Toast.makeText(this, "Timer updated", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showDeleteConfirmation(timer: Timer) {
        val passwordInput = EditText(this)
        passwordInput.hint = "Enter password"
        passwordInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER or 
                                   android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        
        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = resources.getDimensionPixelSize(R.dimen.dialog_margin)
        params.rightMargin = resources.getDimensionPixelSize(R.dimen.dialog_margin)
        passwordInput.layoutParams = params
        container.addView(passwordInput)
        
        AlertDialog.Builder(this)
            .setTitle("Delete Timer")
            .setMessage("Enter password to delete \"${timer.name}\"")
            .setView(container)
            .setPositiveButton("Delete") { _, _ ->
                val enteredPassword = passwordInput.text.toString()
                if (enteredPassword == "1823") {
                    deleteTimer(timer)
                    Toast.makeText(this, "Timer deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        var hasFinished = false
        timers.forEach { timer ->
            if (timer.isRunning) {
                if (timer.isFinished()) {
                    timer.pause()
                    if (!hasFinished) {
                        Toast.makeText(this, "${timer.name} finished!", Toast.LENGTH_SHORT).show()
                        hasFinished = true
                    }
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
