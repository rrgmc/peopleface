package com.rrgmc.peopleface.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where people are from: kid school, club A, etc. ("groups" is an SQLite keyword, hence the table name.) */
@Entity(tableName = "origin_groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "families",
    foreignKeys = [ForeignKey(
        entity = GroupEntity::class,
        parentColumns = ["id"],
        childColumns = ["group_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("group_id")],
)
data class FamilyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "group_id") val groupId: Long,
    /** Optional, e.g. "Silva". When blank the member names are shown instead. */
    val name: String = "",
    val notes: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

enum class Role { ADULT, CHILD }

@Entity(
    tableName = "persons",
    foreignKeys = [ForeignKey(
        entity = FamilyEntity::class,
        parentColumns = ["id"],
        childColumns = ["family_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("family_id")],
)
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "family_id") val familyId: Long,
    val name: String,
    val role: Role,
    /** No longer used (roles are only adult / child); older labels were moved into [notes] (database v2). */
    @ColumnInfo(name = "role_label") val roleLabel: String = "",
    val notes: String = "",
    @ColumnInfo(name = "thumbnail_photo_id") val thumbnailPhotoId: Long? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

/** A face cropped from a larger photo. Both images are JPEG bytes stored in the database. */
@Entity(
    tableName = "photos",
    foreignKeys = [ForeignKey(
        entity = PersonEntity::class,
        parentColumns = ["id"],
        childColumns = ["person_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("person_id")],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "person_id") val personId: Long,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val image: ByteArray,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val thumb: ByteArray,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
) {
    override fun equals(other: Any?) = other is PhotoEntity && other.id == id
    override fun hashCode() = id.hashCode()
}

data class GroupWithCounts(
    @Embedded val group: GroupEntity,
    @ColumnInfo(name = "family_count") val familyCount: Int,
    @ColumnInfo(name = "person_count") val personCount: Int,
)

/** A person together with the small thumbnail image and the names of its family and group. */
data class PersonRow(
    @Embedded val person: PersonEntity,
    val thumb: ByteArray?,
    @ColumnInfo(name = "family_name") val familyName: String,
    @ColumnInfo(name = "group_id") val groupId: Long,
    @ColumnInfo(name = "group_name") val groupName: String,
) {
    override fun equals(other: Any?) =
        other is PersonRow && other.person == person && other.familyName == familyName &&
            other.groupName == groupName && other.thumb.contentEquals(thumb)

    override fun hashCode() = person.hashCode()
}

data class PhotoThumb(
    val id: Long,
    val thumb: ByteArray,
) {
    override fun equals(other: Any?) = other is PhotoThumb && other.id == id
    override fun hashCode() = id.hashCode()
}
