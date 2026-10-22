# MT5 Groq Analyzer — Android APK (MVP)

Aplikasi Android **native Java**, `XAUUSDm` / `M5`, OCR lokal + API Groq `openai/gpt-oss-120b`.
**Tidak menggunakan WebView, tidak bergantung pada Google Play Services, tidak melakukan order trading otomatis.**

## Fitur yang sudah diimplementasikan

| Fitur | Status |
|---|---|
| Input Groq API key dengan Android Keystore AES-256-GCM | ✅ Kode tersedia |
| Impor screenshot dari galeri (Storage Access Framework, tanpa storage permission) | ✅ Kode tersedia |
| Izin MediaProjection, foreground service, screenshot satu frame | ✅ Kode tersedia |
| Floating overlay draggable dengan scan dan ringkasan hasil | ✅ Kode tersedia |
| OCR on-device ML Kit *bundled Latin model* (AOSP) | ✅ Kode tersedia |
| Parse `XAUUSDm`, `M5`, `RSI(14)`, `ATR(14)` dan harga jika terbaca | ✅ Kode tersedia |
| Konfirmasi/edit angka harga/RSI/ATR/EMA20/EMA50/MACD manual sebelum analisis | ✅ Kode tersedia |
| JSON indikator → Groq chat completions `gpt-oss-120b`, JSON Schema strict | ✅ Kode tersedia |
| Hasil BUY / SELL / WAIT, alasan, risiko, data yang kurang | ✅ Kode tersedia |
| GitHub Actions unit test + build APK + unduh artifact | ✅ Workflow tersedia |
| AI melihat langsung pola candle visual dari gambar (vision) | **Belum**; model yang dipilih **text-only** |
| Membaca EMA20/50 dari piksel warna, OHLC historis, feed harga broker | **Belum** |
| Auto trading / order ke MT5 | **Sengaja tidak ada** |

**Catatan validasi:** proyek berisi source lengkap dan konfigurasi CI, tetapi environment pembuat source tidak tersedia Android SDK/Gradle atau akses unduh dependency, sehingga APK dan hasil build CI **belum dapat diklaim berhasil** sampai workflow GitHub Actions dijalankan. Tidak ada APK palsu di arsip.

## Build APK melalui GitHub Actions

1. Buat **repository GitHub kosong**, mis. `mt5-groq-analyzer` (disarankan privat).
2. Extract `MT5-Groq-Analyzer.zip` lalu **upload semua isi direktori**, termasuk folder `.github`, `app`, file `settings.gradle`, `build.gradle`, `gradle.properties` ke root repository. Bisa lewat Termux/Git (lihat di bawah).
3. Pastikan branch bernama `main`, `master`, atau jalankan manual workflow.
4. Buka **Actions → Build Android APK → Run workflow**. Tunggu sampai job hijau.
5. Di halaman run, scroll **Artifacts** → unduh `MT5-Groq-Analyzer-debug-APK.zip`, ekstrak lalu instal `app-debug.apk` di Android.

Workflow memasang JDK17, Android SDK API35, Gradle 8.9, menjalankan test dan assemble. Artifact 30 hari.

**Contoh upload via Termux setelah Git terpasang** (isi akun/repo sendiri, jangan masukkan Groq key ke repository):

```bash
cd /path/hasil-ekstrak/MT5-Groq-Analyzer
git init
git branch -M main
git add .
git commit -m "Initial Android Groq analyzer"
git remote add origin https://github.com/USERNAME/mt5-groq-analyzer.git
git push -u origin main
```

## Cara menggunakan APK

1. Buka APK → masukkan **Groq API key** (dari console.groq.com, bukan langganan ChatGPT Plus) → **Simpan API key**.
2. **Direkomendasikan pertama:** tap `Impor screenshot galeri`, pilih screenshot MT5 **yang hanya memuat chart**, cek hasil pembacaan RSI/ATR/harga, kemudian tap `Analisis dengan Groq`.
3. Untuk pengambilan layar langsung, aktifkan floating overlay (`Tampilkan di atas aplikasi lain`), buka MT5, ketuk bubble `◈ AI` → `SCAN MT5` → setujui popup screen share Android. Pindah/biarkan MT5 tampil selama countdown ~3 detik; hasil akan muncul di overlay.
4. Pada Android 14/15, untuk mode **share satu aplikasi**, pilih aplikasi MT5 di dialog bawaan. Sistem meminta consent **setiap sesi** screen capture.
5. Jika hasil OCR salah/tidak lengkap, gunakan galeri dan perbaiki angka manual. **Jangan eksekusi trading dari angka OCR yang belum diverifikasi.**

## JSON yang dikirim ke Groq

Snapshot contoh:

```json
{
  "source": "manual_confirmed_ocr",
  "symbol": "XAUUSDm",
  "timeframe": "M5",
  "price": 4100.273,
  "unverified_price_from_ocr": null,
  "rsi14": 62.36,
  "atr14": 7.056,
  "ema20": null,
  "ema50": null,
  "macd_histogram": null,
  "market_structure_user_input": "BEARISH",
  "notes_user_input": "Harga pullback di sekitar EMA50; belum ada rejection closed candle.",
  "price_confirmed_by_user": true,
  "timestamp_utc_ms": 1792660000000,
  "ocr_lines_normalized": []
}
```

Panggilan `POST https://api.groq.com/openai/v1/chat/completions`, model `openai/gpt-oss-120b`, `response_format.type=json_schema`, strict mode. Balasan JSON wajib punya `decision`, `trend`, `confidence`, `summary`, `reasons`, `entry_condition`, `stop_loss_guidance`, `take_profit_guidance`, `risks`, dan `missing_data`.

**Penting:** model GPT-OSS 120B **bukan vision model**. Tidak mengirim file screenshot ke model tersebut. OCR lokal tidak mampu menangkap pola candle, EMA crossing, momentum historis, atau level support/resistance secara andal. Field tanpa bukti tidak boleh dipaksakan. Dengan snapshot minimal, AI diinstruksikan memberi **WAIT** dan menyebutkan data yang hilang. Untuk analisis berbasis candlestick yang jauh lebih baik, perlu integrasi data OHLC dari broker/EA, atau pipeline vision terpisah dengan validasi ulang.

## Privasi dan keamanan

- Key dimasukkan lewat aplikasi dan dienkripsi Android Keystore AES-GCM; tidak ada key statis dalam app/source/action.
- **Build APK publik tidak boleh berisi key.** Jika mempublikasikan aplikasi untuk banyak orang, gunakan backend proxy + autentikasi/rate-limiting alih-alih client BYOK.
- File gambar diproses di memori untuk OCR, tidak disimpan dalam galeri dan tidak diunggah ke Groq.
- Hanya JSON berisi indikator dan **baris OCR dengan label pasar yang masuk whitelist** dikirim via HTTPS. Pastikan screenshot hanya berisi chart; jangan sertakan akun/email/chat sensitif dalam kolom catatan.
- State JSON hasil analisis tersimpan lokal di SharedPreferences untuk ditampilkan kembali. Android backup dimatikan.
- Hanya mengambil satu screenshot saat izin aktif; tidak ada monitoring diam-diam, tidak ada auto BUY/SELL.
- Overlay di beberapa ROM/AOSP bisa diblokir oleh pengelola baterai; jalur galeri adalah fallback.

## Teknologi

- Android Java17, minSdk 26, targetSdk 35, compileSdk 35.
- Gradle plugin 8.7.3; Gradle 8.9 (dipasang otomatis oleh Actions).
- ML Kit `com.google.mlkit:text-recognition:16.0.1` **bundled**.
- Java `HttpURLConnection`, Android Keystore, `MediaProjection`, `ImageReader`, `TYPE_APPLICATION_OVERLAY`.

## Struktur

```
.github/workflows/android-apk.yml    # build Android di GitHub
app/build.gradle                    # konfigurasi Android + ML Kit
app/src/main/AndroidManifest.xml    # izin screenshot/overlay/network
app/src/main/java/id/danzku/mt5analyzer/
  MainActivity.java                 # UI, import, konfirmasi OCR, analisis manual
  CaptureService.java              # MediaProjection sekali per consent
  OverlayService.java              # bubble draggable + output
  OcrEngine.java                    # ML Kit bundled OCR
  ChartTextParser.java              # parser RSI, ATR, quote, instrument
  Snapshot.java                     # schema input json
  GroqClient.java                   # HTTPS + JSON Schema strict
  KeyVault.java                     # AES-GCM via Android Keystore
  AnalysisState.java                # state antara layar dan overlay
  UiKit.java                        # komponen UI native
app/src/test/...                    # unit tests parser / format input
```

## Checklist pengujian sesudah Actions hijau

- [ ] APK berhasil install di Android 10 / 13 / 14 / 15 (prioritaskan versi HP sendiri).
- [ ] Groq key valid: query dari tombol **Analisis** berhasil, key 401 dan rate-limit 429 tampil jelas.
- [ ] Galeri: screenshot landscape dan portrait, simbol M5/RSI/ATR terbaca, angka bisa dikoreksi.
- [ ] Screen capture: grant izin, pindah ke MT5, hasil muncul tanpa merekam terus-menerus.
- [ ] Overlay: izin, drag, SCAN, tampil hasil, tutup, pindah aplikasi.
- [ ] Izin ditolak: aplikasi tetap berjalan, bisa gunakan galeri.
- [ ] OCR gagal: error ditampilkan dan pengguna bisa mengulang.
- [ ] Field kosong memicu WAIT, tidak ada angka/level fiktif.
- [ ] Perangkat tanpa Google Play Services dapat OCR (model bundled), izin overlay bergantung ROM.

## Referensi

- https://console.groq.com/docs/model/openai/gpt-oss-120b
- https://console.groq.com/docs/structured-outputs
- https://developer.android.com/media/grow/media-projection
- https://developers.google.com/ml-kit/vision/text-recognition/v2/android

Project by DANZKU LAB — experimental decision support, bukan nasihat investasi atau jaminan profit.
