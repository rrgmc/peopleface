package com.rrgmc.peopleface.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [GroupEntity::class, FamilyEntity::class, PersonEntity::class, PhotoEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun familyDao(): FamilyDao
    abstract fun personDao(): PersonDao
    abstract fun photoDao(): PhotoDao

    companion object {
        const val FILE_NAME = "peopleface.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, FILE_NAME)
                // No -wal/-shm side files: the .db file alone is always a complete backup.
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
    }
}
