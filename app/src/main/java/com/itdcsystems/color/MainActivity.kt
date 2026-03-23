package com.itdcsystems.color

import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.itdcsystems.color.ui.theme.ColorSplashTheme
import java.io.OutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ColorSplashTheme {
                ColorSplashApp()
            }
        }
    }
}

private const val COLOR_URL = "https://color.itdcsystems.com/"

@Composable
fun ColorSplashApp() {
    var isLoading by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        ColorSplashWebView(
            url = COLOR_URL,
            onWebViewCreated = { webView = it },
            onPageStarted = { isLoading = true },
            onPageFinished = { isLoading = false },
            onProgress = { loadingProgress = it }
        )

        AnimatedVisibility(
            visible = isLoading,
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            LinearProgressIndicator(
                progress = { loadingProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            )
        }
    }
}

/**
 * JavaScript interface that receives base64-encoded image data from the WebView
 * and saves it to the device gallery via MediaStore.
 */
class ImageSaver(private val activity: ComponentActivity) {

    @JavascriptInterface
    fun saveBase64Image(base64Data: String, mimeType: String) {
        try {
            // Strip the data URL prefix if present (e.g., "data:image/png;base64,")
            val pureBase64 = if (base64Data.contains(",")) {
                base64Data.substringAfter(",")
            } else {
                base64Data
            }

            val bytes = Base64.decode(pureBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            if (bitmap != null) {
                saveBitmapToGallery(bitmap, mimeType)
            } else {
                // Not a bitmap — save raw bytes (e.g., SVG)
                saveRawBytesToGallery(bytes, mimeType)
            }
        } catch (e: Exception) {
            activity.runOnUiThread {
                Toast.makeText(activity, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, mimeType: String) {
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "png"
        val fileName = "ColorSplash_${System.currentTimeMillis()}.$extension"

        val compressFormat = when {
            mimeType.contains("jpeg") || mimeType.contains("jpg") -> Bitmap.CompressFormat.JPEG
            mimeType.contains("webp") -> Bitmap.CompressFormat.WEBP_LOSSY
            else -> Bitmap.CompressFormat.PNG
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType.ifEmpty { "image/png" })
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ColorSplash")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = activity.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
            val outputStream: OutputStream? = resolver.openOutputStream(uri)
            outputStream?.use { bitmap.compress(compressFormat, 95, it) }

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            activity.runOnUiThread {
                Toast.makeText(activity, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveRawBytesToGallery(bytes: ByteArray, mimeType: String) {
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "png"
        val fileName = "ColorSplash_${System.currentTimeMillis()}.$extension"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType.ifEmpty { "image/png" })
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ColorSplash")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = activity.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            activity.runOnUiThread {
                Toast.makeText(activity, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ColorSplashWebView(
    url: String,
    onWebViewCreated: (WebView) -> Unit,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    onProgress: (Int) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                val activity = context as ComponentActivity
                val imageSaver = ImageSaver(activity)

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowContentAccess = true
                settings.mediaPlaybackRequiresUserGesture = false

                addJavascriptInterface(imageSaver, "AndroidImageSaver")

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onPageStarted()
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onPageFinished()

                        // Inject JS to intercept blob and data URL downloads
                        view?.evaluateJavascript("""
                            (function() {
                                if (window._colorSplashDownloadPatched) return;
                                window._colorSplashDownloadPatched = true;

                                // Intercept clicks on <a> elements with download attribute
                                var origCreateElement = document.createElement.bind(document);
                                document.createElement = function(tag) {
                                    var el = origCreateElement(tag);
                                    if (tag.toLowerCase() === 'a') {
                                        var origClick = el.click.bind(el);
                                        el.click = function() {
                                            if (el.href && el.hasAttribute('download')) {
                                                var href = el.href;
                                                if (href.startsWith('blob:')) {
                                                    fetch(href).then(function(r) { return r.blob(); }).then(function(blob) {
                                                        var reader = new FileReader();
                                                        reader.onloadend = function() {
                                                            AndroidImageSaver.saveBase64Image(reader.result, blob.type || 'image/png');
                                                        };
                                                        reader.readAsDataURL(blob);
                                                    });
                                                    return;
                                                } else if (href.startsWith('data:')) {
                                                    var mimeMatch = href.match(/^data:([^;,]+)/);
                                                    var mime = mimeMatch ? mimeMatch[1] : 'image/png';
                                                    AndroidImageSaver.saveBase64Image(href, mime);
                                                    return;
                                                }
                                            }
                                            origClick();
                                        };
                                    }
                                    return el;
                                };

                                // Also intercept window.open for blob URLs
                                var origOpen = window.open;
                                window.open = function(url) {
                                    if (url && url.startsWith('blob:')) {
                                        fetch(url).then(function(r) { return r.blob(); }).then(function(blob) {
                                            var reader = new FileReader();
                                            reader.onloadend = function() {
                                                AndroidImageSaver.saveBase64Image(reader.result, blob.type || 'image/png');
                                            };
                                            reader.readAsDataURL(blob);
                                        });
                                        return null;
                                    }
                                    return origOpen.apply(this, arguments);
                                };

                                // Intercept URL.createObjectURL to track blob URLs
                                var origCreateObjectURL = URL.createObjectURL;
                                URL.createObjectURL = function(blob) {
                                    var url = origCreateObjectURL(blob);
                                    window._lastBlobUrl = url;
                                    window._lastBlob = blob;
                                    return url;
                                };
                            })();
                        """.trimIndent(), null)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val requestUrl = request?.url?.toString() ?: return false

                        // Handle data: URLs (base64 images)
                        if (requestUrl.startsWith("data:image")) {
                            val mimeMatch = Regex("^data:([^;,]+)").find(requestUrl)
                            val mime = mimeMatch?.groupValues?.get(1) ?: "image/png"
                            imageSaver.saveBase64Image(requestUrl, mime)
                            return true
                        }

                        return !requestUrl.startsWith("https://color.itdcsystems.com")
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        onProgress(newProgress)
                    }
                }

                // Handle regular HTTP/HTTPS file downloads
                setDownloadListener { downloadUrl, _, contentDisposition, mimeType, _ ->
                    if (downloadUrl.startsWith("data:")) {
                        val mime = Regex("^data:([^;,]+)").find(downloadUrl)
                            ?.groupValues?.get(1) ?: mimeType
                        imageSaver.saveBase64Image(downloadUrl, mime)
                    } else if (downloadUrl.startsWith("blob:")) {
                        // Fetch the blob via JS and pass to our saver
                        evaluateJavascript("""
                            (function() {
                                fetch('$downloadUrl').then(function(r) { return r.blob(); }).then(function(blob) {
                                    var reader = new FileReader();
                                    reader.onloadend = function() {
                                        AndroidImageSaver.saveBase64Image(reader.result, blob.type || 'image/png');
                                    };
                                    reader.readAsDataURL(blob);
                                });
                            })();
                        """.trimIndent(), null)
                    } else {
                        // Regular URL download — fetch and save
                        Thread {
                            try {
                                val connection = java.net.URL(downloadUrl).openConnection()
                                connection.connect()
                                val bytes = connection.getInputStream().readBytes()
                                val base64 = Base64.encodeToString(bytes, Base64.DEFAULT)
                                val actualMime = mimeType ?: "image/png"
                                imageSaver.saveBase64Image(
                                    "data:$actualMime;base64,$base64",
                                    actualMime
                                )
                            } catch (e: Exception) {
                                activity.runOnUiThread {
                                    Toast.makeText(activity, "Download failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }.start()
                    }
                }

                onWebViewCreated(this)
                loadUrl(url)
            }
        }
    )
}
