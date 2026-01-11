package com.borja.langtooldroid

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import com.google.gson.annotations.SerializedName

data class CheckResponse(
    @SerializedName("matches") val matches: List<Match>
)

data class Match(
    @SerializedName("message") val message: String,
    @SerializedName("shortMessage") val shortMessage: String,
    @SerializedName("offset") val offset: Int,
    @SerializedName("length") val length: Int,
    @SerializedName("replacements") val replacements: List<Replacement>,
    @SerializedName("rule") val rule: Rule
)

data class Replacement(
    @SerializedName("value") val value: String
)

data class Rule(
    @SerializedName("id") val id: String,
    @SerializedName("description") val description: String,
    @SerializedName("issueType") val issueType: String
)

interface LanguageToolApi {
    @FormUrlEncoded
    @POST("v2/check")
    suspend fun check(
        @retrofit2.http.FieldMap options: Map<String, String>
    ): CheckResponse
}

object LanguageToolClient {
    private var retrofit: Retrofit? = null
    private var currentBaseUrl: String = ""

    fun getApi(baseUrl: String): LanguageToolApi {
        // Ensure trailing slash
        val validUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        
        if (retrofit == null || currentBaseUrl != validUrl) {
            currentBaseUrl = validUrl
            retrofit = Retrofit.Builder()
                .baseUrl(validUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(LanguageToolApi::class.java)
    }
}
