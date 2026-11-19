package com.tsutsen.platformplayer.core.database

import android.content.Context
import android.util.Log
import androidx.room.Room
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single construction site for [AppDatabase]. Used by the Hilt module and
 * by app-level singleton states that predate Hilt injection.
 *
 * The built instance is cached process-wide: callers pass different contexts
 * (the Hilt module passes @ApplicationContext, legacy states pass whatever
 * StateApp holds — which MainActivity replaces with the *activity* context),
 * and separate Room instances on the same file silently diverge into two
 * copies of the data (the backup export saw 0 saved videos while the Library
 * UI, via the Hilt DAO, saw hundreds). One process, one database instance.
 */
object AppDatabaseProvider {
    private const val TAG = "AppDatabaseProvider"
    private const val DB_NAME = "grayjay_database"
    // Must match AppDatabase's @Database version; it is read here (not from
    // AppDatabase) so the pre-migration hook below stays a pure file check.
    private const val DB_VERSION = 12

    private val _instance = AtomicReference<AppDatabase?>(null)

    fun get(context: Context): AppDatabase =
        _instance.get() ?: build(context).let { built ->
            _instance.compareAndSet(null, built)
            // Always return the winner so a lost race still shares one instance.
            _instance.get() ?: built
        }

    /**
     * Forces a WAL checkpoint (TRUNCATE) and runs a quick integrity check.
     * Call before any code that reads the database for a backup/export, so
     * transactions still sitting in the WAL are folded into the main db file
     * and a snapshot taken from it (or from the tables) cannot silently miss
     * recently-written rows. Never throws: a failed checkpoint must not block
     * a backup, but it is logged loudly.
     */
    suspend fun checkpointWal(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = get(context)
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                if (cursor.moveToFirst()) {
                    Log.i(
                        TAG,
                        "WAL checkpoint: busy=${cursor.getInt(0)} logPages=${cursor.getLong(1)} checkpointedPages=${cursor.getLong(2)}"
                    )
                }
            }
            db.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
                if (cursor.moveToFirst()) {
                    Log.i(TAG, "integrity_check: ${cursor.getString(0)}")
                }
            }
        } catch (ex: Throwable) {
            Log.e(TAG, "WAL checkpoint / integrity check failed", ex)
        }
    }

    private fun build(context: Context): AppDatabase {
        backupBeforeMigration(context)
        return Room
            .databaseBuilder(
                context,
                AppDatabase::class.java,
                DB_NAME,
            ).addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
            )
            // Safety net only: MIGRATION_1_2 is registered above, so this
            // triggers just for an unregistered future version (same as before).
            .fallbackToDestructiveMigration()
            .build()
    }

    /**
     * One-shot DB-file snapshot taken before Room runs any migration: if the
     * migration (or the fallbackToDestructiveMigration safety net) destroys
     * data, the raw pre-migration file is recoverable from the app's files
     * dir. Reads the file's user_version from the SQLite header (offset 60,
     * big-endian) without opening SQLite, so this cannot contend with or
     * corrupt anything. Keeps the 2 most recent snapshots.
     */
    private fun backupBeforeMigration(context: Context) {
        try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return
            val fromVersion = readFileUserVersion(dbFile) ?: return
            if (fromVersion >= DB_VERSION) return

            val dir = File(context.filesDir, "pre_migration_backups").apply { mkdirs() }
            val base = "pre_migration_backup_${fromVersion}_${DB_VERSION}"
            dbFile.copyTo(File(dir, "$base.db"), overwrite = true)
            for (suffix in listOf("-wal", "-shm")) {
                val side = File(dbFile.path + suffix)
                if (side.exists()) side.copyTo(File(dir, base + suffix), overwrite = true)
            }
            Log.i(TAG, "Pre-migration backup: v$fromVersion -> v$DB_VERSION saved to ${dir.path}/$base.db")

            // Retention: keep the 2 most recent snapshot bases, delete the
            // rest (including their -wal/-shm sidecars).
            dir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".db") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(2)
                ?.forEach { old ->
                    File(dir, old.nameWithoutExtension + "-wal").delete()
                    File(dir, old.nameWithoutExtension + "-shm").delete()
                    old.delete()
                }
        } catch (ex: Throwable) {
            // Never block the migration itself over a failed backup.
            Log.e(TAG, "Pre-migration backup failed (continuing with migration)", ex)
        }
    }

    private fun readFileUserVersion(file: File): Int? = try {
        file.inputStream().use { input ->
            val header = ByteArray(68)
            var read = 0
            while (read < header.size) {
                val n = input.read(header, read, header.size - read)
                if (n <= 0) break
                read += n
            }
            if (read < header.size) null
            else {
                // SQLite header: 4-byte big-endian user_version at offset 60.
                ((header[60].toInt() and 0xFF) shl 24) or
                        ((header[61].toInt() and 0xFF) shl 16) or
                        ((header[62].toInt() and 0xFF) shl 8) or
                        (header[63].toInt() and 0xFF)
            }
        }
    } catch (ex: Throwable) {
        Log.e(TAG, "Could not read user_version from ${file.path}", ex)
        null
    }
}
