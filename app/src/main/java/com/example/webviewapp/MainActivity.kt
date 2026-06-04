package com.example.webviewapp

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // 🛡️ KUNCI 1: Menggunakan Activity Result API Modern
    // Ini menggantikan fungsi onActivityResult secara total sehingga bebas dari eror "overrides nothing"
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
            filePathCallback?.onReceiveValue(results ?: emptyArray())
        } else {
            filePathCallback?.onReceiveValue(emptyArray())
        }
        filePathCallback = null
    }

    // CONFIG: URL Utama Aplikasi Buku Kas
    private val TARGET_URL = "https://buku-kas-online.vercel.app"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefreshLayout = findViewById(R.id.swipeRefresh)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true

        // 🛡️ KUNCI 2: WebViewClient menggunakan tipe Non-Null murni
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    view.loadUrl(url)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                swipeRefreshLayout.isRefreshing = false
            }
        }

        // 🛡️ KUNCI 3: WebChromeClient menggunakan tipe Non-Null murni
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: WebChromeClient.FileChooserParams
            ): Boolean {
                this@MainActivity.filePathCallback = filePathCallback
                val intent = fileChooserParams.createIntent()
                try {
                    // Menjalankan peluncur file chooser modern
                    fileChooserLauncher.launch(intent)
                    return true
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    return false
                }
            }
        }

        swipeRefreshLayout.setOnRefreshListener {
            webView.reload()
        }

        webView.setDownloadListener { url, _, _, _, _ ->
            if (!url.isNullOrEmpty()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    Toast.makeText(this@MainActivity, "Memproses unduhan back up...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Gagal mengunduh", Toast.LENGTH_SHORT).show()
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Jembatan JavaScript untuk cetak invoice
        webView.addJavascriptInterface(WebAppInterface(this), "Android")

        webView.loadUrl(TARGET_URL)
    }

    // 🖨️ FITUR CETAK (PRINT INVOICE)
    inner class WebAppInterface(private val mContext: Context) {
        @android.webkit.JavascriptInterface
        fun printInvoice() {
            runOnUiThread {
                buatCetakWeb()
            }
        }
    }

    fun buatCetakWeb() {
        try {
            val printManager = getSystemService(Context.PRINT_SERVICE) as? PrintManager
            val printAdapter = webView.createPrintDocumentAdapter("Invoice Buku Kas")
            printManager?.print("Invoice_Document", printAdapter, PrintAttributes.Builder().build())
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Gagal memuat printer", Toast.LENGTH_SHORT).show()
        }
    }
}
