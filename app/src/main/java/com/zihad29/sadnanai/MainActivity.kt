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
        } catch (e: Exception) {}
    }

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
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
        } catch (e: Exception) {}
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
        fun pickFile() {
            runOnUiThread { filePicker.launch(arrayOf("*/*")) }
        }

        @JavascriptInterface
        fun pickFolder() {
            runOnUiThread { folderPicker.launch(null) }
        }
    }
}
