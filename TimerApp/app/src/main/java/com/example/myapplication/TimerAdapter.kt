package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TimerAdapter(
    private val timers: MutableList<Timer>,
    private val onStartPause: (Timer) -> Unit,
    private val onReset: (Timer) -> Unit,
    private val onDelete: (Timer) -> Unit
) : RecyclerView.Adapter<TimerAdapter.TimerViewHolder>() {

    class TimerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.timerName)
        val timeText: TextView = view.findViewById(R.id.timerTime)
        val startPauseButton: Button = view.findViewById(R.id.btnStartPause)
        val resetButton: Button = view.findViewById(R.id.btnReset)
        val deleteButton: Button = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timer, parent, false)
        return TimerViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimerViewHolder, position: Int) {
        val timer = timers[position]
        
        holder.nameText.text = timer.name
        holder.timeText.text = timer.getFormattedTime()
        
        if (timer.isFinished()) {
            holder.timeText.text = "FINISHED!"
            holder.startPauseButton.isEnabled = false
        } else {
            holder.startPauseButton.isEnabled = true
        }
        
        holder.startPauseButton.text = when {
            timer.isRunning -> "Pause"
            timer.isPaused || timer.pausedAtMillis < timer.totalMillis -> "Resume"
            else -> "Start"
        }
        
        holder.startPauseButton.setOnClickListener { onStartPause(timer) }
        holder.resetButton.setOnClickListener { onReset(timer) }
        holder.deleteButton.setOnClickListener { onDelete(timer) }
    }

    override fun getItemCount() = timers.size
    
    fun updateTimer(timer: Timer) {
        val index = timers.indexOfFirst { it.id == timer.id }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }
}
