package com.borja.langtooldroid

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.borja.langtooldroid.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        // Load saved values
        binding.etServerUrl.setText(prefs.getString("server_url", getString(R.string.default_server)))
        binding.etLanguage.setText(prefs.getString("language_code", "auto"))

        // Language Selector Logic
        val languages = arrayOf("auto", "en-US", "es-ES", "fr-FR", "de-DE", "it-IT", "pt-PT", "ru-RU", "zh-CN", "ja-JP", "ca-ES", "gl-ES", "eu-ES", "nl-NL", "pl-PL", "ro-RO", "uk-UA")
        val selectedLanguages = BooleanArray(languages.size)
        // Pre-select saved languages
        val savedLangs = prefs.getString("language_code", "auto")?.split(",") ?: listOf("auto")
        for (i in languages.indices) {
             if (savedLangs.contains(languages[i])) {
                 selectedLanguages[i] = true
             }
        }

        binding.etLanguage.setOnClickListener {
            val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            builder.setTitle(getString(R.string.settings_language))
            builder.setMultiChoiceItems(languages, selectedLanguages) { dialog, which, isChecked ->
                selectedLanguages[which] = isChecked
                if (languages[which] == "auto" && isChecked) {
                     for (i in languages.indices) {
                         if (i != which) {
                             selectedLanguages[i] = false
                             (dialog as? androidx.appcompat.app.AlertDialog)?.listView?.setItemChecked(i, false)
                         }
                     }
                } else if (isChecked) {
                      val autoIndex = languages.indexOf("auto")
                      if (autoIndex != -1 && selectedLanguages[autoIndex]) {
                          selectedLanguages[autoIndex] = false
                           (dialog as? androidx.appcompat.app.AlertDialog)?.listView?.setItemChecked(autoIndex, false)
                      }
                }
            }
            builder.setPositiveButton("OK") { _, _ ->
                val selectedList = mutableListOf<String>()
                for (i in languages.indices) {
                    if (selectedLanguages[i]) {
                        selectedList.add(languages[i])
                    }
                }
                if (selectedList.isEmpty()) {
                    selectedList.add("auto")
                    selectedLanguages[languages.indexOf("auto")] = true
                }
                binding.etLanguage.setText(selectedList.joinToString(","))
            }
            builder.setNegativeButton("Cancel", null)
            builder.show()
        }

        // Mother Tongue Selector
        binding.etMotherTongue.setText(prefs.getString("mother_tongue", "Select..."))
        binding.etMotherTongue.setOnClickListener {
             val builder = androidx.appcompat.app.AlertDialog.Builder(this)
             builder.setTitle("Select Native Language")
             val codes = languages.drop(1).toTypedArray() // Remove 'auto'
             builder.setItems(codes) { _, which ->
                 binding.etMotherTongue.setText(codes[which])
             }
             builder.show()
        }

        // Toggles
        binding.swPicky.isChecked = prefs.getBoolean("picky_mode", false)
        binding.swWifiOnly.isChecked = prefs.getBoolean("wifi_only", false)

        binding.btnRules.setOnClickListener {
            startActivity(Intent(this, RulesActivity::class.java))
        }

        binding.btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        binding.btnClipboard.setOnClickListener {
            startActivity(Intent(this, ClipboardActivity::class.java))
        }

        binding.btnSave.setOnClickListener {
            val url = binding.etServerUrl.text.toString().trim()
            val language = binding.etLanguage.text.toString().trim()
            val motherTongue = binding.etMotherTongue.text.toString()

            if (url.isEmpty()) {
                binding.tfServerUrl.error = "URL cannot be empty"
                return@setOnClickListener
            }
            binding.tfServerUrl.error = null

            prefs.edit()
                .putString("server_url", url)
                .putString("language_code", language)
                .putString("mother_tongue", if(motherTongue == "Select...") "" else motherTongue)
                .putBoolean("picky_mode", binding.swPicky.isChecked)
                .putBoolean("wifi_only", binding.swWifiOnly.isChecked)
                .apply()

            val testLang = if (language.contains(",")) "auto" else language
            testConnection(url, testLang)
        }

        binding.btnOpenSettings.setOnClickListener { openSpellCheckerSettings() }
        binding.btnOpenSettingsWarning.setOnClickListener { openSpellCheckerSettings() }
    }

    override fun onResume() {
        super.onResume()
        checkSpellCheckerServiceStatus()
    }

    private fun openSpellCheckerSettings() {
        try {
            val intent = Intent("android.settings.SPELL_CHECKER_SETTINGS")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open settings directly", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkSpellCheckerServiceStatus() {
        // It's hard to know exactly if WE are the selected one without inspecting secure settings (which needs permission).
        // However, we can at least guide the user.
        // For now, always show the warning if this is a fresh install or just show it until user explicitly dismisses it?
        // Better: Just show the card. The text says "You must enable...". 
        // If we want to be smarter, uses TextServicesManager but public API is limited.
        // Let's rely on visibility. 
        binding.cardSetupWarning.visibility = android.view.View.VISIBLE
    }

    private fun testConnection(url: String, language: String) {
        binding.tvStatus.text = getString(R.string.connection_testing)
        binding.tvStatus.setTextColor(getColor(R.color.md_theme_light_primary))
        binding.btnSave.isEnabled = false
        binding.pbLoading.visibility = android.view.View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // simple check with a dummy text
                LanguageToolClient.getApi(url).check("Hello world", language)
                
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = getString(R.string.connection_success)
                    binding.tvStatus.setTextColor(getColor(R.color.md_theme_light_primary))
                    Toast.makeText(this@MainActivity, "Saved!", Toast.LENGTH_SHORT).show()
                    binding.btnSave.isEnabled = true
                    binding.pbLoading.visibility = android.view.View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = getString(R.string.connection_failed, e.localizedMessage)
                    binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                    binding.btnSave.isEnabled = true
                    binding.pbLoading.visibility = android.view.View.GONE
                }
            }
        }
    }
}
