# Exambro SMA Paramartha (Android Client)

Aplikasi **Exambro** (Exam Browser) berbasis Android khusus untuk SMA Paramartha. Aplikasi ini dirancang sebagai sistem pengunci (*Kiosk Mode / Lock Task Mode*) bagi siswa saat melangsungkan Computer Based Test (CBT) untuk mencegah kecurangan.

Repositori ini berisi *Source Code* aplikasi Android (Kotlin) yang terhubung secara penuh dengan **Web Dashboard SMA Paramartha**.

---

## 🔒 Fitur Keamanan Anti-Curang (Exambro)

- **Kiosk Mode (Lock Task):** Mengunci perangkat Android ke dalam aplikasi ini. Siswa tidak dapat menekan tombol *Home*, *Back*, atau *Recent Apps* selama sesi ujian berlangsung.
- **Screen & Camera Sharing (Real-time):** Memanfaatkan fitur kamera depan dan tangkapan layar untuk mengirimkan aktivitas siswa berupa gambar/frame secara otomatis (setiap beberapa detik) ke Firebase Realtime Database. Pengawas dapat memantaunya langsung melalui Web Dashboard.
- **Blokir Notifikasi & Split Screen:** Mencegah notifikasi chat yang mengganggu dan memblokir upaya siswa untuk membuka dua aplikasi sekaligus (Split-Screen).
- **Auto-KICK & Peringatan:** Aplikasi ini terus memantau *command* dari Firebase. Jika Pengawas dari Web menekan tombol "KICK", maka WebView aplikasi Android ini akan otomatis tertutup dan sesi ujian dibatalkan.
- **Dynamic Config (exam.json):** Aplikasi ini tidak di-hardcode dengan URL statis. Exambro akan mengambil konfigurasi dari `exam.json` yang di-host pada server Web, memungkinkan pembaruan URL tujuan secara dinamis tanpa perlu update APK.
- **QR Code Scanner Terintegrasi:** Siswa dapat melakukan login absensi hanya dengan melakukan scan QR Code pada kartu ujian.

---

## 🔄 Sistem Pembaruan Otomatis (OTA / In-App Update)

Aplikasi ini memiliki fitur **Force Update** otomatis. Jika Anda ingin merilis pembaruan:
1. Buka repositori GitHub ini, masuk ke tab **Actions**, dan jalankan workflow **Release Exambro OTA Update** (`main.yml`).
2. Masukkan *Version Code*, *Version Name*, dan **Apa yang baru? (Changelog)**.
3. Tunggu hingga GitHub selesai me-rakit APK dan mengirimkannya ke grup Telegram Anda. Salin *link* unduhan APK tersebut.
4. Buka file `exam.json` di Vercel Anda, lalu tambahkan/ubah konfigurasi update:
   ```json
   {
     "targetUrl": "https://elearning.smaparamartha.sch.id/",
     "latest_version_code": 6,
     "changelog": "- Fitur Anti-Hacker\n- Tampilan baru",
     "apk_url": "https://link-download-apk-anda.com/app.apk"
   }
   ```
5. Saat siswa membuka aplikasi, mereka akan langsung melihat *Pop-Up Wajib Update* beserta *Changelog*-nya. Aplikasi akan mengunduh dan menginstal APK secara mandiri.

---

## 🛠️ Persyaratan Sistem (Requirement)

- **Android Studio** (Koala / Ladybug atau versi terbaru disarankan).
- **Minimum SDK:** API 24 (Android 7.0 Nougat).
- **Target SDK:** API 34 (Android 14).
- **Bahasa Pemrograman:** Kotlin 1.9+.
- **Firebase Project:** Pastikan Web dan Android menggunakan Firebase Realtime Database yang sama.

---

## 🚀 Cara Menjalankan Project (Build & Run)

1. **Clone Repositori:**
   ```bash
   git clone https://github.com/username/exambro-paramartha.git
   ```
2. **Buka di Android Studio:**
   Buka folder proyek ini menggunakan Android Studio dan biarkan proses sinkronisasi Gradle (Gradle Sync) selesai.
3. **Ubah Konfigurasi URL (Penting!):**
   Buka file `app/src/main/java/com/smaparamartha/exambro/MainActivity.kt`.
   Cari fungsi `fetchDynamicConfig()` dan pastikan URL mengarah ke hosting Web CBT yang aktif. Contoh:
   ```kotlin
   val url = URL("https://paramartaapp.vercel.app/exam.json")
   ```
4. **Jalankan Aplikasi:**
   Hubungkan perangkat Android (fisik) via kabel USB (aktifkan USB Debugging) atau gunakan Emulator. Klik tombol ▶️ **Run 'app'** di Android Studio.

---

## 📡 Bagaimana Screen-Share Bekerja?

Exambro tidak menggunakan sistem *Video Call* WebRTC yang boros bandwidth, melainkan menggunakan teknik **Image Frame Uploading**:

1. Web View mengeksekusi JavaScript melalui antarmuka `AndroidWebAppInterface`.
2. Kamera Android / WebView di-*capture* menjadi format `Base64`.
3. String Base64 tersebut diunggah langsung ke simpul Firebase Realtime Database (`pantau_ujian/[ID_UJIAN]/[NISN]/frame`).
4. Web Dashboard Admin mengambil frame terbaru dan me-rendernya di layar pengawas.

Pendekatan ini sangat ringan (hemat kuota) untuk dijalankan di HP siswa, namun tetap memberikan kejelasan aktivitas layar dan wajah untuk panitia di ruang pengawas.

---

## 🤝 Kontribusi

Aplikasi ini dikembangkan secara spesifik untuk kebutuhan ujian internal. Jika ada fitur tambahan yang ingin dikembangkan (seperti deteksi wajah menggunakan Machine Learning atau perbaikan UI), silakan ubah kode di `MainActivity.kt` atau layout di `activity_main.xml`.
