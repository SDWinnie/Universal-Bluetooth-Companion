// 🤖 Generated wholely or partially with Google Antigravity
package com.sdwinnie.universalbluetooth

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sdwinnie.universalbluetooth.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val onConnectClick: (BluetoothDeviceModel) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<BluetoothDeviceModel>()

    fun updateList(newDevices: List<BluetoothDeviceModel>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    inner class DeviceViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BluetoothDeviceModel) {
            val displayName = item.name.ifEmpty { "อุปกรณ์ BLE นิรนาม" }
            binding.tvDeviceName.text = displayName
            binding.tvDeviceAddress.text = item.address

            val dist = item.getEstimatedDistanceMeters()
            val distText = if (dist > 0) "~$dist ม." else "ไม่ระบุ"
            binding.tvDistanceMetric.text = "$distText (${item.rssi} dBm)"

            // Color coding based on proximity
            val colorRes = when {
                dist in 0.0..5.0 -> R.color.signal_strong
                dist in 5.1..10.0 -> R.color.signal_medium
                else -> R.color.signal_weak
            }
            binding.tvDistanceMetric.setTextColor(
                ContextCompat.getColor(binding.root.context, colorRes)
            )

            binding.btnConnect.setOnClickListener {
                onConnectClick(item)
            }
        }
    }
}
