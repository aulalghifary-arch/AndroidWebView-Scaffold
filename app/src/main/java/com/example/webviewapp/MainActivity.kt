package com.example.webaswapp

import android.app.DownloadManager
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView) // Sesuaikan dengan ID WebView di layout Anda
        
        // Pengaturan WebView
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        // Hubungkan JavaScript Web dengan Android (PENTING)
        webView.addJavascriptInterface(WebAppInterface(this), "Android")

        webView.webViewClient = WebViewClient()

        // MENANGANI BACKUP (Mengubah Blob URL menjadi file JSON di folder Download)
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            if (url.startsWith("blob:")) {
                // Trik: Gunakan JavaScript untuk mengubah Blob menjadi Base64 string, lalu kirim ke Android
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
                
                webView.loadUrl(jsBlobConverter)
                Toast.makeText(this, "Memproses data backup...", Toast.LENGTH_SHORT).show()
            } else {
                // Jika unduhan biasa (bukan blob)
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal membuka tautan unduhan", Toast.LENGTH_SHORT).show()
                }
            }
        }

        webView.loadUrl("https://buku-kas-online.vercel.app") // Sesuaikan URL Anda
    }

    // FUNGSI CETAK: Harus di dalam MainActivity & berjalan di runOnUiThread
    fun buatCetak() {
        runOnUiThread {
            try {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = webView.createPrintDocumentAdapter("Invoice Buku Kas")
                printManager.print("Invoice Buku Kas", printAdapter, PrintAttributes.Builder().build())
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Gagal memuat printer: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // FUNGSI MENYIMPAN JSON: Menyimpan string Base64 menjadi file .json asli
    fun simpanFileBackup(base64Data: String) {
        try {
            // Bersihkan prefix base64 jika ada (contoh: "data:application/json;base64,")
            val pureBase64 = if (base64Data.contains(",")) base64Data.split(",")[1] else base64Data
            val fileBytes = android.util.Base64.decode(pureBase64, android.util.Base64.DEFAULT)
            
            val namaFile = "Backup_Full_Kas_${System.currentTimeMillis()}.json"
            
            // Simpan menggunakan MediaStore agar aman di Android versi baru (Scoped Storage)
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
                // Untuk Android versi lama
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

// CLASS INTERFACE: Letakkan di luar MainActivity (di bagian paling bawah file)
class WebAppInterface(private val mContext: MainActivity) {

    @JavascriptInterface
    fun printInvoice() {
        // Alihkan perintah cetak ke MainActivity agar aman dari crash Thread
        mContext.buatCetak()
    }

    @JavascriptInterface
    fun prosesBase64Backup(base64Data: String) {
        // Alihkan perintah simpan file ke MainActivity
        mContext.simpanFileBackup(base64Data)
    }
}
