package com.rrgmc.peopleface.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rrgmc.peopleface.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Creates an empty database with the schema Room exported for [version] (tests run in the module dir). */
    private fun createDatabase(file: File, version: Int): SQLiteDatabase {
        val schema = JSONObject(File("schemas/${AppDatabase::class.java.name}/$version.json").readText())
            .getJSONObject("database")
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = version
        return db
    }

    @Test
    fun rolesBecomeAdultOrChildAndLabelsMoveToNotes() {
        val file = context.getDatabasePath("migration-test.db").apply { parentFile?.mkdirs(); delete() }
        createDatabase(file, 1).use { db ->
            db.execSQL("INSERT INTO origin_groups (id, name, notes, created_at) VALUES (1, 'School', '', 0)")
            db.execSQL("INSERT INTO families (id, group_id, name, notes, created_at) VALUES (1, 1, '', '', 0)")
            fun person(id: Int, name: String, role: String, label: String, notes: String) = db.execSQL(
                "INSERT INTO persons (id, family_id, name, role, role_label, notes, thumbnail_photo_id, sort_order, created_at) " +
                    "VALUES ($id, 1, '$name', '$role', '$label', '$notes', NULL, $id, 0)"
            )
            person(1, "João", "FATHER", "", "Tall")
            person(2, "Rita", "MOTHER", "", "")
            person(3, "Ana", "KID", "", "")
            person(4, "Lu", "OTHER", "Grandma", "")
            person(5, "Rui", "OTHER", "Coach", "Plays tennis")
        }

        // Opening with the app's Room setup runs the migration and validates the resulting schema.
        val room = Room.databaseBuilder(context, AppDatabase::class.java, file.path)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            val rows = room.openHelper.readableDatabase
                .query("SELECT name, role, role_label, notes FROM persons ORDER BY id").use { c ->
                    buildList {
                        while (c.moveToNext()) add(listOf(c.getString(0), c.getString(1), c.getString(2), c.getString(3)))
                    }
                }
            assertEquals(
                listOf(
                    listOf("João", "ADULT", "", "Tall"),
                    listOf("Rita", "ADULT", "", ""),
                    listOf("Ana", "CHILD", "", ""),
                    listOf("Lu", "ADULT", "", "Grandma"),
                    listOf("Rui", "ADULT", "", "Coach · Plays tennis"),
                ),
                rows,
            )
            assertEquals(6, room.openHelper.readableDatabase.version)
        } finally {
            room.close()
        }
    }

    @Test
    fun familiesGetAnEmptyTagAndTagsCanBeDeleted() {
        val file = context.getDatabasePath("migration-test-3.db").apply { parentFile?.mkdirs(); delete() }
        createDatabase(file, 2).use { db ->
            db.execSQL("INSERT INTO origin_groups (id, name, notes, created_at) VALUES (1, 'School', '', 0)")
            db.execSQL("INSERT INTO families (id, group_id, name, notes, created_at) VALUES (1, 1, 'Silva', '', 0)")
            db.execSQL(
                "INSERT INTO persons (id, family_id, name, role, role_label, notes, thumbnail_photo_id, sort_order, created_at) " +
                    "VALUES (1, 1, 'Ana', 'CHILD', '', '', NULL, 1, 0)"
            )
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, file.path)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(6, db.version)
            fun tagId() = db.query("SELECT tag_id FROM families WHERE id = 1").use { c ->
                c.moveToFirst()
                if (c.isNull(0)) null else c.getLong(0)
            }
            assertEquals(null, tagId())
            db.execSQL("INSERT INTO tags (id, group_id, name, color, created_at) VALUES (1, 1, 'Bus', -1, 0)")
            db.execSQL("UPDATE families SET tag_id = 1 WHERE id = 1")
            assertEquals(1L, tagId())
            db.execSQL("DELETE FROM tags WHERE id = 1")
            assertEquals(null, tagId())
        } finally {
            room.close()
        }
    }

    @Test
    fun groupsGetAnEmptyIcon() {
        val file = context.getDatabasePath("migration-test-4.db").apply { parentFile?.mkdirs(); delete() }
        createDatabase(file, 3).use { db ->
            db.execSQL("INSERT INTO origin_groups (id, name, notes, created_at) VALUES (1, 'School', '', 0)")
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, file.path)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(6, db.version)
            fun icon() = db.query("SELECT icon FROM origin_groups WHERE id = 1").use { c ->
                c.moveToFirst()
                if (c.isNull(0)) null else c.getBlob(0).toList()
            }
            assertEquals(null, icon())
            db.execSQL("UPDATE origin_groups SET icon = X'0102' WHERE id = 1")
            assertEquals(listOf<Byte>(1, 2), icon())
        } finally {
            room.close()
        }
    }

    @Test
    fun personsAreNotPlaceholders() {
        val file = context.getDatabasePath("migration-test-5.db").apply { parentFile?.mkdirs(); delete() }
        createDatabase(file, 4).use { db ->
            db.execSQL("INSERT INTO origin_groups (id, name, notes, created_at) VALUES (1, 'School', '', 0)")
            db.execSQL("INSERT INTO families (id, group_id, name, notes, created_at) VALUES (1, 1, '', '', 0)")
            db.execSQL(
                "INSERT INTO persons (id, family_id, name, role, role_label, notes, thumbnail_photo_id, sort_order, created_at) " +
                    "VALUES (1, 1, 'Ana', 'CHILD', '', '', NULL, 1, 0)"
            )
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, file.path)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            val db = room.openHelper.readableDatabase
            assertEquals(6, db.version)
            val placeholder = db.query("SELECT is_placeholder FROM persons WHERE id = 1").use { c ->
                c.moveToFirst()
                c.getInt(0)
            }
            assertEquals(0, placeholder)
        } finally {
            room.close()
        }
    }

    @Test
    fun familiesKeepTheirOrderByName() {
        val file = context.getDatabasePath("migration-test-6.db").apply { parentFile?.mkdirs(); delete() }
        createDatabase(file, 5).use { db ->
            db.execSQL("INSERT INTO origin_groups (id, name, notes, created_at) VALUES (1, 'School', '', 0)")
            db.execSQL("INSERT INTO families (id, group_id, name, notes, created_at) VALUES (1, 1, 'Silva', '', 0)")
            db.execSQL("INSERT INTO families (id, group_id, name, notes, created_at) VALUES (2, 1, 'Costa', '', 0)")
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, file.path)
            .addMigrations(AppDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(6, room.openHelper.readableDatabase.version)
            val names = runBlocking { Repository(room).observeFamilies(1).first().map { it.name } }
            assertEquals(listOf("Costa", "Silva"), names)
        } finally {
            room.close()
        }
    }
}
