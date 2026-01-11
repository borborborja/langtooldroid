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
            if (textInfo == null || textInfo.text.isNullOrBlank()) {
                return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray())
            }

            // Reuse the shared logic to get matches
            val matches = checkTextWithApi(textInfo.text)
            
            // If matches found, construct SuggestionsInfo
            if (matches.isNotEmpty()) {
                // For onGetSuggestions (single word usually), we map the first match that covers the word
                // Or simply return the suggestions from the first relevant match.
                // Usually onGetSuggestions is for a single word.
                val match = matches.firstOrNull() // Simplified strategy
                if (match != null) {
                    val attributes = SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO
                    val replacements = match.replacements.take(suggestionsLimit).map { it.value }.toTypedArray()
                    return SuggestionsInfo(attributes, replacements)
                }
            }

            return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray())
        }

        override fun onGetSentenceSuggestionsMultiple(
            textInfos: Array<out TextInfo>?,
            suggestionsLimit: Int
        ): Array<SentenceSuggestionsInfo> {
            val result = ArrayList<SentenceSuggestionsInfo>()
            
            if (textInfos == null) return result.toTypedArray()

            for (textInfo in textInfos) {
                val sentence = textInfo.text
                if (sentence.isNullOrBlank()) {
                    result.add(createEmptySentenceSuggestionsInfo())
                    continue
                }

                try {
                    val matches = checkTextWithApi(sentence)

                    val suggestionsInfos = ArrayList<SuggestionsInfo>()
                    val offsets = ArrayList<Int>()
                    val lengths = ArrayList<Int>()

                    for (match in matches) {
                         val replacements = match.replacements.take(suggestionsLimit).map { it.value }.toTypedArray()
                         
                         // Attributes
                         var attributes = 0
                         if (match.rule.issueType == "misspelling") {
                             attributes = SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO
                         } else {
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
                    result.add(createEmptySentenceSuggestionsInfo())
                }
            }

            return result.toTypedArray()
        }
        
        private fun checkTextWithApi(text: String): List<Match> {
             val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
             val serverUrl = prefs.getString("server_url", getString(R.string.default_server)) ?: getString(R.string.default_server)
             val languageCodeRaw = prefs.getString("language_code", "auto") ?: "auto"
             
             // Determine language
             var language = if (languageCodeRaw.contains(",")) "auto" else languageCodeRaw
             
             // If auto, try to resolve a better locale from the session or system
             if (language == "auto") {
                 // The session locale is the most accurate source for the input language
                 val sessionLocale = this.locale 
                 language = if (!sessionLocale.isNullOrIgnore()) {
                     sessionLocale
                 } else {
                     // Fallback to system default if session provides nothing
                     Locale.getDefault().toLanguageTag()
                 }
                 
                 // Log for debugging
                 Log.d("LTDroid", "Language resolved to: $language (Raw: $languageCodeRaw, Session: $sessionLocale)")
             }
             
             // 1. Wifi Only Check
             val wifiOnly = prefs.getBoolean("wifi_only", false)
             if (wifiOnly) {
                 val connManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                 val activeNetwork = connManager.activeNetwork
                 val capabilities = connManager.getNetworkCapabilities(activeNetwork)
                 val isWifi = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
                 if (!isWifi) {
                     return emptyList()
                 }
             }

             val disabledCats = prefs.getString("disabled_categories", "") ?: ""
             val pickyMode = prefs.getBoolean("picky_mode", false)
             val motherTongue = prefs.getString("mother_tongue", "") ?: ""
             val level = if (pickyMode) "picky" else "default"

             // Blocking call
             return runBlocking {
                 try {
                     val params = HashMap<String, String>()
                     params["text"] = text
                     params["language"] = language
                     params["level"] = level
                     if (motherTongue.isNotEmpty()) params["motherTongue"] = motherTongue
                     if (disabledCats.isNotEmpty()) params["disabledCategories"] = disabledCats
                     
                     val response = LanguageToolClient.getApi(serverUrl).check(params)
                     
                     // Filter User Dictionary
                     response.matches.filter { match ->
                         val errorText = text.substring(match.offset, match.offset + match.length)
                         !isWordInUserDictionary(errorText)
                     }
                 } catch (e: Exception) {
                     Log.e("LTDroid", "API Call failed: ${e.message}")
                     emptyList()
                 }
             }
        }
        
        private fun String?.isNullOrIgnore(): Boolean {
            return this.isNullOrBlank() || this == "auto" || this == "" 
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
