package com.aroundme.app.ui.now

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aroundme.app.R
import com.aroundme.app.data.model.DeviceWithObjects
import com.aroundme.app.data.model.Transport
import com.aroundme.app.databinding.ItemDeviceBinding
import com.aroundme.app.util.Format

class DeviceAdapter(
    private val onClick: (DeviceWithObjects) -> Unit
) : ListAdapter<DeviceWithObjects, DeviceAdapter.VH>(DIFF) {

    class VH(val b: ItemDeviceBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = getItem(position)
        val d = item.device
        h.b.iconTransport.setImageResource(
            if (d.transport == Transport.WIFI_AP) R.drawable.ic_wifi else R.drawable.ic_bluetooth
        )
        h.b.textName.text = d.displayName?.takeIf { it.isNotBlank() } ?: "(no name)"
        val rand = if (d.randomizedMac) "  ·  random MAC" else ""
        h.b.textMac.text = "${d.mac}  ·  ${Transport.label(d.transport)}$rand"
        if (item.objectNames.isNullOrBlank()) {
            h.b.textObjects.visibility = View.GONE
        } else {
            h.b.textObjects.visibility = View.VISIBLE
            h.b.textObjects.text = item.objectNames
        }
        h.b.badgeMoving.visibility = if (d.movingWithYou) View.VISIBLE else View.GONE
        h.b.textMeta.text = "×${d.seenCount} · ${Format.ago(d.lastSeen)}"
        h.b.root.setOnClickListener { onClick(item) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<DeviceWithObjects>() {
            override fun areItemsTheSame(a: DeviceWithObjects, b: DeviceWithObjects) =
                a.device.id == b.device.id

            override fun areContentsTheSame(a: DeviceWithObjects, b: DeviceWithObjects) =
                a == b
        }
    }
}
