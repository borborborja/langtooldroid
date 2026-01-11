package com.borja.langtooldroid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.borja.langtooldroid.databinding.ActivityAboutBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding
    private val currentVersion = "0.1.0"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvCurrentVersion.text = "Current Version: $currentVersion"

        binding.btnCheckUpdate.setOnClickListener {
            checkForUpdates()
        }
    }

    private fun checkForUpdates() {
        binding.tvUpdateStatus.text = "Checking..."
        binding.btnCheckUpdate.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val latest = GitHubClient.api.getLatestRelease()
                val latestTag = latest.tag_name.removePrefix("v")

                withContext(Dispatchers.Main) {
                    if (latestTag > currentVersion) {
                        binding.tvUpdateStatus.text = "New version available: ${latest.tag_name}"
                        binding.tvUpdateStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                        binding.btnDownloadUpdate.visibility = View.VISIBLE
                        binding.btnDownloadUpdate.setOnClickListener {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(latest.html_url))
                            startActivity(intent)
                        }
                    } else {
                        binding.tvUpdateStatus.text = "You are on the latest version."
                        binding.tvUpdateStatus.setTextColor(getColor(android.R.color.darker_gray))
                        binding.btnDownloadUpdate.visibility = View.GONE
                    }
                    binding.btnCheckUpdate.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvUpdateStatus.text = "Error checking updates."
                    Toast.makeText(this@AboutActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    binding.btnCheckUpdate.isEnabled = true
                }
            }
        }
    }
}
