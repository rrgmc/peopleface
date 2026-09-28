package com.rrgmc.peopleface.data

import android.content.Context
import com.rrgmc.peopleface.data.db.AppDatabase
import com.rrgmc.peopleface.image.FaceDetector

/** Manual dependency container, owned by the Application. */
class AppContainer(private val context: Context) {
    private var db: AppDatabase? = null

    val database: AppDatabase
        @Synchronized get() = db ?: AppDatabase.create(context).also { db = it }

    val repository by lazy { Repository(database) }
    val backup by lazy { BackupManager(context, this) }
    val faceDetector by lazy { FaceDetector() }

    @Synchronized
    fun closeDatabase() {
        db?.close()
    }
}
