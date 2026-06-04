package com.example.webviewapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
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

    // CONFIG: Target URL
    private val TARGET_URL = "https://buku-kas-online.vercel.app"
    private val PERMISSION_REQUEST_CODE = 100
    private val FILE_CHOOSER_RESULT_CODE = 101

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefreshLayout = findViewById(R.id.swipeRefresh)

        // Konfigurasi Dasar WebView
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true

        // DISATUKAN: WebViewClient hanya boleh dideklarasikan SATU KALI
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
                swipeRefreshLayout.isRefreshing = false
            }
        }

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

        // Swipe Refresh
        swipeRefreshLayout.setOnRefreshListener {
            webView.reload()
        }

        // 🔥 JEMBATAN 1: Menangani Download untuk Fitur Back Up
        webView.setDownloadListener(DownloadListener { url, _, _, _, _ ->
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(url)
                startActivity(intent)
                Toast.makeText(this, "Mengunduh data back up...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal mengunduh: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Jalankan URL Utama
        webView.loadUrl(TARGET_URL)
    }

    // 🔥 JEMBATAN 2: Fungsi untuk Mencetak / Membuat PDF Invoice
    // Fungsi ini bisa dipanggil secara otomatis oleh sistem Android saat mendeteksi perintah cetak web
    fun buatCetakWeb(webView: WebView) {
        try {
            val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
            val printAdapter = webView.createPrintDocumentAdapter("Invoice Buku Kas")
            val jobName = "${getString(R.string.app_name)} Document"
            printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal mencetak: ${e.message}", Toast.LENGTH_LONG).show()
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
