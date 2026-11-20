package com.zihad29.sadnanai

import android.webkit.WebView
import org.json.JSONObject
import java.lang.ref.WeakReference

object SadnanBridge {

    @Volatile
    var serviceReady: Boolean = false

    private var webViewRef: WeakReference<WebView>? = null

    fun attach(wv: WebView) {
        webViewRef = WeakReference(wv)
    }

    fun detach() {
        webViewRef = null
    }

    fun pushScreen(text: String, pkg: String) {
        val wv = webViewRef?.get() ?: return
        val t = JSONObject.quote(text)
        val p = JSONObject.quote(pkg)
        wv.post {
            wv.evaluateJavascript(
                "window.onScreenText && window.onScreenText($t, $p);",
                null
            )
        }
    }

    fun pushEvent(pkg: String, text: String, type: String) {
        val wv = webViewRef?.get() ?: return
        val payload = JSONObject().apply {
            put("pkg", pkg)
            put("text", text)
            put("type", type)
            put("ts", System.currentTimeMillis())
        }.toString()
        val quoted = JSONObject.quote(payload)
        wv.post {
            wv.evaluateJavascript(
                "window.onAccessibilityEvent && window.onAccessibilityEvent($quoted);",
                null
            )
        }
    }

    fun pushFileContent(path: String, base64: String) {
        val wv = webViewRef?.get() ?: return
        val p = JSONObject.quote(path)
        val b = JSONObject.quote(base64)
        wv.post {
            wv.evaluateJavascript(
                "window.onFileContent && window.onFileContent($p, $b);",
                null
            )
        }
    }
}
