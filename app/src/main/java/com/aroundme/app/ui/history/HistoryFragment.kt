package com.aroundme.app.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aroundme.app.App
import com.aroundme.app.data.db.DeviceLinkRow
import com.aroundme.app.data.model.DeviceEntity
import com.aroundme.app.data.model.Transport
import com.aroundme.app.databinding.FragmentHistoryBinding
import com.aroundme.app.ui.device.DeviceDetailActivity
import com.aroundme.app.ui.now.DeviceAdapter
import com.aroundme.app.ui.now.NowItem
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _b: FragmentHistoryBinding? = null
    private val b get() = _b!!
    private val adapter = DeviceAdapter { item ->
        startActivity(
            Intent(requireContext(), DeviceDetailActivity::class.java)
                .putExtra(DeviceDetailActivity.EXTRA_DEVICE_ID, item.device.id)
        )
    }

    private var sortAsc = false
    private var showWifi = true
    private var showBt = true
    private val selectedObjects = mutableSetOf<String>()
    private var latestDevs: List<DeviceEntity> = emptyList()
    private var latestLinks: List<DeviceLinkRow> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentHistoryBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter

        b.btnSort.setOnClickListener {
            sortAsc = !sortAsc
            b.btnSort.text = if (sortAsc) "Last seen ↑" else "Last seen ↓"
            render()
        }
        b.chipWifi.setOnCheckedChangeListener { _, v -> showWifi = v; render() }
        b.chipBt.setOnCheckedChangeListener { _, v -> showBt = v; render() }
        b.btnObjects.setOnClickListener { showObjectPicker() }

        val db = (requireActivity().application as App).db
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                db.deviceDao().allDevicesFlow(),
                db.objectDao().allLinksFlow()
            ) { devs, links -> devs to links }
                .collect { (devs, links) ->
                    latestDevs = devs
                    latestLinks = links
                    render()
                }
        }
    }

    private fun render() {
        val namesByDev = latestLinks.groupBy({ it.deviceId }, { it.objectName })
        val devs = latestDevs.filter { d ->
            when (d.transport) {
                Transport.WIFI_AP -> showWifi
                else -> showBt
            }
        }
        val groups = LinkedHashMap<String, MutableList<DeviceEntity>>()
        for (d in devs) {
            val key = when {
                !d.randomizedMac -> "D:${d.id}"
                d.displayName.isNullOrBlank() -> "UNNAMED"
                else -> "N:${d.displayName}"
            }
            groups.getOrPut(key) { mutableListOf() }.add(d)
        }
        var items = groups.map { (key, members) ->
            val rep = members.maxBy { it.lastSeen }
            val names = members
                .flatMap { namesByDev[it.id].orEmpty() }
                .distinct()
                .joinToString(", ")
                .ifBlank { null }
            when (key) {
                "UNNAMED" -> NowItem(
                    rep, names, members.size,
                    nameOverride = "(no name)  ·  ×${members.size} random MACs",
                    macOverride = "rotating addresses"
                )
                else -> NowItem(rep, names, members.size)
            }
        }
        if (selectedObjects.isNotEmpty()) {
            items = items.filter { item ->
                val names = item.objectNames?.split(", ")?.toSet().orEmpty()
                names.any { it in selectedObjects } ||
                    (names.isEmpty() && UNLINKED in selectedObjects)
            }
        }
        items = if (sortAsc) items.sortedBy { it.device.lastSeen }
            else items.sortedByDescending { it.device.lastSeen }
        adapter.submitList(items)
        b.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        b.textHeader.text =
            "${items.size} entries · ${latestDevs.size} MACs recorded — grouped by identity"
    }

    private fun showObjectPicker() {
        val names = latestLinks.map { it.objectName }.distinct().sorted()
        val entries = listOf(UNLINKED) + names
        val checked = BooleanArray(entries.size) { entries[it] in selectedObjects }
        AlertDialog.Builder(requireContext())
            .setTitle("Show devices linked to")
            .setMultiChoiceItems(entries.toTypedArray(), checked) { _, which, isChecked ->
                if (isChecked) selectedObjects.add(entries[which])
                else selectedObjects.remove(entries[which])
            }
            .setPositiveButton("Done") { _, _ -> updateObjectButton(); render() }
            .setNeutralButton("All") { _, _ ->
                selectedObjects.clear()
                updateObjectButton()
                render()
            }
            .show()
    }

    private fun updateObjectButton() {
        b.btnObjects.text = if (selectedObjects.isEmpty()) "Objects: all"
            else "Objects: ${selectedObjects.size}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private const val UNLINKED = "(unlinked)"
    }
}
