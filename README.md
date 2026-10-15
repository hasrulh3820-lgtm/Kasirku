# KasirKu UMKM

Aplikasi kasir dan stok barang Android untuk toko kecil. Offline, tanpa login, data tersimpan di HP.

## Status (versi 0.1.0 — Tahap 2)

| Bagian | Status |
| --- | --- |
| Beranda: omzet, modal terjual, laba kotor, pengeluaran, laba bersih (hari ini & bulan ini), stok menipis | Ada |
| Produk & stok: tambah, edit, hapus/arsip, cari, filter stok menipis | Ada |
| Barang masuk (modal rata-rata tertimbang), koreksi stok, riwayat stok | Ada |
| Kasir, struk, utang pelanggan, pengeluaran, laporan, backup | Tahap 3 (tombol di Beranda masih nonaktif dan diberi keterangan) |
| Scan barcode kamera, printer Bluetooth | Belum — tidak diklaim sebelum diuji di perangkat nyata |

## Cara mendapatkan APK

Setiap kode baru yang masuk ke cabang `main` otomatis:

1. menjalankan uji perhitungan (unit test),
2. membangun APK debug,
3. menerbitkannya di menu **Releases** repo ini (`KasirKu-debug-<nomor>.apk`).

Buka repo ini dari HP → **Releases** → unduh file `.apk` terbaru → pasang (izinkan "Instal dari sumber tidak dikenal" bila diminta).

## Teknologi

Kotlin · Jetpack Compose (Material 3) · Room (SQLite) · MVVM · minSdk 26 (Android 8.0)

## Aturan uang

- Semua uang disimpan sebagai bilangan bulat Rupiah.
- Omzet = penjualan setelah semua diskon; transaksi batal tidak dihitung.
- Laba kotor = omzet − modal barang terjual (HPP).
- Laba bersih = laba kotor − pengeluaran operasional & lain-lain.
- Belanja barang dari pemasok **bukan** pengeluaran: ia menjadi stok, dan baru dihitung sebagai HPP saat terjual.
