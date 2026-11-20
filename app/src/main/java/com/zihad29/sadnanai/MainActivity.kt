package com.zihad29.sadnanai

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var wv: WebView
    private lateinit var prefs: SharedPreferences

    private val micPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(this, "Mic permission denied", Toast.LENGTH_SHORT).show()
    }

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.edit().putString("folder_uri", uri.toString()).apply()
            Toast.makeText(this, "Folder access granted", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Folder error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                wv.post {
                    wv.evaluateJavascript(
                        "window.onFilePicked && window.onFilePicked(${org.json.JSONObject.quote(uri.toString())}, ${org.json.JSONObject.quote(b64)});",
                        null
                    )
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "File error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("sadnan", MODE_PRIVATE)

        wv = WebView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    request.grant(request.resources)
                }
            }
            addJavascriptInterface(Bridge(), "AndroidBridge")
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(wv)
        SadnanBridge.attach(wv)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPerm.launch(Manifest.permission.RECORD_AUDIO)
        }

        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            startService(Intent(this, OverlayBubbleService::class.java))
        }

        if (!isAccessibilityEnabled()) {
            Toast.makeText(
                this,
                "Enable SADNAN AI in Settings → Accessibility for screen reading",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/${SadnanAccessibilityService::class.java.name}")
    }

    override fun onDestroy() {
        SadnanBridge.detach()
        super.onDestroy()
    }

    inner class Bridge {
        @JavascriptInterface
        fun getApiKey(): String = prefs.getString("api_key", "") ?: ""

        @JavascriptInterface
        fun onApiKeySaved(key: String) {
            prefs.edit().putString("api_key", key).apply()
        }

        @JavascriptInterface
        fun requestMic() {
            runOnUiThread { micPerm.launch(Manifest.permission.RECORD_AUDIO) }
        }

        @JavascriptInterface
        fun openAccessibilitySettings() {
            runOnUiThread {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        @JavascriptInterface
        fun requestOverlay() {
            runOnUiThread {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                } else {
                    startService(Intent(this@MainActivity, OverlayBubbleService::class.java))
                }
            }
        }

        @JavascriptInterface
        fun pickFile() {
            runOnUiThread { filePicker.launch(arrayOf("*/*")) }
        }

        @JavascriptInterface
        fun pickFolder() {
            runOnUiThread { folderPicker.launch(null) }
        }

        @JavascriptInterface
        fun readFileBytes(uriString: String): String {
            return try {
                val bytes = contentResolver.openInputStream(Uri.parse(uriString))?.use { it.readBytes() }
                bytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) } ?: ""
            } catch (e: Exception) { "" }
        }

        @JavascriptInterface
        fun writeFileBytes(uriString: String, base64: String): Boolean {
            return try {
                val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                contentResolver.openOutputStream(Uri.parse(uriString))?.use { it.write(bytes) }
                true
            } catch (e: Exception) { false }
        }

        @JavascriptInterface
        fun downloadZip(name: String, base64: String): Boolean {
            return try {
                val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                val downloads = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                val file = java.io.File(downloads, name)
                file.writeBytes(bytes)
                Toast.makeText(this@MainActivity, "Saved to Downloads/$name", Toast.LENGTH_LONG).show()
                true
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                false
            }
        }

        @JavascriptInterface
        fun shareApk(uriString: String): Boolean {
            return try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(uriString), "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
                true
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Install failed: ${e.message}", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }
}
