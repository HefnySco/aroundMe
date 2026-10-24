package com.aroundme.app.ui

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aroundme.app.App
import com.aroundme.app.databinding.ActivitySettingsBinding
import com.aroundme.app.util.Prefs
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        title = "Settings"

        val prefs = Prefs(this)
        b.switchMerge.isChecked = prefs.groupByName
        b.switchMerge.setOnCheckedChangeListener { _, v -> prefs.groupByName = v }

        b.switchHideNameless.isChecked = prefs.hideNamelessBt
        b.switchHideNameless.setOnCheckedChangeListener { _, v -> prefs.hideNamelessBt = v }

        b.btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear collected data?")
                .setMessage("All observed devices, sightings and alerts will be deleted. Objects and links are kept.")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        val db = (application as App).db
                        db.sightingDao().clearAll()
                        db.deviceDao().clearAll()
                        db.alertDao().clearAll()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
