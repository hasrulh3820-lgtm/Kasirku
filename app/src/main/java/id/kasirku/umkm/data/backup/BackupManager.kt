package id.kasirku.umkm.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import id.kasirku.umkm.data.db.AppDatabase
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Backup = salinan utuh file database ke tempat yang dipilih pemilik (folder Download, Google Drive, dll).
 * Pulihkan = ganti database di HP dengan file backup, setelah file diperiksa dulu.
 */
class BackupManager(private val context: Context) {

    private val dbFile: File get() = context.getDatabasePath(AppDatabase.FILE_NAME)
    private val prefs get() = context.getSharedPreferences("kasirku", Context.MODE_PRIVATE)

    /** Waktu backup terakhir (epoch ms), 0 jika belum pernah. */
    val lastBackupAt: Long get() = prefs.getLong("lastBackupAt", 0L)

    /** Tulis backup ke [out]. Semua perubahan yang masih di file WAL digabung dulu agar salinan lengkap. */
    fun writeBackup(out: OutputStream) {
        val db = AppDatabase.get(context)
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        dbFile.inputStream().use { it.copyTo(out) }
        out.flush()
        prefs.edit().putLong("lastBackupAt", System.currentTimeMillis()).apply()
    }

    /** null jika [file] adalah backup KasirKu yang valid; selain itu pesan kesalahan yang mudah dipahami. */
    fun checkBackupFile(file: File): String? {
        val header = ByteArray(16)
        val read = file.inputStream().use { it.read(header) }
        if (read < 16 || String(header, 0, 15, Charsets.US_ASCII) != "SQLite format 3") {
            return "File ini bukan backup KasirKu. Pilih file backup yang dibuat dari menu Backup."
        }
        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                if (db.version > AppDatabase.VERSION) {
                    return "Backup ini dibuat dengan versi aplikasi yang lebih baru. Perbarui aplikasi dulu, lalu coba lagi."
                }
                db.rawQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN ('products', 'sales', 'stock_movements')",
                    null,
                ).use { c ->
                    c.moveToFirst()
                    if (c.getInt(0) < 3) "File ini bukan backup KasirKu." else null
                }
            }
        } catch (e: Exception) {
            "File backup rusak atau tidak bisa dibaca."
        }
    }

    /**
     * Pulihkan dari [input]. Mengembalikan null jika berhasil, atau pesan kesalahan (data lama tidak diubah).
     * Setelah berhasil, aplikasi harus dimulai ulang.
     */
    fun restore(input: InputStream): String? {
        val tmp = File(context.cacheDir, "pulihkan.tmp")
        try {
            input.use { src -> tmp.outputStream().use { src.copyTo(it) } }
            checkBackupFile(tmp)?.let { return it }
            AppDatabase.closeAndReset()
            listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
            dbFile.parentFile?.mkdirs()
            tmp.copyTo(dbFile, overwrite = true)
            return null
        } finally {
            tmp.delete()
        }
    }
}
