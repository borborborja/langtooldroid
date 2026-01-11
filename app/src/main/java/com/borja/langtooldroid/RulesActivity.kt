package com.borja.langtooldroid

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.borja.langtooldroid.databinding.ActivityRulesBinding
import com.google.android.material.switchmaterial.SwitchMaterial

class RulesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRulesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val disabledCats = prefs.getString("disabled_categories", "") ?: ""

        setupSwitch(binding.swCasing, "CASING", disabledCats)
        setupSwitch(binding.swTypo, "TYPOGRAPHY", disabledCats)
        setupSwitch(binding.swStyle, "STYLE", disabledCats)
        setupSwitch(binding.swRedundancy, "REDUNDANCY", disabledCats)
        setupSwitch(binding.swSemantics, "SEMANTICS", disabledCats)
        setupSwitch(binding.swGrammar, "GRAMMAR", disabledCats)
    }

    private fun setupSwitch(switch: SwitchMaterial, categoryId: String, disabledCats: String) {
        // Switch is ON if category is NOT in disabled list
        switch.isChecked = !disabledCats.contains(categoryId)

        switch.setOnCheckedChangeListener { _, isChecked ->
            val currentPrefs = PreferenceManager.getDefaultSharedPreferences(this)
            var currentDisabled = currentPrefs.getString("disabled_categories", "") ?: ""
            val list = if (currentDisabled.isEmpty()) mutableListOf() else currentDisabled.split(",").toMutableList()

            if (isChecked) {
                // Remove from disabled list
                list.remove(categoryId)
            } else {
                // Add to disabled list
                if (!list.contains(categoryId)) {
                    list.add(categoryId)
                }
            }

            currentPrefs.edit().putString("disabled_categories", list.joinToString(",")).apply()
        }
    }
}
