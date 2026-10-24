package com.aroundme.app.ui.alerts

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aroundme.app.App
import com.aroundme.app.data.model.AlertEntity
import com.aroundme.app.databinding.FragmentAlertsBinding
import com.aroundme.app.databinding.ItemAlertBinding
import com.aroundme.app.ui.device.DeviceDetailActivity
import com.aroundme.app.util.Format
import kotlinx.coroutines.launch

class AlertsFragment : Fragment() {

    private var _b: FragmentAlertsBinding? = null
    private val b get() = _b!!

    private val adapter = AlertAdapter { alert ->
        viewLifecycleOwner.lifecycleScope.launch {
            (requireActivity().application as App).db.alertDao().acknowledge(alert.id)
        }
        alert.deviceId?.let {
            startActivity(
                Intent(requireContext(), DeviceDetailActivity::class.java)
                    .putExtra(DeviceDetailActivity.EXTRA_DEVICE_ID, it)
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentAlertsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter

        b.btnClearAll.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Clear all alerts?")
                .setMessage("This deletes all recorded alerts.")
                .setPositiveButton("Clear") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        (requireActivity().application as App).db.alertDao().clearAll()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                (requireActivity().application as App).db.alertDao().allFlow()
                    .collect { list ->
                        adapter.submitList(list)
                        b.empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}

class AlertAdapter(
    private val onClick: (AlertEntity) -> Unit
) : ListAdapter<AlertEntity, AlertAdapter.VH>(DIFF) {

    class VH(val b: ItemAlertBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAlertBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val a = getItem(position)
        h.b.textMessage.text = a.message
        h.b.textMessage.alpha = if (a.acknowledged) 0.55f else 1f
        h.b.textTime.text = Format.dayTime(a.timestamp)
        h.b.root.setOnClickListener { onClick(a) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AlertEntity>() {
            override fun areItemsTheSame(a: AlertEntity, b: AlertEntity) = a.id == b.id
            override fun areContentsTheSame(a: AlertEntity, b: AlertEntity) = a == b
        }
    }
}
