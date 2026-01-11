package com.borja.langtooldroid

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClipboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No layout needed, just a dialog
        title = "Checking Clipboard..."
        
        checkClipboard()
    }

    private fun checkClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip() || !clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)!!) {
             Toast.makeText(this, "Clipboard is empty or not text", Toast.LENGTH_SHORT).show()
             finish()
             return
        }

        val text = clipboard.primaryClip?.getItemAt(0)?.text.toString()
        if (text.isBlank()) {
            Toast.makeText(this, "Clipboard text is blank", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val serverUrl = prefs.getString("server_url", getString(R.string.default_server)) ?: getString(R.string.default_server)
        val languageCodeRaw = prefs.getString("language_code", "auto") ?: "auto"
        val language = if (languageCodeRaw.contains(",")) "auto" else languageCodeRaw
        val disabledCats = prefs.getString("disabled_categories", "") ?: ""
        val pickyMode = prefs.getBoolean("picky_mode", false)
        val motherTongue = prefs.getString("mother_tongue", "") ?: ""
        val level = if (pickyMode) "picky" else "default"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val params = HashMap<String, String>()
                params["text"] = text
                params["language"] = language
                params["level"] = level
                if (motherTongue.isNotEmpty()) params["motherTongue"] = motherTongue
                if (disabledCats.isNotEmpty()) params["disabledCategories"] = disabledCats
                
                val response = LanguageToolClient.getApi(serverUrl).check(params)

                withContext(Dispatchers.Main) {
                    showResults(text, response.matches)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                     Toast.makeText(this@ClipboardActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                     finish()
                }
            }
        }
    }

    private fun showResults(text: String, matches: List<Match>) {
        if (matches.isEmpty()) {
             Toast.makeText(this, "No issues found!", Toast.LENGTH_SHORT).show()
             finish()
             return
        }

        val sb = StringBuilder()
        sb.append("Found ${matches.size} issues:\n\n")
        
        for (match in matches) {
            val errorText = text.substring(match.offset, (match.offset + match.length).coerceAtMost(text.length))
            sb.append("• \"$errorText\": ${match.message}\n")
            if (match.replacements.isNotEmpty()) {
                sb.append("  Slope: ${match.replacements.joinToString(", ") { it.value }}\n")
            }
            sb.append("\n")
        }

        AlertDialog.Builder(this)
            .setTitle("Clipboard Results")
            .setMessage(sb.toString())
            .setPositiveButton("OK") { _, _ -> finish() }
            .setOnDismissListener { finish() }
            .show()
    }
}
