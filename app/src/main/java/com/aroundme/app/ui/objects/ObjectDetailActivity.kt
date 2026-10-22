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
import com.aroundme.app.data.model.ObjectNameLink
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
    private var typeReady = false

    private val db by lazy { (application as App).db }

    sealed class Row {
        data class NameLink(val name: String) : Row()
        data class Dev(val d: DeviceEntity, val direct: Boolean) : Row()
    }

    private val devicesAdapter = LinkedDeviceAdapter(
        onRemove = { row ->
            lifecycleScope.launch {
                when (row) {
                    is Row.NameLink -> db.objectDao().unlinkName(objectId, row.name)
                    is Row.Dev -> {
                        if (row.direct) db.objectDao().unlink(objectId, row.d.id)
                        else row.d.displayName?.let { db.objectDao().unlinkName(objectId, it) }
                    }
                }
                reloadLinked()
            }
        }
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

        b.btnAttachDevice.setOnClickListener { showAttachByMacDialog() }
        b.btnAttachName.setOnClickListener { showAttachByNameDialog() }

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
        val direct = db.objectDao().directDeviceIdsForObject(objectId).toSet()
        val names = db.objectDao().namesForObject(objectId)
        val allIds = db.objectDao().deviceIdsForObject(objectId).toSet()
        val devices = allIds.mapNotNull { db.deviceDao().get(it) }
        val rows = names.map { Row.NameLink(it) } +
            devices.map { Row.Dev(it, it.id in direct) }
        devicesAdapter.submit(rows)

        val since = System.currentTimeMillis() - 7L * 24 * 3600_000
        val sightings = if (allIds.isEmpty()) emptyList()
        else db.sightingDao().forDevicesSince(allIds.toList(), since, 40)
        sightingsAdapter.submit(sightings)
    }

    private fun showAttachByMacDialog() {
        lifecycleScope.launch {
            val all = db.deviceDao().recent(300)
            if (all.isEmpty()) {
                AlertDialog.Builder(this@ObjectDetailActivity)
                    .setMessage("No devices observed yet — start tracking first.")
                    .setPositiveButton("OK", null).show()
                return@launch
            }
            val alreadyLinked = db.objectDao().directDeviceIdsForObject(objectId).toMutableSet()
            val names = all.map { d ->
                val n = d.displayName?.takeIf { it.isNotBlank() } ?: "(no name)"
                "$n  ·  ${d.mac}  (${Transport.label(d.transport)})"
            }.toTypedArray()
            val checked = all.map { alreadyLinked.contains(it.id) }.toBooleanArray()
            AlertDialog.Builder(this@ObjectDetailActivity)
                .setTitle("Attach devices by MAC")
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

    private fun showAttachByNameDialog() {
        lifecycleScope.launch {
            val allNames = db.objectDao().distinctNames()
            if (allNames.isEmpty()) {
                AlertDialog.Builder(this@ObjectDetailActivity)
                    .setMessage("No named devices observed yet.")
                    .setPositiveButton("OK", null).show()
                return@launch
            }
            val linked = db.objectDao().namesForObject(objectId).toMutableSet()
            val checked = allNames.map { linked.contains(it) }.toBooleanArray()
            AlertDialog.Builder(this@ObjectDetailActivity)
                .setTitle("Attach by advertised name — matches any MAC")
                .setMultiChoiceItems(allNames.toTypedArray(), checked) { _, which, isChecked ->
                    checked[which] = isChecked
                }
                .setPositiveButton("Save") { _, _ ->
                    lifecycleScope.launch {
                        val newSet = allNames.indices.filter { checked[it] }
                            .map { allNames[it] }.toSet()
                        for (n in newSet - linked) {
                            db.objectDao().linkName(ObjectNameLink(objectId, n))
                        }
                        for (n in linked - newSet) {
                            db.objectDao().unlinkName(objectId, n)
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
    private val onRemove: (ObjectDetailActivity.Row) -> Unit
) : RecyclerView.Adapter<LinkedDeviceAdapter.VH>() {

    private var items = listOf<ObjectDetailActivity.Row>()

    class VH(val b: ItemLinkedDeviceBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<ObjectDetailActivity.Row>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemLinkedDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        when (val row = items[position]) {
            is ObjectDetailActivity.Row.NameLink -> {
                h.b.textName.text = "≈ \"${row.name}\""
                h.b.textMac.text = "matches any device advertising this name"
            }
            is ObjectDetailActivity.Row.Dev -> {
                val d = row.d
                val via = if (row.direct) "" else "  ·  via name"
                h.b.textName.text =
                    "${d.displayName?.takeIf { it.isNotBlank() } ?: "(no name)"}  ·  ${Transport.label(d.transport)}$via"
                h.b.textMac.text = d.mac
            }
        }
        h.b.btnRemove.setOnClickListener { onRemove(items[position]) }
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
