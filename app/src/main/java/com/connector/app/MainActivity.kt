package com.connector.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private val mainHandler = Handler(Looper.getMainLooper())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val streams = ConcurrentHashMap<String, EventSource>()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                textZoom = 100
            }
            addJavascriptInterface(NativeBridge(), "AndroidBridge")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false
                    return if (url.startsWith("file://") || url.startsWith("data:")) {
                        false
                    } else {
                        view?.loadUrl(url)
                        true
                    }
                }
            }
            webChromeClient = WebChromeClient()
            setBackgroundColor(0xFFFAFAFA.toInt())
        }
        setContentView(webView)
        loadUi()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun loadUi() {
        val html = readAsset("chat.html")
        if (html != null && html.length > 2000 && !html.contains("PLACEHOLDER")) {
            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                html,
                "text/html",
                "utf-8",
                null
            )
        } else {
            webView.loadDataWithBaseURL(
                null,
                """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{font-family:system-ui;padding:24px;background:#fafafa;color:#111;line-height:1.5}
code{background:#eee;padding:2px 6px;border-radius:6px}</style></head>
<body>
<h2>Нужен chat.html</h2>
<p>Скопируйте полный файл из архива <b>ConnectorApp-native-bridge.zip</b> в:</p>
<p><code>app/src/main/assets/chat.html</code></p>
<p>Затем: Build → Rebuild Project.</p>
</body></html>""".trimIndent(),
                "text/html",
                "utf-8",
                null
            )
        }
    }

    private fun readAsset(name: String): String? {
        return try {
            assets.open(name).use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun js(code: String) {
        mainHandler.post { webView.evaluateJavascript(code, null) }
    }

    private fun escapeJs(s: String): String = JSONObject.quote(s)

    inner class NativeBridge {
        @JavascriptInterface
        fun httpPost(requestId: String, url: String, headersJson: String, body: String) {
            thread {
                try {
                    val headers = JSONObject(headersJson)
                    val rb = body.toRequestBody("application/json; charset=utf-8".toMediaType())
                    val builder = Request.Builder().url(url).post(rb)
                    val keys = headers.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        builder.header(k, headers.getString(k))
                    }
                    httpClient.newCall(builder.build()).execute().use { response ->
                        val respBody = response.body?.string() ?: ""
                        js("window.__nativeHttpDone && window.__nativeHttpDone(${escapeJs(requestId)}, ${response.code}, ${escapeJs(respBody)})")
                    }
                } catch (e: Exception) {
                    js("window.__nativeHttpError && window.__nativeHttpError(${escapeJs(requestId)}, ${escapeJs(e.message ?: "Network error")})")
                }
            }
        }

        @JavascriptInterface
        fun streamPost(requestId: String, url: String, headersJson: String, body: String) {
            thread {
                try {
                    val headers = JSONObject(headersJson)
                    val rb = body.toRequestBody("application/json; charset=utf-8".toMediaType())
                    val builder = Request.Builder().url(url).post(rb)
                    val keys = headers.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        builder.header(k, headers.getString(k))
                    }
                    val request = builder.build()
                    val listener = object : EventSourceListener() {
                        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                            if (data == "[DONE]") {
                                streams.remove(requestId)
                                eventSource.cancel()
                                js("window.__nativeStreamDone && window.__nativeStreamDone(${escapeJs(requestId)})")
                                return
                            }
                            js("window.__nativeStreamChunk && window.__nativeStreamChunk(${escapeJs(requestId)}, ${escapeJs(data)})")
                        }
                        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                            streams.remove(requestId)
                            val msg = (t?.message ?: "Stream error") + (response?.let { " (HTTP ${it.code})" } ?: "")
                            js("window.__nativeStreamError && window.__nativeStreamError(${escapeJs(requestId)}, ${escapeJs(msg)})")
                        }
                        override fun onClosed(eventSource: EventSource) {
                            if (streams.remove(requestId) != null) {
                                js("window.__nativeStreamDone && window.__nativeStreamDone(${escapeJs(requestId)})")
                            }
                        }
                    }
                    streams[requestId] = EventSources.createFactory(httpClient).newEventSource(request, listener)
                } catch (e: Exception) {
                    js("window.__nativeStreamError && window.__nativeStreamError(${escapeJs(requestId)}, ${escapeJs(e.message ?: "Network error")})")
                }
            }
        }

        @JavascriptInterface
        fun httpGetHeaders(requestId: String, url: String, headersJson: String) {
            thread {
                try {
                    val headers = JSONObject(headersJson)
                    val builder = Request.Builder().url(url)
                    val keys = headers.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        builder.header(k, headers.getString(k))
                    }
                    httpClient.newCall(builder.build()).execute().use { response ->
                        val respBody = response.body?.string() ?: ""
                        js("window.__nativeHttpDone && window.__nativeHttpDone(${escapeJs(requestId)}, ${response.code}, ${escapeJs(respBody)})")
                    }
                } catch (e: Exception) {
                    js("window.__nativeHttpError && window.__nativeHttpError(${escapeJs(requestId)}, ${escapeJs(e.message ?: "Network error")})")
                }
            }
        }

        @JavascriptInterface
        fun cancelStream(requestId: String) {
            streams.remove(requestId)?.cancel()
        }

        @JavascriptInterface
        fun httpGet(requestId: String, url: String) {
            thread {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (compatible; ConnectorBot/1.0)")
                        .build()
                    httpClient.newCall(request).execute().use { response ->
                        val respBody = response.body?.string() ?: ""
                        js("window.__nativeHttpDone && window.__nativeHttpDone(${escapeJs(requestId)}, ${response.code}, ${escapeJs(respBody)})")
                    }
                } catch (e: Exception) {
                    js("window.__nativeHttpError && window.__nativeHttpError(${escapeJs(requestId)}, ${escapeJs(e.message ?: "Network error")})")
                }
            }
        }
    }

    override fun onDestroy() {
        streams.values.forEach { it.cancel() }
        streams.clear()
        webView.destroy()
        super.onDestroy()
    }
}
