package com.rrgmc.peopleface.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [GroupEntity::class, FamilyEntity::class, PersonEntity::class, PhotoEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun familyDao(): FamilyDao
    abstract fun personDao(): PersonDao
    abstract fun photoDao(): PhotoDao

    companion object {
        const val FILE_NAME = "peopleface.db"

        /**
         * Roles became only adult / child: kid -> child, father / mother / other -> adult. The free-text
         * label of "other" people (grandma, coach...) is kept by moving it to the start of their notes.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE persons SET
                        notes = CASE
                            WHEN TRIM(role_label) = '' THEN notes
                            WHEN TRIM(notes) = '' THEN TRIM(role_label)
                            ELSE TRIM(role_label) || ' · ' || notes
                        END,
                        role_label = '',
                        role = CASE role WHEN 'KID' THEN 'CHILD' ELSE 'ADULT' END
                    """
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, FILE_NAME)
                // No -wal/-shm side files: the .db file alone is always a complete backup.
                .setJournalMode(JournalMode.TRUNCATE)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
