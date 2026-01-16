package com.hotspotapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hotspotapp.R
import com.hotspotapp.WifiNetwork

class WifiListAdapter(
    private val onItemClick: (WifiNetwork) -> Unit
) : ListAdapter<WifiNetwork, WifiListAdapter.WifiViewHolder>(WifiDiffCallback()) {

    private var selectedPosition: Int = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WifiViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wifi, parent, false)
        return WifiViewHolder(view)
    }

    override fun onBindViewHolder(holder: WifiViewHolder, position: Int) {
        val network = getItem(position)
        holder.bind(network, position == selectedPosition)
        holder.itemView.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(previousPosition)
            notifyItemChanged(selectedPosition)
            onItemClick(network)
        }
    }

    fun getSelectedNetwork(): WifiNetwork? {
        return if (selectedPosition != RecyclerView.NO_POSITION && selectedPosition < itemCount) {
            getItem(selectedPosition)
        } else null
    }

    fun clearSelection() {
        val previousPosition = selectedPosition
        selectedPosition = RecyclerView.NO_POSITION
        notifyItemChanged(previousPosition)
    }

    class WifiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ssidText: TextView = itemView.findViewById(R.id.tvSsid)
        private val securityText: TextView = itemView.findViewById(R.id.tvSecurity)
        private val signalIcon: ImageView = itemView.findViewById(R.id.ivSignal)
        private val frequencyText: TextView = itemView.findViewById(R.id.tvFrequency)

        fun bind(network: WifiNetwork, isSelected: Boolean) {
            ssidText.text = network.ssid
            securityText.text = network.securityType
            frequencyText.text = if (network.frequency > 5000) "5 GHz" else "2.4 GHz"

            val signalDrawable = when (network.signalStrength) {
                4 -> R.drawable.ic_signal_4
                3 -> R.drawable.ic_signal_3
                2 -> R.drawable.ic_signal_2
                1 -> R.drawable.ic_signal_1
                else -> R.drawable.ic_signal_0
            }
            signalIcon.setImageResource(signalDrawable)

            itemView.isSelected = isSelected
            itemView.setBackgroundResource(
                if (isSelected) R.drawable.bg_selected_item
                else android.R.color.transparent
            )
        }
    }

    class WifiDiffCallback : DiffUtil.ItemCallback<WifiNetwork>() {
        override fun areItemsTheSame(oldItem: WifiNetwork, newItem: WifiNetwork): Boolean {
            return oldItem.bssid == newItem.bssid
        }

        override fun areContentsTheSame(oldItem: WifiNetwork, newItem: WifiNetwork): Boolean {
            return oldItem == newItem
        }
    }
}
