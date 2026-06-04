package com.example.webaswapp

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var mWebView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    // Menangani Fitur MENCARI FILE (Penting untuk Fitur Pulihkan Data)
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
            fileChooserCallback?.onReceiveValue(results)
        } else {
            fileChooserCallback?.onReceiveValue(null)
        }
        fileChooserCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inisialisasi komponen sesuai layout XML asli Anda
        mWebView = findViewById(R.id.mWebView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        // Konfigurasi WebView Settings
        val settings = mWebView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        // Menangani Navigasi Halaman Web
        mWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    view.loadUrl(url)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = ProgressBar.GONE
                swipeRefreshLayout.isRefreshing = false
            }
        }

        // Menangani Fitur File Upload / Pulihkan Data agar bekerja di APK
        mWebView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileChooserCallback = filePathCallback
                val intent = fileChooserParams.createIntent()
                try {
                    fileChooserLauncher.launch(intent)
                    return true
                } catch (e: Exception) {
                    fileChooserCallback = null
                    return false
                }
            }
        }

        // Fitur Geser ke Bawah untuk Muat Ulang (Swipe to Refresh)
        swipeRefreshLayout.setOnRefreshListener {
            mWebView.reload()
        }

        // MENANGANI BACKUP DATA (Mencegat Blob URL & Mengonversinya ke File JSON asli)
        mWebView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            if (url.startsWith("blob:")) {
                val jsBlobConverter = """
                    javascript:
                    var xhr = new XMLHttpRequest();
                    xhr.open('GET', '$url', true);
                    xhr.responseType = 'blob';
                    xhr.onload = function(e) {
                        if (this.status == 200) {
                            var blob = this.response;
                            var reader = new FileReader();
                            reader.readAsDataURL(blob);
                            reader.onloadend = function() {
                                var base64data = reader.result;
                                Android.prosesBase64Backup(base64data);
                            }
                        }
                    };
                    xhr.send();
                """.trimIndent()
                
                mWebView.loadUrl(jsBlobConverter)
                Toast.makeText(this, "Memproses data backup...", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal membuka tautan unduhan", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Menangani Tombol Back Fisik HP agar tidak langsung keluar aplikasi
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mWebView.canGoBack()) {
                    mWebView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Hubungkan Interface JavaScript
        mWebView.addJavascriptInterface(WebAppInterface(this), "Android")
        
        // Memuat alamat Web Buku Kas Anda
        mWebView.loadUrl("https://buku-kas-online.vercel.app")
    }

    // FUNGSI UTAMA CETAK PDF (Aman dari Crash Thread)
    fun buatCetak() {
        runOnUiThread {
            try {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = mWebView.createPrintDocumentAdapter("Invoice Buku Kas")
                printManager.print("Invoice Buku Kas", printAdapter, PrintAttributes.Builder().build())
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Gagal memuat printer: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // FUNGSI UTAMA MENYIMPAN BACKUP JSON
    fun simpanFileBackup(base64Data: String) {
        try {
            val pureBase64 = if (base64Data.contains(",")) base64Data.split(",")[1] else base64Data
            val fileBytes = android.util.Base64.decode(pureBase64, android.util.Base64.DEFAULT)
            
            val namaFile = "Backup_Full_Kas_${System.currentTimeMillis()}.json"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, namaFile)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(fileBytes)
                    }
                }
            } else {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadDir, namaFile)
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
                Toast.makeText(this, "Gagal menyimpan file backup: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// CLASS INTERFACE JAVASCRIPT (Berada di luar MainActivity)
class WebAppInterface(private val mContext: MainActivity) {

    @JavascriptInterface
    fun printInvoice() {
        mContext.buatCetak()
    }

    @JavascriptInterface
    fun prosesBase64Backup(base64Data: String) {
        mContext.simpanFileBackup(base64Data)
    }
}
