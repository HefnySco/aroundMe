package com.aroundme.app.ui.device

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aroundme.app.App
import com.aroundme.app.data.model.ObjectDeviceLink
import com.aroundme.app.data.model.ObjectNameLink
import com.aroundme.app.data.model.ObjectType
import com.aroundme.app.data.model.SightingEntity
import com.aroundme.app.data.model.Transport
import com.aroundme.app.data.model.VirtualObjectEntity
import com.aroundme.app.databinding.ActivityDeviceDetailBinding
import com.aroundme.app.databinding.ItemSessionBinding
import com.aroundme.app.util.Category
import com.aroundme.app.util.DeviceClassifier
import com.aroundme.app.util.Format
import com.aroundme.app.util.Geo
import kotlinx.coroutines.launch
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class DeviceDetailActivity : AppCompatActivity() {

    private lateinit var b: ActivityDeviceDetailBinding
    private val db by lazy { (application as App).db }
    private var deviceId: String = ""
    private val sessionsAdapter = SessionAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDeviceDetailBinding.inflate(layoutInflater)
        setContentView(b.root)
        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: ""

        b.listSessions.layoutManager = LinearLayoutManager(this)
        b.listSessions.adapter = sessionsAdapter
        b.map.setMultiTouchControls(true)

        b.btnAttach.setOnClickListener { showAttachDialog() }

        lifecycleScope.launch { load() }
    }

    override fun onResume() {
        super.onResume()
        b.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        b.map.onPause()
    }

    private suspend fun load() {
        val device = db.deviceDao().get(deviceId) ?: return
        val objects = db.objectDao().objectsForDevice(deviceId, device.displayName)
        b.textName.text = device.displayName?.takeIf { it.isNotBlank() } ?: "(no name)"
        b.textMac.text = "${device.mac}  ·  ${Transport.label(device.transport)}"
        val objText = if (objects.isEmpty()) "not linked to any object"
        else "linked to: " + objects.joinToString(", ") { "${it.name} (${ObjectType.label(it.type)})" }
        val rand = if (device.randomizedMac) "  ·  random MAC" else ""
        val moving = if (device.movingWithYou) "  ·  ⚠ moving with you" else ""
        val cat = Category.label(
            DeviceClassifier.classify(
                device.transport, device.displayName, device.appearance,
                device.btClass, device.beacon
            )
        )
        val vendor = DeviceClassifier.vendor(device.mac, device.companyId)
        val typeText = listOfNotNull(cat, vendor).joinToString(" · ")
        b.textMeta.text = "$objText\nseen ×${device.seenCount} · first ${Format.dayTime(device.firstSeen)} · last ${Format.ago(device.lastSeen)}$rand$moving" +
            if (typeText.isNotBlank()) "\ntype: $typeText" else ""

        val sightings = db.sightingDao().forDevice(deviceId)
        sessionsAdapter.submit(sessions(sightings).reversed())
        plotMap(sightings)
    }

    private fun plotMap(sightings: List<SightingEntity>) {
        val pts = sightings.filter { it.lat != null && it.lng != null }
            .takeLast(MAX_MAP_POINTS)
        if (pts.isEmpty()) {
            b.map.visibility = View.GONE
            return
        }
        b.map.overlays.clear()
        val geoPoints = pts.map { GeoPoint(it.lat!!, it.lng!!) }
        for (i in pts.indices) {
            val m = Marker(b.map)
            m.position = geoPoints[i]
            m.title = Format.dayTime(pts[i].timestamp)
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            b.map.overlays.add(m)
        }
        b.map.post {
            try {
                b.map.zoomToBoundingBox(
                    BoundingBox.fromGeoPointsSafe(geoPoints).increaseByScale(1.3f), false
                )
            } catch (_: Exception) {
                b.map.controller.setCenter(geoPoints.last())
                b.map.controller.setZoom(15.0)
            }
        }
        b.map.invalidate()
    }

    private fun showAttachDialog() {
        lifecycleScope.launch {
            val device = db.deviceDao().get(deviceId) ?: return@launch
            val objects = db.objectDao().all()
            if (objects.isEmpty()) {
                AlertDialog.Builder(this@DeviceDetailActivity)
                    .setMessage("No objects yet — create one in the Objects tab.")
                    .setPositiveButton("OK", null).show()
                return@launch
            }
            val devName = device.displayName?.takeIf { it.isNotBlank() }
            if (devName != null) {
                AlertDialog.Builder(this@DeviceDetailActivity)
                    .setTitle("Link \"$devName\" how?")
                    .setItems(
                        arrayOf(
                            "Only this MAC (${device.mac})",
                            "Any device named \"$devName\""
                        )
                    ) { _, which ->
                        lifecycleScope.launch { showObjectPicker(objects, byName = which == 1, name = devName) }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                showObjectPicker(objects, byName = false, name = null)
            }
        }
    }

    private suspend fun showObjectPicker(
        objects: List<VirtualObjectEntity>,
        byName: Boolean,
        name: String?
    ) {
        val device = db.deviceDao().get(deviceId) ?: return
        val linked = if (byName) {
            objects.filter { name in db.objectDao().namesForObject(it.id) }
                .map { it.id }.toMutableSet()
        } else {
            db.objectDao().directObjectIds(deviceId).toMutableSet()
        }
        val names = objects.map { "${it.name} (${ObjectType.label(it.type)})" }.toTypedArray()
        val checked = objects.map { linked.contains(it.id) }.toBooleanArray()
        AlertDialog.Builder(this@DeviceDetailActivity)
            .setTitle(if (byName) "Link name \"$name\" to objects" else "Attach device to objects")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Save") { _, _ ->
                lifecycleScope.launch {
                    val newSet = objects.indices.filter { checked[it] }
                        .map { objects[it].id }.toSet()
                    if (byName && name != null) {
                        for (id in newSet - linked) {
                            db.objectDao().linkName(ObjectNameLink(id, name))
                        }
                        for (id in linked - newSet) {
                            db.objectDao().unlinkName(id, name)
                        }
                    } else {
                        for (id in newSet - linked) {
                            db.objectDao().link(ObjectDeviceLink(id, deviceId))
                        }
                        for (id in linked - newSet) {
                            db.objectDao().unlink(id, deviceId)
                        }
                    }
                    load()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sessions(list: List<SightingEntity>): List<Session> {
        if (list.isEmpty()) return emptyList()
        val out = mutableListOf<Session>()
        var from = list.first()
        var prev = list.first()
        var count = 1
        for (s in list.drop(1)) {
            if (s.timestamp - prev.timestamp > SESSION_GAP_MS) {
                out += makeSession(from, prev, count)
                from = s
                count = 1
            } else {
                count++
            }
            prev = s
        }
        out += makeSession(from, prev, count)
        return out
    }

    private fun makeSession(from: SightingEntity, to: SightingEntity, count: Int): Session {
        val span = if (from.lat != null && to.lat != null)
            Geo.distanceMeters(from.lat, from.lng!!, to.lat, to.lng!!) else 0.0
        return Session(from.timestamp, to.timestamp, count, span)
    }

    companion object {
        const val EXTRA_DEVICE_ID = "device_id"
        private const val SESSION_GAP_MS = 10 * 60_000L
        private const val MAX_MAP_POINTS = 300
    }
}

data class Session(val from: Long, val to: Long, val count: Int, val spanMeters: Double)

class SessionAdapter : RecyclerView.Adapter<SessionAdapter.VH>() {

    private var items = listOf<Session>()

    class VH(val b: ItemSessionBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<Session>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val s = items[position]
        h.b.textRange.text = Format.range(s.from, s.to)
        val dur = (s.to - s.from) / 60_000
        val spanText = if (s.spanMeters > 50)
            "  ·  span ${Geo.formatDistance(s.spanMeters)}" else ""
        h.b.textSpan.text = "${s.count} sightings · ${dur} min$spanText"
    }
}
