package com.aroundme.app.ui.objects

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aroundme.app.App
import com.aroundme.app.R
import com.aroundme.app.data.db.ObjectWithCount
import com.aroundme.app.data.model.ObjectType
import com.aroundme.app.data.model.VirtualObjectEntity
import com.aroundme.app.databinding.FragmentObjectsBinding
import com.aroundme.app.databinding.ItemObjectBinding
import kotlinx.coroutines.launch

class ObjectsFragment : Fragment() {

    private var _b: FragmentObjectsBinding? = null
    private val b get() = _b!!

    private val adapter = ObjectAdapter(
        onClick = { o ->
            startActivity(
                Intent(requireContext(), ObjectDetailActivity::class.java)
                    .putExtra(ObjectDetailActivity.EXTRA_OBJECT_ID, o.obj.id)
            )
        },
        onLongClick = { o -> confirmDelete(o.obj) }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentObjectsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter

        b.fabAdd.setOnClickListener { showNewObjectDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                (requireActivity().application as App).db.objectDao()
                    .objectsWithCountFlow()
                    .collect { list ->
                        adapter.submitList(list)
                        b.empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
            }
        }
    }

    private fun showNewObjectDialog() {
        val v = layoutInflater.inflate(R.layout.dialog_new_object, null)
        val edit = v.findViewById<EditText>(R.id.editName)
        val spinner = v.findViewById<Spinner>(R.id.spinnerType)
        spinner.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, ObjectType.labels
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        AlertDialog.Builder(requireContext())
            .setTitle("New object")
            .setView(v)
            .setPositiveButton("Create") { _, _ ->
                val name = edit.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val type = spinner.selectedItemPosition + 1
                viewLifecycleOwner.lifecycleScope.launch {
                    val db = (requireActivity().application as App).db
                    val id = db.objectDao().insert(
                        VirtualObjectEntity(
                            name = name, type = type, note = null,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    startActivity(
                        Intent(requireContext(), ObjectDetailActivity::class.java)
                            .putExtra(ObjectDetailActivity.EXTRA_OBJECT_ID, id)
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(o: VirtualObjectEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete \"${o.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    (requireActivity().application as App).db.objectDao().delete(o)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}

class ObjectAdapter(
    private val onClick: (ObjectWithCount) -> Unit,
    private val onLongClick: (ObjectWithCount) -> Unit
) : ListAdapter<ObjectWithCount, ObjectAdapter.VH>(DIFF) {

    class VH(val b: ItemObjectBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemObjectBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = getItem(position)
        h.b.textName.text = item.obj.name
        h.b.textType.text = ObjectType.label(item.obj.type)
        h.b.textCount.text = "${item.deviceCount} MAC${if (item.deviceCount == 1) "" else "s"}"
        h.b.iconType.setImageResource(
            when (item.obj.type) {
                ObjectType.PERSON -> R.drawable.ic_person
                ObjectType.PLACE -> R.drawable.ic_place
                ObjectType.CAR -> R.drawable.ic_car
                else -> R.drawable.ic_devices
            }
        )
        h.b.root.setOnClickListener { onClick(item) }
        h.b.root.setOnLongClickListener { onLongClick(item); true }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ObjectWithCount>() {
            override fun areItemsTheSame(a: ObjectWithCount, b: ObjectWithCount) =
                a.obj.id == b.obj.id

            override fun areContentsTheSame(a: ObjectWithCount, b: ObjectWithCount) = a == b
        }
    }
}
