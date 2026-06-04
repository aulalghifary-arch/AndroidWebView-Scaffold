package com.example.webviewapp

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.DownloadListener
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val TAG = "WebViewApp"
    private val FILE_CHOOSER_RESULT_CODE = 101

    // CONFIG: URL Utama Aplikasi Buku Kas Anda
    private val TARGET_URL = "https://buku-kas-online.vercel.app"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mengembalikan ke layout XML bawaan agar Gradle tidak eror
        setContentView(R.layout.activity_main)

        // Menghubungkan id komponen sesuai template asli
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefreshLayout = findViewById(R.id.swipeRefresh)

        // Konfigurasi performa web standar
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true

        // WebViewClient Tunggal (Aman & Sinkron dengan SwipeRefresh)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    view?.loadUrl(url)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Menghentikan animasi loading refresh saat halaman selesai dimuat
                swipeRefreshLayout.isRefreshing = false
            }
        }

        // WebChromeClient untuk menangani unggah file / pulihkan data
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback = filePathCallback
                val intent = fileChooserParams?.createIntent()
                try {
                    startActivityForResult(intent, FILE_CHOOSER_RESULT_CODE)
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    return false
                }
                return true
            }
        }

        // Fitur Swipe Refresh bawaan template
        swipeRefreshLayout.setOnRefreshListener {
            webView.reload()
        }

        // ==========================================
        // 📥 FITUR Tambahan 1: JEMBATAN DOWNLOAD (BACK UP)
        // ==========================================
        webView.setDownloadListener(DownloadListener { url, _, _, _, _ ->
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(url)
                startActivity(intent)
                Toast.makeText(this, "Mengalihkan ke unduhan back up...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal mengunduh: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Buka URL Aplikasi
        webView.loadUrl(TARGET_URL)
    }

    // ==========================================
    // 🖨️ FITUR Tambahan 2: JEMBATAN CETAK (PRINT INVOICE)
    // ==========================================
    @TargetApi(Build.VERSION_CODES.KITKAT)
    fun buatCetakWeb(webView: WebView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = webView.createPrintDocumentAdapter("Invoice Buku Kas")
                val jobName = "Invoice Buku Kas Document"
                printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal memuat printer: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Fitur cetak tidak didukung pada versi Android ini.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (filePathCallback == null) return
            filePathCallback!!.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            filePathCallback = null
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
