package com.borja.langtooldroid

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater

import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.borja.langtooldroid.databinding.FragmentAboutBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAbout.setOnClickListener {
            startActivity(Intent(requireContext(), AboutActivity::class.java))
        }

        binding.btnOpenSettings.setOnClickListener {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error opening settings", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.btnDebugCheck.setOnClickListener {
            val text = binding.etDebugInput.text.toString()
            if (text.isBlank()) {
                Toast.makeText(requireContext(), "Enter text first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            binding.tvDebugOutput.visibility = View.VISIBLE
            binding.tvDebugOutput.text = "Checking..."
            binding.btnDebugCheck.isEnabled = false
            
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
            val serverUrl = (prefs.getString("server_url", getString(R.string.default_server)) ?: getString(R.string.default_server)).trim()
            // Use 'en' as fallback for debug if auto/system fails, but try to verify 'auto' logic if possible.
            // In Activity context, we don't have 'session locale', so we simulate 'auto' -> System Locale.
            var languageCodeRaw = prefs.getString("language_code", "auto") ?: "auto"
            var languageCode = if (languageCodeRaw.contains(",")) "auto" else languageCodeRaw
            
            if (languageCode == "auto") {
                languageCode = java.util.Locale.getDefault().toLanguageTag()
            }
            
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val sb = StringBuilder()
                sb.append("Config:\n")
                sb.append("Server: $serverUrl\n")
                sb.append("Language: $languageCode\n")
                
                try {
                     val params = HashMap<String, String>()
                     params["text"] = text
                     params["language"] = languageCode
                     
                     val response = LanguageToolClient.getApi(serverUrl).check(params)
                     
                     sb.append("\nAPI Success!\n")
                     sb.append("Matches: ${response.matches.size}\n")
                     response.matches.forEach { match ->
                         sb.append("- ${match.message}\n")
                         sb.append("  Rule: ${match.rule.id}\n")
                         sb.append("  Replacements: ${match.replacements.joinToString { it.value }}\n")
                     }
                     
                } catch (e: HttpException) {
                    sb.append("\nHTTP ERROR ${e.code()}: ${e.message()}\n")
                    try {
                        val errorBody = e.response()?.errorBody()?.string()
                        sb.append("Body: $errorBody\n")
                    } catch (e2: Exception) {
                        sb.append("Could not read error body.\n")
                    }
                } catch (e: Exception) {
                    sb.append("\nERROR: ${e.message}\n")
                    e.printStackTrace()
                }
                
                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                         binding.tvDebugOutput.text = sb.toString()
                         binding.btnDebugCheck.isEnabled = true
                    }
                }
            }
        }
        
        binding.btnViewLogs.setOnClickListener {
            binding.tvDebugOutput.visibility = View.VISIBLE
            val logs = SpellCheckerService.serviceLogs
            if (logs.isEmpty()) {
                binding.tvDebugOutput.text = "No logs found. \n(Service might not be active/bound yet)"
            } else {
                binding.tvDebugOutput.text = logs.joinToString("\n")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
