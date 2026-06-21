const CACHE_NAME = 'buku-kas-v8-cache';
// Daftar file yang wajib disimpan agar aplikasi bisa terbuka saat offline
const ASSETS_TO_CACHE = [
  '/',
  '/index.html',
  '/manifest.json',
  '/icon-192.png',
  '/icon-512.png'
];

// 1. Tahap Instalasi: Simpan semua aset utama ke dalam cache HP
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      console.log('Mengunduh aset untuk mode offline...');
      return cache.addAll(ASSETS_TO_CACHE);
    })
  );
  self.skipWaiting();
});

// 2. Tahap Aktivasi: Bersihkan cache versi lama jika ada pembaruan
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames.map((cache) => {
          if (cache !== CACHE_NAME) {
            console.log('Menghapus cache usang:', cache);
            return caches.delete(cache);
          }
        })
      );
    })
  );
  self.clients.claim();
});

// 3. Tahap Pengambilan Data (Strategi Network First, Fallback to Cache)
// Mencoba mengambil data terbaru dari internet dulu (supaya Supabase tetap sinkron),
// jika internet mati/gagal, langsung ambil dari cache lokal HP.
self.addEventListener('fetch', (event) => {
  // Hanya tangani permintaan internal website (bukan request luar seperti ke Supabase)
  if (event.request.url.startsWith(self.location.origin)) {
    event.respondWith(
      fetch(event.request)
        .then((response) => {
          // Jika sukses mendapat data segar dari internet, perbarui salinan di cache
          if (response.status === 200) {
            const responseClone = response.clone();
            caches.open(CACHE_NAME).then((cache) => {
              cache.put(event.request, responseClone);
            });
          }
          return response;
        })
        .catch(() => {
          // Jika internet mati (fetch gagal), ambil dari cache HP
          return caches.match(event.request).then((cachedResponse) => {
            if (cachedResponse) {
              return cachedResponse;
            }
            // Jika benar-benar tidak ada di cache, arahkan ke halaman utama
            return caches.match('/index.html');
          });
        })
    );
  }
});
