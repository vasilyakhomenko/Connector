package com.connector.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private val mainHandler = Handler(Looper.getMainLooper())
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var bridgePickRequestId: String? = null

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // WebChromeClient path
        val cb = fileCallback
        if (cb != null) {
            fileCallback = null
            if (result.resultCode != Activity.RESULT_OK || result.data == null) {
                cb.onReceiveValue(null)
            } else {
                cb.onReceiveValue(extractUris(result.data!!))
            }
            return@registerForActivityResult
        }
        // Native bridge path
        val reqId = bridgePickRequestId
        bridgePickRequestId = null
        if (reqId != null) {
            if (result.resultCode != Activity.RESULT_OK || result.data == null) {
                js("window.__nativeFilesPicked && window.__nativeFilesPicked(${JSONObject.quote(reqId)}, '[]')")
            } else {
                val uris = extractUris(result.data!!)
                thread {
                    val arr = JSONArray()
                    for (uri in uris) {
                        try {
                            val meta = readUriAsAttachment(uri) ?: continue
                            arr.put(meta)
                        } catch (_: Exception) {}
                    }
                    js("window.__nativeFilesPicked && window.__nativeFilesPicked(${JSONObject.quote(reqId)}, ${JSONObject.quote(arr.toString())})")
                }
            }
        }
    }

    private fun extractUris(data: Intent): Array<Uri> {
        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let { uris.add(it) }
            }
        }
        if (uris.isEmpty()) data.data?.let { uris.add(it) }
        return uris.toTypedArray()
    }

    private fun readUriAsAttachment(uri: Uri): JSONObject? {
        val name = resolveDisplayName(uri) ?: "file"
        val mime = contentResolver.getType(uri) ?: "application/octet-stream"
        contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            if (bytes.size > 20_000_000) return null
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            return JSONObject()
                .put("n", name)
                .put("mt", mime)
                .put("d", b64)
        }
        return null
    }

    private fun resolveDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx)
            }
        }
        return uri.lastPathSegment
    }

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
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = filePathCallback
                    return try {
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                        filePicker.launch(Intent.createChooser(intent, "Выберите файл"))
                        true
                    } catch (e: Exception) {
                        fileCallback = null
                        false
                    }
                }
            }
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
                "https://app.local/",
                html,
                "text/html",
                "utf-8",
                null
            )
        } else {
            webView.loadDataWithBaseURL(
                null,
                """<!doctype html><html><head><meta charset=\"utf-8\">
<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">
<style>body{font-family:system-ui;padding:24px}</style></head>
<body><h2>Нужен chat.html в assets</h2></body></html>""".trimIndent(),
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
        fun pickFiles(requestId: String) {
            mainHandler.post {
                bridgePickRequestId = requestId
                try {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    filePicker.launch(Intent.createChooser(intent, "Выберите файл"))
                } catch (e: Exception) {
                    bridgePickRequestId = null
                    js("window.__nativeFilesPicked && window.__nativeFilesPicked(${escapeJs(requestId)}, '[]')")
                }
            }
        }

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
                    builder.header("Accept", "text/event-stream")
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
                            val bodyText = try { response?.body?.string()?.take(500) } catch (_: Exception) { null }
                            val msg = buildString {
                                append(t?.message ?: "Stream error")
                                if (response != null) append(" (HTTP ${response.code})")
                                if (!bodyText.isNullOrBlank()) append("\n").append(bodyText)
                            }
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
