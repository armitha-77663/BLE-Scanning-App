package com.example.blebeacon

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class BeaconAdapter : ListAdapter<BleBeacon, BeaconAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvName)
        val rssi: TextView = view.findViewById(R.id.tvRssi)
        val distance: TextView = view.findViewById(R.id.tvDistance)
        val meta: TextView = view.findViewById(R.id.tvMeta)
        val bar: ProgressBar = view.findViewById(R.id.progressBar)
        val indicator: View = view.findViewById(R.id.colorIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_beacon, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val b = getItem(position)
        holder.name.text = b.name
        holder.rssi.text = "${b.rssi} dBm"
        holder.distance.text = "${"%.2f".format(b.distance)} m"
        holder.meta.text = "${b.id.take(17)}...  ·  ${b.signalStrength.name.lowercase()}"
        holder.bar.progress = b.rssiBarPercent

        val color = when (b.signalStrength) {
            SignalStrength.STRONG -> Color.parseColor("#1D9E75")
            SignalStrength.WEAK -> Color.parseColor("#BA7517")
            SignalStrength.LOST -> Color.parseColor("#E24B4A")
        }
        holder.indicator.setBackgroundColor(color)
        holder.bar.progressTintList = ColorStateList.valueOf(color)
    }

    class DiffCallback : DiffUtil.ItemCallback<BleBeacon>() {
        override fun areItemsTheSame(a: BleBeacon, b: BleBeacon) = a.id == b.id
        override fun areContentsTheSame(a: BleBeacon, b: BleBeacon) = a == b
    }
}
