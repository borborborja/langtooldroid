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
                
                // Logic: If 'auto' is checked, uncheck others? Or handle in save?
                // Let's keep it simple: if 'auto' is selected, it takes precedence eventually.
                if (languages[which] == "auto" && isChecked) {
                     // Optionally uncheck others for UI clarity
                     for (i in languages.indices) {
                         if (i != which) {
                             selectedLanguages[i] = false
                             (dialog as? androidx.appcompat.app.AlertDialog)?.listView?.setItemChecked(i, false)
                         }
                     }
                } else if (isChecked) {
                     // If specific language check, uncheck 'auto'
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

            // If multiple languages -> test with 'auto' or just the first one?
            // For testing connection, we use the first valid code or auto
            val testLang = if (language.contains(",")) "auto" else language
            testConnection(url, testLang)
        }

        binding.btnOpenSettings.setOnClickListener {
            try {
                val intent = Intent("android.settings.SPELL_CHECKER_SETTINGS")
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open settings directly", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun testConnection(url: String, language: String) {
        binding.tvStatus.text = "Testing connection..."
        binding.btnSave.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // simple check with a dummy text
                LanguageToolClient.getApi(url).check("Hello world", language)
                
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = getString(R.string.connection_success)
                    binding.tvStatus.setTextColor(getColor(R.color.md_theme_light_primary))
                    Toast.makeText(this@MainActivity, "Saved!", Toast.LENGTH_SHORT).show()
                    binding.btnSave.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = getString(R.string.connection_failed, e.localizedMessage)
                    binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                    binding.btnSave.isEnabled = true
                }
            }
        }
    }
}
