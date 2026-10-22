package com.aroundme.app.ui.now

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.aroundme.app.App
import com.aroundme.app.data.model.DeviceWithObjects
import com.aroundme.app.databinding.FragmentNowBinding
import com.aroundme.app.scan.ScanService
import com.aroundme.app.ui.device.DeviceDetailActivity
import com.aroundme.app.util.Perms
import com.aroundme.app.util.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class NowFragment : Fragment() {

    private var _b: FragmentNowBinding? = null
    private val b get() = _b!!
    private lateinit var prefs: Prefs
    private val adapter = DeviceAdapter { item ->
        startActivity(
            Intent(requireContext(), DeviceDetailActivity::class.java)
                .putExtra(DeviceDetailActivity.EXTRA_DEVICE_ID, item.device.id)
        )
    }
    private var spinnerReady = false
    private var hideSpinnerReady = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentNowBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = Prefs(requireContext())

        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter

        val spinnerAdapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item,
            Prefs.INTERVAL_LABELS
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        b.spinnerInterval.adapter = spinnerAdapter
        val idx = Prefs.INTERVAL_OPTIONS.indexOf(prefs.scanIntervalMs)
        b.spinnerInterval.setSelection(if (idx >= 0) idx else 1)
        b.spinnerInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (spinnerReady) prefs.scanIntervalMs = Prefs.INTERVAL_OPTIONS[pos]
                spinnerReady = true
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val hideAdapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, Prefs.HIDE_LABELS
        )
        hideAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        b.spinnerHide.adapter = hideAdapter
        val hideIdx = Prefs.HIDE_OPTIONS.indexOf(prefs.hideAfterMs)
        b.spinnerHide.setSelection(if (hideIdx >= 0) hideIdx else 2)
        b.spinnerHide.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (hideSpinnerReady) prefs.hideAfterMs = Prefs.HIDE_OPTIONS[pos]
                hideSpinnerReady = true
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        b.switchWifi.isChecked = prefs.wifiScan
        b.switchBt.isChecked = prefs.btScan
        b.switchWifi.setOnCheckedChangeListener { _, v -> prefs.wifiScan = v }
        b.switchBt.setOnCheckedChangeListener { _, v -> prefs.btScan = v }

        b.switchTracking.setOnCheckedChangeListener { _, checked ->
            if (checked) startTracking() else {
                ScanService.stop(requireContext())
                refreshSwitch()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val db = (requireActivity().application as App).db
                val ticker = flow {
                    while (true) {
                        emit(Unit)
                        delay(REFRESH_TICK_MS)
                    }
                }
                combine(
                    db.deviceDao().allDevicesFlow(),
                    db.objectDao().allLinksFlow(),
                    ticker
                ) { devs, links, _ ->
                    val cutoff = System.currentTimeMillis() - prefs.hideAfterMs
                    val namesByDev = links.groupBy({ it.deviceId }, { it.objectName })
                    devs.filter { it.lastSeen > cutoff }.map {
                        DeviceWithObjects(it, namesByDev[it.id]?.joinToString(", "))
                    }
                }.collect { list ->
                    adapter.submitList(list)
                    b.empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    b.textStatus.text =
                        if (ScanService.isRunning) "Tracking — ${list.size} devices around"
                        else "Tracking is off — showing last seen"
                }
            }
        }
    }

    private fun startTracking() {
        val missing = Perms.missing(requireContext())
        if (missing.isNotEmpty()) {
            b.textPermHint.visibility = View.VISIBLE
            b.textPermHint.text = "Grant the permissions (location, nearby devices) to start tracking."
            permLauncher.launch(missing.toTypedArray())
            refreshSwitch()
            return
        }
        b.textPermHint.visibility = View.GONE
        if (!Perms.locationEnabled(requireContext())) {
            b.textPermHint.visibility = View.VISIBLE
            b.textPermHint.text =
                "Location is off — Wi-Fi scanning and GPS tagging need it enabled."
        }
        ScanService.start(requireContext())
        refreshSwitch()
    }

    private val permLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) startTracking() else refreshSwitch()
    }

    private fun refreshSwitch() {
        b.switchTracking.setOnCheckedChangeListener(null)
        b.switchTracking.isChecked = ScanService.isRunning
        b.switchTracking.setOnCheckedChangeListener { _, checked ->
            if (checked) startTracking() else {
                ScanService.stop(requireContext())
                refreshSwitch()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSwitch()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private const val REFRESH_TICK_MS = 10_000L
    }
}
