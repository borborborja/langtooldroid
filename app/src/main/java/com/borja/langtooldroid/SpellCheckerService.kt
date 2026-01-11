package com.borja.langtooldroid

import android.service.textservice.SpellCheckerService
import android.util.Log
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import androidx.preference.PreferenceManager
import kotlinx.coroutines.runBlocking
import java.util.Locale

class SpellCheckerService : SpellCheckerService() {

    override fun createSession(): Session {
        return LanguageToolSession()
    }

    private inner class LanguageToolSession : Session() {

        override fun onCreate() {
            // Initialize session if needed
        }

        override fun onGetSuggestions(textInfo: TextInfo?, suggestionsLimit: Int): SuggestionsInfo {
            // Deprecated, but some apps might still call it.
            // We can delegate or return empty.
            return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray())
        }

        override fun onGetSentenceSuggestionsMultiple(
            textInfos: Array<out TextInfo>?,
            suggestionsLimit: Int
        ): Array<SentenceSuggestionsInfo> {
            val result = ArrayList<SentenceSuggestionsInfo>()
            
            if (textInfos == null) return result.toTypedArray()

            val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val serverUrl = prefs.getString("server_url", getString(R.string.default_server)) ?: getString(R.string.default_server)
            // Use saved language or default to auto (e.g., "en-US" or "auto")
            val languageCodeRaw = prefs.getString("language_code", "auto") ?: "auto"
            // If multiple languages are selected (comma separated), fallback to "auto"
            val language = if (languageCodeRaw.contains(",")) "auto" else languageCodeRaw

            for (textInfo in textInfos) {
                val sentence = textInfo.text
                if (sentence.isNullOrBlank()) {
                    result.add(createEmptySentenceSuggestionsInfo())
                    continue
                }

                try {
                    // 1. Wifi Only Check
                    val wifiOnly = prefs.getBoolean("wifi_only", false)
                    if (wifiOnly) {
                        val connManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                        val activeNetwork = connManager.activeNetwork
                        val capabilities = connManager.getNetworkCapabilities(activeNetwork)
                        val isWifi = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
                        if (!isWifi) {
                             // Skip check if wifi only requested and not on wifi
                             result.add(createEmptySentenceSuggestionsInfo())
                             continue
                        }
                    }

                    // 2. User Dictionary Check is complex to do efficiently here without iterating all words.
                    // Instead, we will filter the RESULTS from the API. if a "typo" match exists in UserDictionary, ignore it.
                    
                    val disabledCats = prefs.getString("disabled_categories", "") ?: ""
                    val pickyMode = prefs.getBoolean("picky_mode", false)
                    val motherTongue = prefs.getString("mother_tongue", "") ?: ""
                    val level = if (pickyMode) "picky" else "default"

                    // Synchronous blocking call is required here as the API is synchronous
                    // Ideally we should keep this fast.
                    val checkResponse = runBlocking {
                            val params = HashMap<String, String>()
                            params["text"] = sentence
                            params["language"] = language
                            params["level"] = level
                            params["motherTongue"] = motherTongue
                            params["disabledCategories"] = disabledCats
                            // Add other options if implemented in preferences later
                            
                            LanguageToolClient.getApi(serverUrl).check(params)
                    }

                    val suggestionsInfos = ArrayList<SuggestionsInfo>()
                    val offsets = ArrayList<Int>()
                    val lengths = ArrayList<Int>()

                    for (match in checkResponse.matches) {
                         // Check User Dictionary
                         // We are looking for text covered by match.offset and match.length
                         val errorText = sentence.substring(match.offset, match.offset + match.length)
                         if (isWordInUserDictionary(errorText)) {
                             continue
                         }

                         val replacements = match.replacements.take(suggestionsLimit).map { it.value }.toTypedArray()
                         
                         // Attributes
                         var attributes = 0
                         if (match.rule.issueType == "misspelling") {
                             attributes = SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO
                         } else {
                             // Use RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR (Value 8, added in API 31)
                             // We use the constant if available or raw value 8.
                             // 0x0008 is RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR
                             attributes = 0x0008 
                         }
                         
                         suggestionsInfos.add(SuggestionsInfo(attributes, replacements))
                         
                         offsets.add(match.offset)
                         lengths.add(match.length)
                    }
                    
                    if (suggestionsInfos.isEmpty()) {
                         result.add(createEmptySentenceSuggestionsInfo())
                    } else {
                         result.add(SentenceSuggestionsInfo(
                             suggestionsInfos.toTypedArray(),
                             offsets.toIntArray(),
                             lengths.toIntArray()
                         ))
                    }

                } catch (e: Exception) {
                    Log.e("LTDroid", "Error checking text: ${e.message}")
                    // On error, return empty to avoid crashing the client app
                    result.add(createEmptySentenceSuggestionsInfo())
                }
            }

            return result.toTypedArray()
        }
        
        private fun createEmptySentenceSuggestionsInfo(): SentenceSuggestionsInfo {
            return SentenceSuggestionsInfo(emptyArray(), intArrayOf(), intArrayOf())
        }

        private fun isWordInUserDictionary(word: String): Boolean {
            val cursor = contentResolver.query(
                android.provider.UserDictionary.Words.CONTENT_URI,
                arrayOf(android.provider.UserDictionary.Words.WORD),
                "${android.provider.UserDictionary.Words.WORD} = ?",
                arrayOf(word),
                null
            )
            val exists = cursor?.count ?: 0 > 0
            cursor?.close()
            return exists
        }
    }
}
