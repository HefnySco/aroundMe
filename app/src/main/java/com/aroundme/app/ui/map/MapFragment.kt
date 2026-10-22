package com.aroundme.app.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aroundme.app.App
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

    private fun refreshMarkers() {
        viewLifecycleOwner.lifecycleScope.launch {
            val app = requireActivity().application as App
            val prefs = Prefs(requireContext())
            val since = System.currentTimeMillis() - MAP_WINDOW_MS
            val lastPositions = app.db.sightingDao().lastPositionsSince(since, MAX_MARKERS)
            val devices = app.db.deviceDao().seenSinceList(since).associateBy { it.id }

            b.map.overlays.clear()

            if (prefs.hasLocation) {
                val me = Marker(b.map)
                me.position = GeoPoint(prefs.lastLat, prefs.lastLng)
                me.title = "You"
                me.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                b.map.overlays.add(me)
            }

            for (s in lastPositions) {
                val d = devices[s.deviceId] ?: continue
                val m = Marker(b.map)
                m.position = GeoPoint(s.lat!!, s.lng!!)
                val name = d.displayName?.takeIf { it.isNotBlank() } ?: d.mac
                m.title = "$name (${Transport.label(d.transport)})"
                m.snippet = "last seen ${Format.ago(s.timestamp)}"
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                b.map.overlays.add(m)
            }

            if (!plotted) {
                plotted = true
                if (prefs.hasLocation) {
                    b.map.controller.setCenter(GeoPoint(prefs.lastLat, prefs.lastLng))
                } else if (lastPositions.isNotEmpty()) {
                    val s = lastPositions.first()
                    b.map.controller.setCenter(GeoPoint(s.lat!!, s.lng!!))
                }
            }

            b.textMapHint.text =
                "Last 24h — ${lastPositions.size} devices · markers are last-seen positions"
            b.map.invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private const val MAP_WINDOW_MS = 24 * 3600_000L
        private const val MAX_MARKERS = 150
    }
}
