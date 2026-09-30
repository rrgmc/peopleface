package com.rrgmc.peopleface.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.rrgmc.peopleface.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Exports / imports the whole app state, which is the single SQLite file. */
class BackupManager(private val context: Context, private val container: AppContainer) {

    private val dbFile: File get() = context.getDatabasePath(AppDatabase.FILE_NAME)

    suspend fun export(target: Uri) = withContext(Dispatchers.IO) {
        val db = container.database
        // Make sure everything is flushed into the main file.
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        context.contentResolver.openOutputStream(target, "wt")?.use { out ->
            dbFile.inputStream().use { it.copyTo(out) }
        } ?: throw IOException("Cannot open destination")
    }

    /**
     * Replaces the current database with the one at [source]. The database is closed while doing so;
     * the caller must restart the app afterwards.
     */
    suspend fun import(source: Uri) = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "import.db")
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: throw IOException("Cannot open file")
            validate(tmp)
            container.closeDatabase()
            for (suffix in listOf("", "-wal", "-shm", "-journal")) File(dbFile.path + suffix).delete()
            tmp.copyTo(dbFile, overwrite = true)
        } finally {
            tmp.delete()
        }
    }

    private fun validate(file: File) {
        val header = ByteArray(16)
        file.inputStream().use { if (it.read(header) != 16) throw InvalidBackupException() }
        if (String(header, Charsets.US_ASCII) != "SQLite format 3\u0000") throw InvalidBackupException()
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            throw InvalidBackupException()
        }
        db.use {
            val tables = mutableSetOf<String>()
            it.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                while (c.moveToNext()) tables += c.getString(0)
            }
            if (!tables.containsAll(listOf("origin_groups", "families", "persons", "photos"))) {
                throw InvalidBackupException()
            }
            if (it.version > CURRENT_VERSION) throw InvalidBackupException()
        }
    }

    class InvalidBackupException : Exception("Not a PeopleFace backup")

    private companion object {
        const val CURRENT_VERSION = 4
    }
}
