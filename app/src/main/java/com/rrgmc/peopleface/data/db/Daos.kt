package com.rrgmc.peopleface.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val PERSON_ROW_SELECT = """
    SELECT p.*, ph.thumb AS thumb, f.name AS family_name, g.id AS group_id, g.name AS group_name
    FROM persons p
    JOIN families f ON f.id = p.family_id
    JOIN origin_groups g ON g.id = f.group_id
    LEFT JOIN photos ph ON ph.id = p.thumbnail_photo_id
"""

/** Parents first, then kids in their chosen order, then everyone else. */
private const val PERSON_ORDER = """
    CASE p.role WHEN 'FATHER' THEN 0 WHEN 'MOTHER' THEN 1 WHEN 'KID' THEN 2 ELSE 3 END,
    p.sort_order, p.id
"""

@Dao
interface GroupDao {
    @Query(
        """
        SELECT g.*,
            (SELECT COUNT(*) FROM families f WHERE f.group_id = g.id) AS family_count,
            (SELECT COUNT(*) FROM persons p JOIN families f ON f.id = p.family_id WHERE f.group_id = g.id) AS person_count
        FROM origin_groups g ORDER BY g.name COLLATE NOCASE
        """
    )
    fun observeAll(): Flow<List<GroupWithCounts>>

    @Query("SELECT * FROM origin_groups WHERE id = :id")
    fun observe(id: Long): Flow<GroupEntity?>

    @Insert
    suspend fun insert(group: GroupEntity): Long

    @Update
    suspend fun update(group: GroupEntity)

    @Delete
    suspend fun delete(group: GroupEntity)
}

@Dao
interface FamilyDao {
    @Query("SELECT * FROM families WHERE group_id = :groupId ORDER BY name COLLATE NOCASE, id")
    fun observeByGroup(groupId: Long): Flow<List<FamilyEntity>>

    @Query("SELECT * FROM families WHERE id = :id")
    fun observe(id: Long): Flow<FamilyEntity?>

    @Insert
    suspend fun insert(family: FamilyEntity): Long

    @Update
    suspend fun update(family: FamilyEntity)

    @Delete
    suspend fun delete(family: FamilyEntity)
}

@Dao
interface PersonDao {
    @Query("$PERSON_ROW_SELECT WHERE f.group_id = :groupId ORDER BY $PERSON_ORDER")
    fun observeRowsByGroup(groupId: Long): Flow<List<PersonRow>>

    @Query("$PERSON_ROW_SELECT WHERE p.family_id = :familyId ORDER BY $PERSON_ORDER")
    fun observeRowsByFamily(familyId: Long): Flow<List<PersonRow>>

    @Query("$PERSON_ROW_SELECT WHERE p.id = :id")
    fun observeRow(id: Long): Flow<PersonRow?>

    @Query("$PERSON_ROW_SELECT ORDER BY p.name COLLATE NOCASE")
    fun observeAllRows(): Flow<List<PersonRow>>

    /** People that have at least one photo, optionally restricted to a group (0 = all groups). */
    @Query(
        """$PERSON_ROW_SELECT
        WHERE EXISTS (SELECT 1 FROM photos x WHERE x.person_id = p.id)
          AND (:groupId = 0 OR f.group_id = :groupId)"""
    )
    suspend fun rowsWithPhotos(groupId: Long): List<PersonRow>

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun get(id: Long): PersonEntity?

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM persons WHERE family_id = :familyId")
    suspend fun maxSortOrder(familyId: Long): Int

    @Insert
    suspend fun insert(person: PersonEntity): Long

    @Update
    suspend fun update(person: PersonEntity)

    @Update
    suspend fun updateAll(persons: List<PersonEntity>)

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE persons SET thumbnail_photo_id = :photoId WHERE id = :personId")
    suspend fun setThumbnail(personId: Long, photoId: Long?)
}

@Dao
interface PhotoDao {
    @Query("SELECT id, thumb FROM photos WHERE person_id = :personId ORDER BY created_at, id")
    fun observeThumbs(personId: Long): Flow<List<PhotoThumb>>

    @Query("SELECT image FROM photos WHERE id = :id")
    suspend fun image(id: Long): ByteArray?

    @Query("SELECT image FROM photos WHERE person_id = :personId ORDER BY RANDOM() LIMIT 1")
    suspend fun randomImage(personId: Long): ByteArray?

    @Query("SELECT id FROM photos WHERE person_id = :personId ORDER BY created_at, id LIMIT 1")
    suspend fun firstPhotoId(personId: Long): Long?

    @Query("SELECT person_id FROM photos WHERE id = :id")
    suspend fun personIdOf(id: Long): Long?

    @Insert
    suspend fun insert(photo: PhotoEntity): Long

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun delete(id: Long)
}
