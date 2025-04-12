package com.url.expander

import kotlinx.coroutines.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.textfield.TextInputEditText
import com.url.expander.databinding.ActivityMainBinding
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent != null) {
            handleIncomingShareIntent(intent)
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        findViewById<Button>(R.id.expand_button).setOnClickListener {
            fetchUrlAsync(
                findViewById<TextInputEditText>(R.id.url_textinput).text.toString()
            ) {result -> findViewById<TextView>(R.id.output_url_textview).setText(result)}
        }
        findViewById<Button>(R.id.paste_button).setOnClickListener {
            pasteTextFromClipboard(findViewById(R.id.url_textinput))
        }
        val outputUrlTextview: TextView = findViewById(R.id.output_url_textview)
        findViewById<Button>(R.id.copy_button).setOnClickListener {
            copyTextToClipboard(outputUrlTextview.text.toString())
        }
        findViewById<Button>(R.id.open_button).setOnClickListener {
            val url = extractUrlFromText(
                outputUrlTextview.text.toString().replace("http://", "https://")
            ).toString()
            if (url.isNotEmpty()) {
                openUrlInBrowser(url)
            } else {
                Toast.makeText(this, "No URL to open!", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.share_button).setOnClickListener {
            shareText(outputUrlTextview.text.toString())
        }
    }

    private fun handleIncomingShareIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            fetchUrlAsync(
                intent.getStringExtra(Intent.EXTRA_TEXT).toString()
            ) {result ->
                copyTextToClipboard(result)
                shareText(result)
            }
        }
    }
    private fun fetchUrlAsync(url: String, callback: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            var result = fetchUrl(url)
            withContext(Dispatchers.Main) {
                if (result != null) {
                    callback(result)
                }
            }
        }
    }

    private fun pasteTextFromClipboard(textInputEditText: EditText) {
        val clipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val item = clip.getItemAt(0)
            val clipboardText = item.text.toString()
            if (!TextUtils.isEmpty(clipboardText)) {
                textInputEditText.setText(clipboardText)
            }
        }
    }
    private fun copyTextToClipboard(textToCopy: String) {
        val clipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Copied Text", textToCopy)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(
            this, "Clean URL copied to clipboard.", Toast.LENGTH_SHORT
        ).show()
    }
    private fun openUrlInBrowser(url: String) {
        Log.w("URL",url.toUri().toString())
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivity(intent)
    }
    private fun shareText(text: String) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(shareIntent, "Share Clean URL via"))
    }
}
fun extractUrlFromText(text: String): String? {
    val urlPattern = Pattern.compile(
        "(https?://(?:www\\.)?[\\w-]+(?:\\.[\\w-]+)+[/#?&=\\w-]*)",
        Pattern.CASE_INSENSITIVE
    )
    val matcher = urlPattern.matcher(text)
    return if (matcher.find()) {
        matcher.group(0)  // Returns the first match
    } else {
        null  // No URL found
    }
}
fun getCleanUrl(url: String): String {
    return try {
        var urlObject = URL(url)
        "${urlObject.protocol}://${urlObject.host}${urlObject.path}"
    } catch (e: Exception) {
        e.printStackTrace()
        "invalid URL!"
    }
}
fun fetchUrl(text: String): String? {
    val httpsstext = text.replace("http://", "https://")
    val originalUrl = extractUrlFromText(httpsstext)
        ?: return null  // Extract URL from input text
    var connection: HttpURLConnection? = null
    try {
        val urlObject = URL(originalUrl)
        connection = urlObject.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false  // Prevent automatic redirects
        connection.requestMethod = "GET"  // Set method to GET
        val responseCode = connection.responseCode
        Log.w("RES", "response Code: $responseCode")

        // Check for HTTP redirects (301 or 302)
        if (
            responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
            responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
            responseCode == 307
        ) {
            Log.w("LOC", connection.getHeaderField("Location").toString())
            return httpsstext.replace(
                originalUrl,
                " " + getCleanUrl(
                    connection.getHeaderField("Location")
                ) + " "
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        connection?.disconnect()
    }
    return null  // No redirect found
}