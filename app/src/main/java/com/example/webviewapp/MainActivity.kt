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
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
    private val FILE_CHOOSER_RESULT_CODE = 101

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

        // 🛡️ Menggunakan WebResourceRequest (Standar SDK Modern) untuk bypass 'overrides nothing'
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

        // 🛡️ Parameter Non-Null murni sesuai format cetak biru Gradle masa kini
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: WebChromeClient.FileChooserParams
            ): Boolean {
                this@MainActivity.filePathCallback = filePathCallback
                val intent = fileChooserParams.createIntent()
                try {
                    startActivityForResult(intent, FILE_CHOOSER_RESULT_CODE)
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

        // 📥 FITUR DOWNLOAD (Pemberangus Ambiguitas Lambda)
        webView.setDownloadListener { url, _, _, _, _ ->
            if (!url.isNullOrEmpty()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    Toast.makeText(this, "Memproses unduhan back up...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal mengunduh", Toast.LENGTH_SHORT).show()
                }
            }
        }

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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun buatCetakWeb() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val printManager = getSystemService(Context.PRINT_SERVICE) as? PrintManager
                val printAdapter = webView.createPrintDocumentAdapter("Invoice Buku Kas")
                printManager?.print("Invoice_Document", printAdapter, PrintAttributes.Builder().build())
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                val printManager = getSystemService(Context.PRINT_SERVICE) as? PrintManager
                @Suppress("DEPRECATION")
                val printAdapter = webView.createPrintDocumentAdapter()
                printManager?.print("Invoice_Document", printAdapter, PrintAttributes.Builder().build())
            } else {
                Toast.makeText(this, "Fitur cetak tidak didukung perangkat ini", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Gagal memuat printer", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            val results = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            // 🛡️ SOLUSI TYPE MISMATCH: Ditambahkan operator '?: emptyArray()' agar compiler tidak protes eror Nullable!
            filePathCallback?.onReceiveValue(results ?: emptyArray())
            filePathCallback = null
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
