package com.aroundme.app.ui.objects

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aroundme.app.App
import com.aroundme.app.R
import com.aroundme.app.data.model.DeviceEntity
import com.aroundme.app.data.model.ObjectDeviceLink
import com.aroundme.app.data.model.ObjectType
import com.aroundme.app.data.model.SightingEntity
import com.aroundme.app.data.model.Transport
import com.aroundme.app.data.model.VirtualObjectEntity
import com.aroundme.app.databinding.ActivityObjectDetailBinding
import com.aroundme.app.databinding.ItemLinkedDeviceBinding
import com.aroundme.app.util.Format
import kotlinx.coroutines.launch

class ObjectDetailActivity : AppCompatActivity() {

    private lateinit var b: ActivityObjectDetailBinding
    private var obj: VirtualObjectEntity? = null
    private var objectId: Long = -1
    private var linked = mutableListOf<DeviceEntity>()
    private var typeReady = false

    private val db by lazy { (application as App).db }

    private val devicesAdapter = LinkedDeviceAdapter(
        onRemove = { d -> lifecycleScope.launch { db.objectDao().unlink(objectId, d.id); reloadLinked() } },
        removable = true
    )
    private val sightingsAdapter = SightingsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityObjectDetailBinding.inflate(layoutInflater)
        setContentView(b.root)
        objectId = intent.getLongExtra(EXTRA_OBJECT_ID, -1)

        b.spinnerType.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, ObjectType.labels
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        b.spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (typeReady) save()
                typeReady = true
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        b.listDevices.layoutManager = LinearLayoutManager(this)
        b.listDevices.adapter = devicesAdapter
        b.listSightings.layoutManager = LinearLayoutManager(this)
        b.listSightings.adapter = sightingsAdapter

        b.btnAttachDevice.setOnClickListener { showAttachDialog() }

        lifecycleScope.launch {
            obj = db.objectDao().get(objectId)
            obj?.let {
                b.editName.setText(it.name)
                b.editNote.setText(it.note ?: "")
                b.spinnerType.setSelection((it.type - 1).coerceIn(0, ObjectType.labels.size - 1))
            }
            reloadLinked()
        }
    }

    override fun onPause() {
        super.onPause()
        save()
    }

    private fun save() {
        val o = obj ?: return
        val updated = o.copy(
            name = b.editName.text.toString().trim().ifEmpty { o.name },
            type = b.spinnerType.selectedItemPosition + 1,
            note = b.editNote.text.toString().trim().ifEmpty { null }
        )
        if (updated != o) {
            obj = updated
            lifecycleScope.launch { db.objectDao().update(updated) }
        }
    }

    private suspend fun reloadLinked() {
        val ids = db.objectDao().deviceIdsForObject(objectId)
        val list = ids.mapNotNull { db.deviceDao().get(it) }
        linked = list.toMutableList()
        devicesAdapter.submit(list)
        val since = System.currentTimeMillis() - 7L * 24 * 3600_000
        val sightings = if (ids.isEmpty()) emptyList()
        else db.sightingDao().forDevicesSince(ids, since, 40)
        sightingsAdapter.submit(sightings)
    }

    private fun showAttachDialog() {
        lifecycleScope.launch {
            val all = db.deviceDao().recent(300)
            if (all.isEmpty()) {
                AlertDialog.Builder(this@ObjectDetailActivity)
                    .setMessage("No devices observed yet — start tracking first.")
                    .setPositiveButton("OK", null).show()
                return@launch
            }
            val alreadyLinked = linked.map { it.id }.toMutableSet()
            val names = all.map { d ->
                val n = d.displayName?.takeIf { it.isNotBlank() } ?: "(no name)"
                "$n  ·  ${d.mac}  (${Transport.label(d.transport)})"
            }.toTypedArray()
            val checked = all.map { alreadyLinked.contains(it.id) }.toBooleanArray()
            AlertDialog.Builder(this@ObjectDetailActivity)
                .setTitle("Attach devices")
                .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                    checked[which] = isChecked
                }
                .setPositiveButton("Save") { _, _ ->
                    lifecycleScope.launch {
                        val newSet = all.indices.filter { checked[it] }.map { all[it].id }.toSet()
                        for (id in newSet - alreadyLinked) {
                            db.objectDao().link(ObjectDeviceLink(objectId, id))
                        }
                        for (id in alreadyLinked - newSet) {
                            db.objectDao().unlink(objectId, id)
                        }
                        reloadLinked()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    companion object {
        const val EXTRA_OBJECT_ID = "object_id"
    }
}

class LinkedDeviceAdapter(
    private val onRemove: (DeviceEntity) -> Unit,
    private val removable: Boolean
) : RecyclerView.Adapter<LinkedDeviceAdapter.VH>() {

    private var items = listOf<DeviceEntity>()

    class VH(val b: ItemLinkedDeviceBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<DeviceEntity>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemLinkedDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val d = items[position]
        h.b.textName.text =
            "${d.displayName?.takeIf { it.isNotBlank() } ?: "(no name)"}  ·  ${Transport.label(d.transport)}"
        h.b.textMac.text = d.mac
        h.b.btnRemove.visibility = if (removable) View.VISIBLE else View.GONE
        h.b.btnRemove.setOnClickListener { onRemove(d) }
    }
}

class SightingsAdapter : RecyclerView.Adapter<SightingsAdapter.VH>() {

    private var items = listOf<SightingEntity>()

    class VH(val b: ItemLinkedDeviceBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<SightingEntity>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemLinkedDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val s = items[position]
        h.b.textName.text = s.label?.takeIf { it.isNotBlank() } ?: s.deviceId.substringAfter(':')
        val loc = if (s.lat != null && s.lng != null)
            "  @ ${"%.5f".format(s.lat)}, ${"%.5f".format(s.lng)}" else ""
        h.b.textMac.text = "${Format.dayTime(s.timestamp)}$loc"
        h.b.btnRemove.visibility = View.GONE
    }
}
