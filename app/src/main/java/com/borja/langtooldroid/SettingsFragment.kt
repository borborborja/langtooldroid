package com.borja.langtooldroid

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.borja.langtooldroid.databinding.FragmentSettingsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Load saved values
        binding.etServerUrl.setText(prefs.getString("server_url", getString(R.string.default_server)))
        binding.etLanguage.setText(prefs.getString("language_code", "auto"))
        binding.etMotherTongue.setText(prefs.getString("mother_tongue", "Select..."))

        // Language Selector Logic
        binding.etLanguage.setOnClickListener {
            val dialog = LanguageSelectionDialog()
            dialog.initiallySelectedCodes = binding.etLanguage.text.toString().split(",")
            dialog.onLanguagesSelected = { codes ->
                binding.etLanguage.setText(codes.joinToString(","))
            }
            dialog.show(parentFragmentManager, "LanguageSelector")
        }

        // Mother Tongue Selector
        binding.etMotherTongue.setOnClickListener {
            val dialog = LanguageSelectionDialog()
            dialog.isSingleSelection = true
            dialog.onSingleLanguageSelected = { code ->
                binding.etMotherTongue.setText(code)
            }
            dialog.show(parentFragmentManager, "MotherTongueSelector")
        }

        // Toggles
        binding.swPicky.isChecked = prefs.getBoolean("picky_mode", false)
        binding.swWifiOnly.isChecked = prefs.getBoolean("wifi_only", false)

        binding.btnRules.setOnClickListener {
            startActivity(Intent(requireContext(), RulesActivity::class.java))
        }

        binding.btnClipboard.setOnClickListener {
            startActivity(Intent(requireContext(), ClipboardActivity::class.java))
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
                .putString("language_code", if(language.isBlank()) "auto" else language) // Validation 1
                .putString("mother_tongue", if(motherTongue == "Select...") "" else motherTongue)
                .putBoolean("picky_mode", binding.swPicky.isChecked)
                .putBoolean("wifi_only", binding.swWifiOnly.isChecked)
                .apply()

            // FIX: Ensure language is never empty
            val testLang = if (language.contains(",") || language.isBlank()) "auto" else language
            testConnection(url, testLang)
        }
        
        binding.btnOpenSettingsWarning.setOnClickListener { openSpellCheckerSettings() }
        
        checkSpellCheckerServiceStatus()
    }
    
    override fun onResume() {
        super.onResume()
        checkSpellCheckerServiceStatus()
    }

    private fun checkSpellCheckerServiceStatus() {
        binding.cardSetupWarning.visibility = View.VISIBLE
    }

    private fun openSpellCheckerSettings() {
        try {
            val intent = Intent("android.settings.SPELL_CHECKER_SETTINGS")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not open settings directly", Toast.LENGTH_SHORT).show()
        }
    }

    private fun testConnection(url: String, language: String) {
        binding.tvStatus.text = getString(R.string.connection_testing)
        binding.tvStatus.setTextColor(requireContext().getColor(R.color.md_theme_light_primary))
        binding.btnSave.isEnabled = false
        binding.pbLoading.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Defensive: Ensure we never send an empty language
                val safeLang = if (language.isNullOrBlank() || language.length < 2) "auto" else language
                
                // For debugging locally if needed
                withContext(Dispatchers.Main) {
                   // binding.tvStatus.text = "Checking with: $safeLang..."
                }

                // simple check with a dummy text
                val params = HashMap<String, String>()
                params["text"] = "Hello world"
                params["language"] = safeLang
                
                LanguageToolClient.getApi(url).check(params)
                
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = getString(R.string.connection_success)
                    binding.tvStatus.setTextColor(requireContext().getColor(R.color.md_theme_light_primary))
                    Toast.makeText(requireContext(), "Saved!", Toast.LENGTH_SHORT).show()
                    binding.btnSave.isEnabled = true
                    binding.pbLoading.visibility = View.GONE
                }
            } catch (e: Exception) {
                // ERROR HANDLING with details
                val errorMessage = if (e is retrofit2.HttpException) {
                    try {
                        e.response()?.errorBody()?.string() ?: e.message()
                    } catch (e2: Exception) { e.message }
                } else {
                    e.localizedMessage
                }

                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = getString(R.string.connection_failed, errorMessage)
                    binding.tvStatus.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
                    binding.btnSave.isEnabled = true
                    binding.pbLoading.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
