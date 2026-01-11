package com.borja.langtooldroid

data class GitHubRelease(
    val tag_name: String,
    val html_url: String,
    val name: String,
    val body: String
)
