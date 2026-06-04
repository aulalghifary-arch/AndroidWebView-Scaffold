package com.example.webviewapp

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(WebAppInterface(this), "Android")
        webView.loadUrl("https://buku-kas-online.vercel.app")

        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            if (url.startsWith("data:")) {
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
                webView.loadUrl("javascript:$jsDataConverter")
                Toast.makeText(this, "Memproses data backup...", Toast.LENGTH_SHORT).show()
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

    fun buatCetak() {
        runOnUiThread {
            try {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = webView.createPrintDocumentAdapter("Invoice Buku Kas")
                printManager.print("Invoice Buku Kas", printAdapter, PrintAttributes.Builder().build())
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Gagal memuat printer", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun simpanFileBackup(base64Data: String) {
        try {
            val pureBase64 = if (base64Data.contains(",")) {
                base64Data.substring(base64Data.indexOf(",") + 1)
            } else {
                base64Data
            }
            val fileBytes = Base64.decode(pureBase64, Base64.DEFAULT)
            val fileName = "Backup_Full_Kas_${System.currentTimeMillis()}.json"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
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
                Toast.makeText(this, "Backup berhasil disimpan di folder Download!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Gagal menyimpan file backup", Toast.LENGTH_SHORT).show()
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
        mContext.simpanFileBackup(base64Data)
    }
}
