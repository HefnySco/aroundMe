package com.aroundme.app.ui.map

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aroundme.app.App
import com.aroundme.app.R
import com.aroundme.app.data.model.ObjectType
import com.aroundme.app.data.model.Transport
import com.aroundme.app.databinding.FragmentMapBinding
import com.aroundme.app.util.Format
import com.aroundme.app.util.Prefs
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class MapFragment : Fragment() {

    private var _b: FragmentMapBinding? = null
    private val b get() = _b!!
    private var plotted = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentMapBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.map.setMultiTouchControls(true)
        b.map.controller.setZoom(15.0)
        b.map.controller.setCenter(GeoPoint(0.0, 0.0))

        val prefs = Prefs(requireContext())
        b.toggleMode.check(if (prefs.mapMode == 1) R.id.btnObjects else R.id.btnDevices)
        b.toggleMode.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                prefs.mapMode = if (id == R.id.btnObjects) 1 else 0
                refreshMarkers()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        b.map.onResume()
        refreshMarkers()
    }

    override fun onPause() {
        super.onPause()
        b.map.onPause()
    }

    private fun icon(res: Int, color: Int): Drawable {
        val d = ContextCompat.getDrawable(requireContext(), res)!!.mutate()
        DrawableCompat.setTint(d, color)
        return d
    }

    private fun meMarker(): Marker {
        val prefs = Prefs(requireContext())
        val m = Marker(b.map)
        m.position = GeoPoint(prefs.lastLat, prefs.lastLng)
        m.title = "You"
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        return m
    }

    private fun refreshMarkers() {
        viewLifecycleOwner.lifecycleScope.launch {
            val app = requireActivity().application as App
            val prefs = Prefs(requireContext())
            val db = app.db
            val since = System.currentTimeMillis() - MAP_WINDOW_MS

            b.map.overlays.clear()
            if (prefs.hasLocation) b.map.overlays.add(meMarker())

            var count = 0
            if (prefs.mapMode == 1) {
                for (o in db.objectDao().all()) {
                    val ids = db.objectDao().deviceIdsForObject(o.id)
                    if (ids.isEmpty()) continue
                    val newest = ids.mapNotNull { db.sightingDao().lastForDevice(it) }
                        .filter { it.lat != null && it.lng != null && it.timestamp > since }
                        .maxByOrNull { it.timestamp } ?: continue
                    val m = Marker(b.map)
                    m.position = GeoPoint(newest.lat!!, newest.lng!!)
                    m.title = o.name
                    m.snippet = "${ObjectType.label(o.type)} · ${ids.size} devices · ${Format.ago(newest.timestamp)}"
                    m.icon = icon(objectIcon(o.type), objectColor(o.type))
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    b.map.overlays.add(m)
                    count++
                }
                b.textMapHint.text = "Objects — $count with recent sightings"
            } else {
                val lastPositions = db.sightingDao().lastPositionsSince(since, MAX_MARKERS)
                val devices = db.deviceDao().seenSinceList(since).associateBy { it.id }
                for (s in lastPositions) {
                    val d = devices[s.deviceId] ?: continue
                    val m = Marker(b.map)
                    m.position = GeoPoint(s.lat!!, s.lng!!)
                    val name = d.displayName?.takeIf { it.isNotBlank() } ?: d.mac
                    m.title = "$name (${Transport.label(d.transport)})"
                    m.snippet = "last seen ${Format.ago(s.timestamp)}"
                    m.icon = if (d.transport == Transport.WIFI_AP)
                        icon(R.drawable.ic_wifi, COLOR_WIFI)
                    else
                        icon(R.drawable.ic_bluetooth, COLOR_BT)
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    b.map.overlays.add(m)
                    count++
                }
                b.textMapHint.text =
                    "Devices — $count seen in last 24h · teal=Wi-Fi · blue=BT"
            }

            if (!plotted) {
                plotted = true
                if (prefs.hasLocation) {
                    b.map.controller.setCenter(GeoPoint(prefs.lastLat, prefs.lastLng))
                }
            }
            b.map.invalidate()
        }
    }

    private fun objectIcon(type: Int) = when (type) {
        ObjectType.PERSON -> R.drawable.ic_person
        ObjectType.PLACE -> R.drawable.ic_place
        ObjectType.CAR -> R.drawable.ic_car
        else -> R.drawable.ic_devices
    }

    private fun objectColor(type: Int) = when (type) {
        ObjectType.PERSON -> COLOR_PERSON
        ObjectType.PLACE -> COLOR_PLACE
        ObjectType.CAR -> COLOR_CAR
        else -> COLOR_OTHER
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private const val MAP_WINDOW_MS = 24 * 3600_000L
        private const val MAX_MARKERS = 150
        private const val COLOR_WIFI = 0xFF00796B.toInt()
        private const val COLOR_BT = 0xFF1E88E5.toInt()
        private const val COLOR_PERSON = 0xFF8E24AA.toInt()
        private const val COLOR_PLACE = 0xFF00796B.toInt()
        private const val COLOR_CAR = 0xFFFB8C00.toInt()
        private const val COLOR_OTHER = 0xFF616161.toInt()
    }
}
