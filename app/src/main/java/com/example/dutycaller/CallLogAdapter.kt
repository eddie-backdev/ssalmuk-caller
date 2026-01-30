package com.example.dutycaller

import android.provider.CallLog
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Date

data class CallLogItem(
    val number: String,
    val date: Long,
    val duration: Long,
    val type: Int
)

class CallLogAdapter(private val items: List<CallLogItem>) : RecyclerView.Adapter<CallLogAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivType: ImageView = view.findViewById(R.id.ivType)
        val tvNumber: TextView = view.findViewById(R.id.tvLogNumber)
        val tvDate: TextView = view.findViewById(R.id.tvLogDate)
        val tvDuration: TextView = view.findViewById(R.id.tvLogDuration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_call_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        holder.tvNumber.text = item.number
        holder.tvDate.text = DateFormat.format("yyyy-MM-dd HH:mm", Date(item.date))
        holder.tvDuration.text = Utils.formatTime(item.duration * 1000)

        when (item.type) {
            CallLog.Calls.INCOMING_TYPE -> {
                holder.ivType.setImageResource(android.R.drawable.sym_call_incoming)
                holder.ivType.setColorFilter(0xFF2196F3.toInt()) // Blue
            }
            CallLog.Calls.OUTGOING_TYPE -> {
                holder.ivType.setImageResource(android.R.drawable.sym_call_outgoing)
                holder.ivType.setColorFilter(0xFF4CAF50.toInt()) // Green
            }
            CallLog.Calls.MISSED_TYPE -> {
                holder.ivType.setImageResource(android.R.drawable.sym_call_missed)
                holder.ivType.setColorFilter(0xFFF44336.toInt()) // Red
            }
            else -> {
                holder.ivType.setImageResource(android.R.drawable.sym_call_incoming)
                holder.ivType.clearColorFilter()
            }
        }
    }

    override fun getItemCount() = items.size
}
