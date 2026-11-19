package com.pc5.tvlauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Hosts the unchanged PC5 web UI (app/web) in a WebView and replaces the Python core with [Backend].
 * The page talks to window.webkit.messageHandlers.couch exactly like on Linux; a small shim forwards it here.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var backend: Backend
    private val worker = Executors.newSingleThreadExecutor()

    private val shim = """
        (function () {
          // Polyfills for older Android TV WebViews (Chrome < 98)
          if (typeof window.structuredClone !== "function") {
            window.structuredClone = function (v) { return v === undefined ? undefined : JSON.parse(JSON.stringify(v)); };
          }
          if (!Object.hasOwn) Object.hasOwn = function (o, k) { return Object.prototype.hasOwnProperty.call(o, k); };
          if (!Array.prototype.at) Array.prototype.at = function (i) { i = Math.trunc(i) || 0; if (i < 0) i += this.length; return this[i]; };
          // Show startup errors on the TV itself
          function showErr(msg) {
            try {
              var d = document.getElementById("pc5-err");
              if (!d) {
                d = document.createElement("pre"); d.id = "pc5-err";
                d.style.cssText = "position:fixed;left:24px;right:24px;bottom:24px;max-height:45vh;overflow:auto;z-index:2147483647;" +
                  "background:rgba(120,0,0,.92);color:#fff;font:14px/1.4 monospace;padding:12px;border-radius:8px;white-space:pre-wrap";
                (document.body || document.documentElement).appendChild(d);
              }
              d.textContent += msg + "\n";
            } catch (e) {}
          }
          window.addEventListener("error", function (e) { showErr("Error: " + e.message + " @ " + (e.filename || "") + ":" + (e.lineno || "")); });
          window.addEventListener("unhandledrejection", function (e) { var r = e.reason; showErr("Promise: " + (r && (r.stack || r.message) || r)); });
          setTimeout(function () {
            if (!window.__couchReady) showErr("UI did not finish starting (WebView " + navigator.userAgent.replace(/.*Chrome\/([0-9.]+).*/, "Chrome $1") + ")");
          }, 8000);
          if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.couch) return;
          var seq = 0, pending = {};
          window.__couchReply = function (id, raw) {
            var p = pending[id]; if (!p) return; delete pending[id]; p(raw);
          };
          window.webkit = { messageHandlers: { couch: { postMessage: function (text) {
            return new Promise(function (resolve) { var id = ++seq; pending[id] = resolve; CouchNative.post(id, String(text)); });
          } } } };
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        setContentView(web)

        backend = Backend(this) { event, payload -> dispatch(event, payload) }

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/web/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/art/", backend.artHandler)
            .build()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            textZoom = 100
        }
        web.addJavascriptInterface(Native(), "CouchNative")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, shim, setOf("*"))
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                // Fallback for old WebViews without document-start scripts
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) view.evaluateJavascript(shim, null)
            }
        }
        web.webChromeClient = object : android.webkit.WebChromeClient() {
            // Hide WebView's grey "play" placeholder on <video> elements without a poster
            override fun getDefaultVideoPoster(): android.graphics.Bitmap =
                android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)

            override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                android.util.Log.i("PC5", "[console] ${m.message()} @ ${m.sourceId()}:${m.lineNumber()}")
                return true
            }
        }
        web.loadUrl("https://appassets.androidplatform.net/web/index.html?role=home")
        web.requestFocus()
    }

    inner class Native {
        @JavascriptInterface
        fun post(id: Int, text: String) {
            worker.execute {
                val reply = backend.handle(text)
                val js = "window.__couchReply($id, ${JSONObject.quote(reply.toString())})"
                web.post { web.evaluateJavascript(js, null) }
            }
        }
    }

    fun dispatch(event: String, payload: Any?) {
        val p = payload?.toString() ?: "{}"
        web.post { web.evaluateJavascript("window.__couch && window.__couch.dispatch(${JSONObject.quote(event)}, $p)", null) }
    }

    // Remote / gamepad -> PC5 pad events (same names the Linux input service sends)
    private fun buttonFor(code: Int): String? = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> "up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BUTTON_A -> "cross"
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_B -> "circle"
        KeyEvent.KEYCODE_BUTTON_X -> "square"
        KeyEvent.KEYCODE_BUTTON_Y -> "triangle"
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_START -> "options"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "create"
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_MEDIA_REWIND -> "l1"
        KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "r1"
        KeyEvent.KEYCODE_BUTTON_L2 -> "l2"
        KeyEvent.KEYCODE_BUTTON_R2 -> "r2"
        KeyEvent.KEYCODE_BUTTON_THUMBL -> "l3"
        KeyEvent.KEYCODE_BUTTON_THUMBR -> "r3"
        else -> null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val button = buttonFor(event.keyCode) ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            dispatch("pad", JSONObject().put("button", button).put("repeat", event.repeatCount > 0))
        }
        return true // the page handles every mapped key; Back never leaves the launcher
    }

    // HOME pressed while we are already in front: jump back to the PC5 home screen
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) dispatch("ps", JSONObject().put("kind", "long"))
    }

    override fun onResume() {
        super.onResume()
        backend.onResume()
    }

    override fun onDestroy() {
        worker.shutdown()
        web.destroy()
        super.onDestroy()
    }
}
