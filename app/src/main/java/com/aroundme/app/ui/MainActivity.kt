package com.aroundme.app.ui

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.aroundme.app.R
import com.aroundme.app.databinding.ActivityMainBinding
import com.aroundme.app.ui.alerts.AlertsFragment
import com.aroundme.app.ui.map.MapFragment
import com.aroundme.app.ui.now.NowFragment
import com.aroundme.app.ui.objects.ObjectsFragment
import com.aroundme.app.util.Perms

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val fragments = HashMap<Int, Fragment>()
    private var currentTag: Int = -1

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_now
            if (Perms.missing(this).isNotEmpty()) {
                permLauncher.launch(Perms.required())
            }
        } else {
            currentTag = savedInstanceState.getInt("tab", R.id.nav_now)
            binding.bottomNav.selectedItemId = currentTag
        }
    }

    private fun showTab(id: Int) {
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()
        fm.fragments.forEach { tx.hide(it) }
        val tag = "tab_$id"
        var f = fm.findFragmentByTag(tag)
        if (f == null) {
            f = when (id) {
                R.id.nav_objects -> ObjectsFragment()
                R.id.nav_map -> MapFragment()
                R.id.nav_alerts -> AlertsFragment()
                else -> NowFragment()
            }
            tx.add(R.id.container, f, tag)
        } else {
            tx.show(f)
        }
        tx.commit()
        currentTag = id
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", currentTag)
    }
}
