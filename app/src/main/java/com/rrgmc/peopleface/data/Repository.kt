package com.rrgmc.peopleface.data

import androidx.room.withTransaction
import com.rrgmc.peopleface.data.db.AppDatabase
import com.rrgmc.peopleface.data.db.FamilyEntity
import com.rrgmc.peopleface.data.db.GroupEntity
import com.rrgmc.peopleface.data.db.PersonEntity
import com.rrgmc.peopleface.data.db.PersonRow
import com.rrgmc.peopleface.data.db.PhotoEntity
import com.rrgmc.peopleface.data.db.Role
import com.rrgmc.peopleface.data.db.TagEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class Repository(private val db: AppDatabase) {
    private val groups = db.groupDao()
    private val families = db.familyDao()
    private val persons = db.personDao()
    private val photos = db.photoDao()
    private val tags = db.tagDao()

    // Groups
    fun observeGroups() = groups.observeAll().map { list -> list.sortedByName { it.group.name } }
    fun observeGroup(id: Long) = groups.observe(id)
    suspend fun addGroup(name: String, notes: String = "") =
        groups.insert(GroupEntity(name = name.trim(), notes = notes.trim()))
    suspend fun updateGroup(group: GroupEntity) = groups.update(group)
    suspend fun deleteGroup(group: GroupEntity) = groups.delete(group)
    /** [icon]: JPEG bytes, or null to remove the icon. */
    suspend fun setGroupIcon(groupId: Long, icon: ByteArray?) = groups.setIcon(groupId, icon)

    // Families
    fun observeFamilies(groupId: Long) = families.observeByGroup(groupId).map { list -> list.sortedByName { it.name } }
    fun observeFamily(id: Long) = families.observe(id)
    suspend fun addFamily(groupId: Long, name: String, notes: String = "") =
        families.insert(FamilyEntity(groupId = groupId, name = name.trim(), notes = notes.trim()))
    suspend fun updateFamily(family: FamilyEntity) = families.update(family)
    suspend fun deleteFamily(family: FamilyEntity) = families.delete(family)
    suspend fun setFamilyTag(familyId: Long, tagId: Long?) = families.setTag(familyId, tagId)

    // Tags
    fun observeTags(groupId: Long) = tags.observeByGroup(groupId).map { list -> list.sortedByName { it.name } }
    suspend fun addTag(groupId: Long, name: String, color: Int) =
        tags.insert(TagEntity(groupId = groupId, name = name.trim(), color = color))
    suspend fun updateTag(tag: TagEntity) = tags.update(tag.copy(name = tag.name.trim()))
    /** Families with this tag are left without a tag. */
    suspend fun deleteTag(tag: TagEntity) = tags.delete(tag)

    // Persons
    fun observePersonsInGroup(groupId: Long) = persons.observeRowsByGroup(groupId)
    fun observePersonsInFamily(familyId: Long) = persons.observeRowsByFamily(familyId)
    fun observePerson(id: Long): Flow<PersonRow?> = persons.observeRow(id)
    fun observeAllPersons() = persons.observeAllRows().map { list -> list.sortedByName { it.person.name } }
    suspend fun personsWithPhotos(groupId: Long) = persons.rowsWithPhotos(groupId)

    suspend fun addPerson(
        familyId: Long, name: String, role: Role, notes: String = "", placeholder: Boolean = false,
    ): Long =
        db.withTransaction {
            persons.insert(
                PersonEntity(
                    familyId = familyId,
                    name = name.trim(),
                    role = role,
                    notes = notes.trim(),
                    isPlaceholder = placeholder,
                    sortOrder = persons.maxSortOrder(familyId) + 1,
                )
            )
        }

    /** A person to be added; blank names are skipped by [addPeople]. */
    data class NewPerson(val name: String, val role: Role, val placeholder: Boolean = false)

    /**
     * Adds all non-blank [people] in order. With [familyId] 0 a new family is created in [groupId]
     * (only if there is a name or at least one person). Returns the family id, or null if nothing was added.
     */
    suspend fun addPeople(groupId: Long, familyId: Long, familyName: String, people: List<NewPerson>): Long? =
        db.withTransaction {
            val toAdd = people.filter { it.name.isNotBlank() }
            if (familyId == 0L && toAdd.isEmpty() && familyName.isBlank()) return@withTransaction null
            val id = if (familyId != 0L) familyId else addFamily(groupId, familyName)
            toAdd.forEach { addPerson(id, it.name, it.role, placeholder = it.placeholder) }
            id
        }

    /** Adds each non-blank person as a one-person family (without a family name) in [groupId]. */
    suspend fun addIndividuals(groupId: Long, people: List<NewPerson>): Int = db.withTransaction {
        val toAdd = people.filter { it.name.isNotBlank() }
        toAdd.forEach { addPerson(addFamily(groupId, ""), it.name, it.role, placeholder = it.placeholder) }
        toAdd.size
    }

    suspend fun updatePerson(person: PersonEntity) = persons.update(
        person.copy(
            name = person.name.trim(),
            notes = person.notes.trim(),
        )
    )

    suspend fun deletePerson(id: Long) = persons.delete(id)

    /** Swaps the display position of two members of the same family. */
    suspend fun swapOrder(a: PersonEntity, b: PersonEntity) = db.withTransaction {
        val (sa, sb) = if (a.sortOrder == b.sortOrder) a.sortOrder to a.sortOrder + 1 else a.sortOrder to b.sortOrder
        persons.updateAll(listOf(a.copy(sortOrder = sb), b.copy(sortOrder = sa)))
    }

    // Photos
    fun observePhotoThumbs(personId: Long) = photos.observeThumbs(personId)
    suspend fun photoImage(id: Long) = photos.image(id)
    suspend fun randomImage(personId: Long) = photos.randomImage(personId)

    /** Stores a cropped face; the first photo of a person becomes the thumbnail. */
    suspend fun addPhoto(personId: Long, image: ByteArray, thumb: ByteArray): Long = db.withTransaction {
        val id = photos.insert(PhotoEntity(personId = personId, image = image, thumb = thumb))
        val person = persons.get(personId)
        if (person != null && person.thumbnailPhotoId == null) persons.setThumbnail(personId, id)
        id
    }

    suspend fun setThumbnail(personId: Long, photoId: Long) = persons.setThumbnail(personId, photoId)

    suspend fun deletePhoto(photoId: Long) = db.withTransaction {
        val personId = photos.personIdOf(photoId) ?: return@withTransaction
        photos.delete(photoId)
        val person = persons.get(personId) ?: return@withTransaction
        if (person.thumbnailPhotoId == photoId) persons.setThumbnail(personId, photos.firstPhotoId(personId))
    }
}
