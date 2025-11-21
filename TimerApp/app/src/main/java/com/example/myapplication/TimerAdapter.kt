package com.example.myapplication

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class TimerAdapter(
    private val timers: MutableList<Timer>,
    private val onEdit: (Timer) -> Unit,
    private val onSetClock: (Timer) -> Unit,
    private val onDelete: (Timer) -> Unit
) : RecyclerView.Adapter<TimerAdapter.TimerViewHolder>() {

    init {
        setHasStableIds(true)
    }

    class TimerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: CardView = view.findViewById(R.id.timerCard)
        val nameText: TextView = view.findViewById(R.id.timerName)
        val timeText: TextView = view.findViewById(R.id.timerTime)
        val editButton: Button = view.findViewById(R.id.btnEdit)
        val setClockButton: Button = view.findViewById(R.id.btnSetClock)
        val deleteButton: Button = view.findViewById(R.id.btnDelete)
        
        var currentTimer: Timer? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timer, parent, false)
        return TimerViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimerViewHolder, position: Int) {
        val timer = timers[position]
        holder.currentTimer = timer
        
        holder.nameText.text = timer.name
        updateTimerDisplay(holder, timer)
        
        holder.editButton.setOnClickListener { onEdit(timer) }
        holder.setClockButton.setOnClickListener { onSetClock(timer) }
        holder.deleteButton.setOnClickListener { onDelete(timer) }
    }
    
    override fun onBindViewHolder(holder: TimerViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // Partial update - only update the time display
            val timer = timers[position]
            updateTimerDisplay(holder, timer)
        }
    }
    
    private fun updateTimerDisplay(holder: TimerViewHolder, timer: Timer) {
        if (timer.isFinished()) {
            holder.timeText.text = "FINISHED!"
            holder.cardView.setCardBackgroundColor(Color.parseColor("#4CAF50"))  // Green
            holder.nameText.setTextColor(Color.WHITE)
            holder.timeText.setTextColor(Color.WHITE)
        } else {
            holder.timeText.text = timer.getFormattedTime()
            holder.cardView.setCardBackgroundColor(Color.WHITE)
            holder.nameText.setTextColor(Color.parseColor("#000000"))
            holder.timeText.setTextColor(Color.parseColor("#000000"))
        }
    }

    override fun getItemCount() = timers.size
    
    override fun getItemId(position: Int): Long = timers[position].id
    
    fun updateTimer(timer: Timer) {
        val index = timers.indexOfFirst { it.id == timer.id }
        if (index != -1) {
            notifyItemChanged(index, "UPDATE_TIME")
        }
    }
}
