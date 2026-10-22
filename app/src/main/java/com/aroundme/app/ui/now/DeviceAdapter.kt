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
import com.aroundme.app.util.Category
import com.aroundme.app.util.Consts
import com.aroundme.app.util.DeviceClassifier
import com.aroundme.app.util.Format

class DeviceAdapter(
    private val onClick: (DeviceWithObjects) -> Unit
) : ListAdapter<DeviceWithObjects, DeviceAdapter.VH>(DIFF) {

    class VH(val b: ItemDeviceBinding) : RecyclerView.ViewHolder(b.root) {
        val defaultCardColor = b.root.cardBackgroundColor
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = getItem(position)
        val d = item.device
        val now = System.currentTimeMillis()
        val isNew = now - d.firstSeen < Consts.NEW_DEVICE_MS
        if (isNew) {
            h.b.root.setCardBackgroundColor(NEW_CARD_COLOR)
        } else {
            h.b.root.setCardBackgroundColor(h.defaultCardColor)
        }
        h.b.iconTransport.setImageResource(
            if (d.transport == Transport.WIFI_AP) R.drawable.ic_wifi else R.drawable.ic_bluetooth
        )
        h.b.iconTransport.setColorFilter(
            if (d.transport == Transport.WIFI_AP) COLOR_WIFI else COLOR_BT
        )
        h.b.textName.text = d.displayName?.takeIf { it.isNotBlank() } ?: "(no name)"
        val rand = if (d.randomizedMac) "  ·  random MAC" else ""
        h.b.textMac.text = "${d.mac}  ·  ${Transport.label(d.transport)}$rand"
        if (item.objectNames.isNullOrBlank()) {
            h.b.textObjects.visibility = View.GONE
        } else {
            h.b.textObjects.visibility = View.VISIBLE
            h.b.textObjects.text = item.objectNames
            h.b.textObjects.setTextColor(COLOR_OBJECT)
        }
        h.b.badgeMoving.visibility = if (d.movingWithYou) View.VISIBLE else View.GONE
        val cat = Category.label(DeviceClassifier.classify(d.transport, d.displayName))
        val vendor = DeviceClassifier.vendor(d.mac)
        h.b.textMeta.text =
            listOfNotNull("×${d.seenCount}", Format.ago(d.lastSeen), cat, vendor)
                .joinToString(" · ")
        h.b.root.setOnClickListener { onClick(item) }
    }

    companion object {
        private const val NEW_CARD_COLOR = 0x40FFB300
        private const val COLOR_WIFI = 0xFF00A896.toInt()
        private const val COLOR_BT = 0xFF2979FF.toInt()
        private const val COLOR_OBJECT = 0xFF8E24AA.toInt()

        val DIFF = object : DiffUtil.ItemCallback<DeviceWithObjects>() {
            override fun areItemsTheSame(a: DeviceWithObjects, b: DeviceWithObjects) =
                a.device.id == b.device.id

            override fun areContentsTheSame(a: DeviceWithObjects, b: DeviceWithObjects) =
                a == b
        }
    }
}
