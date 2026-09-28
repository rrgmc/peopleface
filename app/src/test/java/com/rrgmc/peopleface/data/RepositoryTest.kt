package com.rrgmc.peopleface.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rrgmc.peopleface.data.db.AppDatabase
import com.rrgmc.peopleface.data.db.Role
import com.rrgmc.peopleface.ui.common.familyLabels
import com.rrgmc.peopleface.ui.quiz.pickOptions
import com.rrgmc.peopleface.ui.search.matches
import com.rrgmc.peopleface.ui.search.normalizeForSearch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = Repository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun familyWithManyKidsIsOrderedParentsFirst() = runTest {
        val group = repo.addGroup("School")
        val family = repo.addFamily(group, "Silva")
        repo.addPerson(family, "Ana", Role.CHILD)
        repo.addPerson(family, "Bia", Role.CHILD)
        repo.addPerson(family, "Grandma", Role.ADULT)
        repo.addPerson(family, "Rita", Role.ADULT)
        repo.addPerson(family, "Caio", Role.CHILD)
        repo.addPerson(family, "João", Role.ADULT)

        val names = repo.observePersonsInFamily(family).first().map { it.person.name }
        assertEquals(listOf("Grandma", "Rita", "João", "Ana", "Bia", "Caio"), names)

        val members = repo.observePersonsInFamily(family).first()
        repo.swapOrder(members[5].person, members[4].person) // Caio above Bia
        assertEquals(
            listOf("Grandma", "Rita", "João", "Ana", "Caio", "Bia"),
            repo.observePersonsInFamily(family).first().map { it.person.name },
        )
    }

    @Test
    fun addPeopleSkipsBlankNames() = runTest {
        val group = repo.addGroup("School")
        val rows = listOf(
            Repository.NewPerson("  ", Role.ADULT),
            Repository.NewPerson("Rita", Role.ADULT),
            Repository.NewPerson("Ana", Role.CHILD),
            Repository.NewPerson("", Role.CHILD),
            Repository.NewPerson("Bia", Role.CHILD),
            Repository.NewPerson("Tia", Role.ADULT),
        )
        val family = repo.addPeople(group, 0, "", rows)!!
        val members = repo.observePersonsInFamily(family).first()
        assertEquals(listOf("Rita", "Tia", "Ana", "Bia"), members.map { it.person.name })

        // Adding more to the existing family keeps the kids' order.
        repo.addPeople(group, family, "", listOf(Repository.NewPerson("Caio", Role.CHILD), Repository.NewPerson("", Role.ADULT)))
        assertEquals(
            listOf("Rita", "Tia", "Ana", "Bia", "Caio"),
            repo.observePersonsInFamily(family).first().map { it.person.name },
        )
    }

    @Test
    fun addIndividualsMakesOneFamilyEach() = runTest {
        val group = repo.addGroup("Club")
        val added = repo.addIndividuals(group, listOf(
            Repository.NewPerson("Ana", Role.ADULT),
            Repository.NewPerson(" ", Role.ADULT),
            Repository.NewPerson("Rui", Role.ADULT),
        ))
        assertEquals(2, added)
        val rows = repo.observePersonsInGroup(group).first()
        assertEquals(setOf("Ana", "Rui"), rows.map { it.person.name }.toSet())
        assertEquals(2, rows.map { it.person.familyId }.distinct().size)
        assertEquals(2, repo.observeGroups().first().single().familyCount)
    }

    @Test
    fun addPeopleWithNothingCreatesNoFamily() = runTest {
        val group = repo.addGroup("School")
        assertNull(repo.addPeople(group, 0, " ", listOf(Repository.NewPerson("", Role.CHILD))))
        assertEquals(0, repo.observeGroups().first().single().familyCount)

        // A named family with nobody in it yet is allowed.
        val family = repo.addPeople(group, 0, "Silva", listOf(Repository.NewPerson("", Role.CHILD)))
        assertEquals("Silva", repo.observeFamily(family!!).first()!!.name)
    }

    @Test
    fun firstPhotoBecomesThumbnailAndDeletionFallsBack() = runTest {
        val family = repo.addFamily(repo.addGroup("Club"), "")
        val person = repo.addPerson(family, "Leo", Role.CHILD)
        val p1 = repo.addPhoto(person, byteArrayOf(1), byteArrayOf(11))
        val p2 = repo.addPhoto(person, byteArrayOf(2), byteArrayOf(22))

        var row = repo.observePerson(person).first()!!
        assertEquals(p1, row.person.thumbnailPhotoId)
        assertTrue(row.thumb!!.contentEquals(byteArrayOf(11)))

        repo.setThumbnail(person, p2)
        repo.deletePhoto(p2)
        row = repo.observePerson(person).first()!!
        assertEquals(p1, row.person.thumbnailPhotoId)

        repo.deletePhoto(p1)
        assertNull(repo.observePerson(person).first()!!.person.thumbnailPhotoId)
    }

    @Test
    fun deletingGroupCascades() = runTest {
        val groupId = repo.addGroup("Club")
        val person = repo.addPerson(repo.addFamily(groupId, "X"), "Leo", Role.CHILD)
        val photo = repo.addPhoto(person, byteArrayOf(1), byteArrayOf(2))
        val group = repo.observeGroup(groupId).first()!!

        repo.deleteGroup(group)
        assertNull(repo.observePerson(person).first())
        assertNull(repo.photoImage(photo))
    }

    @Test
    fun groupCountsAndQuizCandidates() = runTest {
        val g = repo.addGroup("School")
        val f1 = repo.addFamily(g, "A")
        val f2 = repo.addFamily(g, "B")
        val a = repo.addPerson(f1, "Ana", Role.CHILD)
        repo.addPerson(f1, "Rui", Role.ADULT)
        repo.addPerson(f2, "Zé", Role.CHILD)
        repo.addPhoto(a, byteArrayOf(1), byteArrayOf(1))

        val counts = repo.observeGroups().first().single()
        assertEquals(2, counts.familyCount)
        assertEquals(3, counts.personCount)
        assertEquals(listOf("Ana"), repo.personsWithPhotos(g).map { it.person.name })
        assertEquals(listOf("Ana"), repo.personsWithPhotos(0).map { it.person.name })
    }

    @Test
    fun searchIgnoresAccentsAndCase() = runTest {
        val g = repo.addGroup("Clube Pinheiros")
        val f = repo.addFamily(g, "Conceição")
        repo.addPerson(f, "José", Role.ADULT, notes = "Joga futebol")
        val row = repo.observeAllPersons().first().single()

        assertTrue(row.matches(normalizeForSearch("jose")))
        assertTrue(row.matches(normalizeForSearch("conceicao pinheiros")))
        assertTrue(row.matches(normalizeForSearch("FUTEBOL")))
        assertFalse(row.matches(normalizeForSearch("maria")))
    }

    @Test
    fun familyLabelsTellApartSameNames() = runTest {
        val g = repo.addGroup("School")
        val f1 = repo.addPeople(g, 0, "", listOf(
            Repository.NewPerson("Michelangelo", Role.ADULT),
            Repository.NewPerson("Suzi", Role.ADULT),
            Repository.NewPerson("Isabella", Role.CHILD),
            Repository.NewPerson("Milena", Role.CHILD),
        ))!!
        val f2 = repo.addPeople(g, 0, "Silva", listOf(Repository.NewPerson("Isabella", Role.CHILD), Repository.NewPerson("Leo", Role.CHILD)))!!
        val f3 = repo.addPeople(g, 0, "", listOf(Repository.NewPerson("Ana", Role.CHILD)))!!
        val rows = repo.observePersonsInGroup(g).first()
        val labels = familyLabels(rows)
        fun label(family: Long, name: String) = labels[rows.single { it.person.familyId == family && it.person.name == name }.person.id]

        assertEquals("Michelangelo & Suzi", label(f1, "Isabella"))
        assertEquals("Suzi", label(f1, "Michelangelo")) // never their own name
        assertEquals("Silva · Leo", label(f2, "Isabella"))
        assertEquals("", label(f3, "Ana"))

        // Searching a parent's name finds the kid.
        val isabella1 = rows.single { it.person.familyId == f1 && it.person.name == "Isabella" }
        assertTrue(isabella1.matches(normalizeForSearch("isa michel"), label(f1, "Isabella")!!))
    }

    @Test
    fun quizOptionsContainTargetAndUniqueNames() = runTest {
        val f = repo.addFamily(repo.addGroup("G"), "")
        listOf("Ana", "Bia", "Caio", "Duda", "Eva", "Ana").forEach { repo.addPerson(f, it, Role.CHILD) }
        val all = repo.observeAllPersons().first()
        val target = all.first()
        repeat(20) {
            val options = pickOptions(target, all)
            assertEquals(4, options.size)
            assertTrue(options.any { it.person.id == target.person.id })
            assertEquals(4, options.map { it.person.name }.distinct().size)
        }
    }

    @Test
    fun familyTagIsShownOnMembersAndClearedWhenTagIsDeleted() = runTest {
        val group = repo.addGroup("School")
        val other = repo.addGroup("Club")
        val family = repo.addFamily(group, "Silva")
        repo.addPerson(family, "Ana", Role.CHILD)
        val bus = repo.addTag(group, " Bus ", 0xFF1E88E5.toInt())
        repo.addTag(group, "Class B", 0xFFE53935.toInt())
        repo.addTag(other, "Tennis", 0xFF43A047.toInt())

        assertEquals(listOf("Bus", "Class B"), repo.observeTags(group).first().map { it.name })
        assertNull(repo.observePersonsInFamily(family).first().single().tagName)

        repo.setFamilyTag(family, bus)
        assertEquals(bus, repo.observeFamily(family).first()?.tagId)
        val ana = repo.observePersonsInFamily(family).first().single()
        assertEquals("Bus", ana.tagName)
        assertEquals(0xFF1E88E5.toInt(), ana.tagColor)
        assertTrue(ana.matches(normalizeForSearch("bus")))

        val tag = repo.observeTags(group).first().first { it.id == bus }
        repo.updateTag(tag.copy(name = "Van", color = 0xFF000000.toInt()))
        assertEquals("Van", repo.observePersonsInFamily(family).first().single().tagName)

        repo.deleteTag(tag)
        assertNull(repo.observeFamily(family).first()?.tagId)
        assertNull(repo.observePersonsInFamily(family).first().single().tagName)

        repo.deleteGroup(repo.observeGroup(group).first()!!)
        assertTrue(repo.observeTags(group).first().isEmpty())
        assertEquals(1, repo.observeTags(other).first().size)
    }
}
