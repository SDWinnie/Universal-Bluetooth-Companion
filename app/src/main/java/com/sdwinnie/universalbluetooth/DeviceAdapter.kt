// 🤖 Generated wholely or partially with Google Antigravity
package com.sdwinnie.universalbluetooth

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.sdwinnie.universalbluetooth.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val onConnectClick: (BluetoothDeviceModel) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<BluetoothDeviceModel>()

    init {
        setHasStableIds(true)
    }

    fun updateList(newDevices: List<BluetoothDeviceModel>) {
        val oldSnapshot = devices.toList()
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldSnapshot.size
            override fun getNewListSize(): Int = newDevices.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean =
                oldSnapshot[oldPos].address == newDevices[newPos].address

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val o = oldSnapshot[oldPos]
                val n = newDevices[newPos]
                return o.name == n.name &&
                    o.rssi == n.rssi &&
                    o.deviceType == n.deviceType &&
                    o.isBonded == n.isBonded &&
                    o.isConnected == n.isConnected
            }
        })
        devices.clear()
        devices.addAll(newDevices)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemId(position: Int): Long = devices[position].address.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
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
            val ctx = binding.root.context
            binding.tvDeviceName.text = item.name.ifBlank { "อุปกรณ์บลูทูธไร้สาย" }
            binding.tvDeviceAddress.text = "${item.address} • ${item.getTransportLabel()}"

            if (item.rssi <= -97 && item.isBonded && !item.isConnected) {
                binding.tvDistanceMetric.text = "จับคู่ไว้ในเครื่อง (กดเพื่อบังคับเชื่อมต่อ)"
                binding.tvDistanceMetric.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            } else {
                val dist = item.getEstimatedDistanceMeters()
                val distText = if (dist > 0) "~$dist ม." else "ระยะใกล้"
                binding.tvDistanceMetric.text = if (item.isConnected) {
                    "เชื่อมต่ออยู่ • $distText (${item.rssi} dBm)"
                } else {
                    "$distText (${item.rssi} dBm)"
                }

                val colorRes = when {
                    item.isConnected || dist in 0.0..5.0 -> R.color.signal_strong
                    dist in 5.1..10.0 -> R.color.signal_medium
                    else -> R.color.signal_weak
                }
                binding.tvDistanceMetric.setTextColor(ContextCompat.getColor(ctx, colorRes))
            }

            val strokeColorRes = if (item.isConnected) R.color.connected_green else R.color.surface_variant
            binding.cardDeviceRoot.strokeColor = ContextCompat.getColor(ctx, strokeColorRes)

            binding.btnConnect.text = if (item.isConnected) "เชื่อมซ้ำ" else "เชื่อมต่อ"
            binding.btnConnect.setOnClickListener {
                onConnectClick(item)
            }
        }
    }
}
