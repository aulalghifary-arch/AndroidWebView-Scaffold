package com.example.webviewapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    
    // BUG FIX: Media penampung untuk data file yang dipilih user
    private var uploadMessage: ValueCallback<Array<Uri>>? = null

    // BUG FIX: Sistem peluncur jendela File Manager HP
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val results = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            uploadMessage?.onReceiveValue(results)
        } else {
            uploadMessage?.onReceiveValue(null)
        }
        uploadMessage = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        swipeRefreshLayout = cariSwipeRefresh(findViewById(android.R.id.content))

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        swipeRefreshLayout?.setOnRefreshListener {
            webView.reload()
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefreshLayout?.isRefreshing = false
                view?.evaluateJavascript("window.print = function() { Android.printInvoice(); };", null)
            }
        }

        // BUG FIX: Mengganti WebChromeClient standar agar bisa merespons tombol "Pulihkan Data"
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                uploadMessage?.onReceiveValue(null)
                uploadMessage = filePathCallback

                val intent = fileChooserParams?.createIntent()
                try {
                    fileChooserLauncher.launch(intent)
                } catch (e: Exception) {
                    uploadMessage = null
                    Toast.makeText(this@MainActivity, "Gagal membuka file manager", Toast.LENGTH_SHORT).show()
                    return false
                }
                return true
            }
        }

        webView.addJavascriptInterface(WebAppInterface(this), "Android")
        webView.loadUrl("https://buku-kas-online.vercel.app")

        webView.setDownloadListener { url, _, _, _, _ ->
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                val jsDataConverter = """
                    (function() {
                        var xhr = new XMLHttpRequest();
                        xhr.open('GET', '$url', true);
                        xhr.responseType = 'blob';
                        xhr.onload = function() {
                            if (xhr.status === 200) {
                                var reader = new FileReader();
                                reader.readAsDataURL(xhr.response);
                                reader.onloadend = function() {
                                    var base64Data = reader.result;
                                    Android.prosesDownload(base64Data);
                                }
                            }
                        };
                        xhr.send();
                    })();
                """.trimIndent()
                webView.evaluateJavascript(jsDataConverter, null)
                Toast.makeText(this, "Memproses file dokumen...", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal membuka tautan download", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun cariSwipeRefresh(view: View): SwipeRefreshLayout? {
        if (view is SwipeRefreshLayout) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = cariSwipeRefresh(view.getChildAt(i))
                if (child != null) return child
            }
        }
        return null
    }

    fun buatCetak() {
        runOnUiThread {
            try {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = webView.createPrintDocumentAdapter("Dokumen Buku Kas")
                printManager.print("Dokumen Buku Kas", printAdapter, PrintAttributes.Builder().build())
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Gagal memuat sistem printer", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun simpanFile(base64Data: String) {
        try {
            val mimeType = if (base64Data.contains(";")) {
                base64Data.substringAfter("data:").substringBefore(";")
            } else {
                "application/octet-stream"
            }

            val pureBase64 = base64Data.substringAfter("base64,")
            val fileBytes = Base64.decode(pureBase64, Base64.DEFAULT)

            val extension = when {
                mimeType.contains("pdf") -> ".pdf"
                mimeType.contains("json") -> ".json"
                mimeType.contains("png") -> ".png"
                mimeType.contains("jpeg") || mimeType.contains("jpg") -> ".jpg"
                else -> ".bin"
            }

            val fileName = "BukuKas_${System.currentTimeMillis()}$extension"
            val mimeTypeToSave = if (extension == ".pdf") "application/pdf" else if (extension == ".json") "application/json" else "application/octet-stream"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeTypeToSave)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(fileBytes)
                    }
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(fileBytes)
                }
            }

            runOnUiThread {
                Toast.makeText(this, "Berhasil! Cek folder Download di HP Anda.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Gagal menyimpan file ke memori", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class WebAppInterface(private val mContext: MainActivity) {
    @JavascriptInterface
    fun printInvoice() {
        mContext.buatCetak()
    }

    @JavascriptInterface
    fun prosesDownload(base64Data: String) {
        mContext.simpanFile(base64Data)
    }
}
