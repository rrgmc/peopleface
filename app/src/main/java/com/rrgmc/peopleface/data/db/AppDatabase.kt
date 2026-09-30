package com.rrgmc.peopleface.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [GroupEntity::class, FamilyEntity::class, PersonEntity::class, PhotoEntity::class, TagEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun familyDao(): FamilyDao
    abstract fun personDao(): PersonDao
    abstract fun photoDao(): PhotoDao
    abstract fun tagDao(): TagDao

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

        /** Tags: a table of per-group tags and an optional tag on each family. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`group_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `color` INTEGER NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, FOREIGN KEY(`group_id`) REFERENCES `origin_groups`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tags_group_id` ON `tags` (`group_id`)")
                db.execSQL(
                    "ALTER TABLE `families` ADD COLUMN `tag_id` INTEGER DEFAULT NULL " +
                        "REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_families_tag_id` ON `families` (`tag_id`)")
            }
        }

        /** Groups: an optional icon picture. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `origin_groups` ADD COLUMN `icon` BLOB DEFAULT NULL")
            }
        }

        /** Persons: the name can be a placeholder ("Pai", "Mãe") until the real name is known. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `persons` ADD COLUMN `is_placeholder` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, FILE_NAME)
                // No -wal/-shm side files: the .db file alone is always a complete backup.
                .setJournalMode(JournalMode.TRUNCATE)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
