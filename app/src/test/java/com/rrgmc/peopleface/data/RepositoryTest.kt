package com.rrgmc.peopleface.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rrgmc.peopleface.data.db.AppDatabase
import com.rrgmc.peopleface.data.db.Role
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
        repo.addPerson(family, "Ana", Role.KID)
        repo.addPerson(family, "Bia", Role.KID)
        repo.addPerson(family, "Grandma", Role.OTHER, roleLabel = "Grandmother")
        repo.addPerson(family, "Rita", Role.MOTHER)
        repo.addPerson(family, "Caio", Role.KID)
        repo.addPerson(family, "João", Role.FATHER)

        val names = repo.observePersonsInFamily(family).first().map { it.person.name }
        assertEquals(listOf("João", "Rita", "Ana", "Bia", "Caio", "Grandma"), names)

        val members = repo.observePersonsInFamily(family).first()
        repo.swapOrder(members[4].person, members[3].person) // Caio above Bia
        assertEquals(
            listOf("João", "Rita", "Ana", "Caio", "Bia", "Grandma"),
            repo.observePersonsInFamily(family).first().map { it.person.name },
        )
    }

    @Test
    fun addPeopleSkipsBlankNames() = runTest {
        val group = repo.addGroup("School")
        val rows = listOf(
            Repository.NewPerson("  ", Role.FATHER),
            Repository.NewPerson("Rita", Role.MOTHER),
            Repository.NewPerson("Ana", Role.KID),
            Repository.NewPerson("", Role.KID),
            Repository.NewPerson("Bia", Role.KID),
            Repository.NewPerson("Tia", Role.OTHER, roleLabel = "Aunt"),
        )
        val family = repo.addPeople(group, 0, "", rows)!!
        val members = repo.observePersonsInFamily(family).first()
        assertEquals(listOf("Rita", "Ana", "Bia", "Tia"), members.map { it.person.name })
        assertEquals("Aunt", members.last().person.roleLabel)

        // Adding more to the existing family keeps the kids' order.
        repo.addPeople(group, family, "", listOf(Repository.NewPerson("Caio", Role.KID), Repository.NewPerson("", Role.FATHER)))
        assertEquals(
            listOf("Rita", "Ana", "Bia", "Caio", "Tia"),
            repo.observePersonsInFamily(family).first().map { it.person.name },
        )
    }

    @Test
    fun addPeopleWithNothingCreatesNoFamily() = runTest {
        val group = repo.addGroup("School")
        assertNull(repo.addPeople(group, 0, " ", listOf(Repository.NewPerson("", Role.KID))))
        assertEquals(0, repo.observeGroups().first().single().familyCount)

        // A named family with nobody in it yet is allowed.
        val family = repo.addPeople(group, 0, "Silva", listOf(Repository.NewPerson("", Role.KID)))
        assertEquals("Silva", repo.observeFamily(family!!).first()!!.name)
    }

    @Test
    fun firstPhotoBecomesThumbnailAndDeletionFallsBack() = runTest {
        val family = repo.addFamily(repo.addGroup("Club"), "")
        val person = repo.addPerson(family, "Leo", Role.KID)
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
        val person = repo.addPerson(repo.addFamily(groupId, "X"), "Leo", Role.KID)
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
        val a = repo.addPerson(f1, "Ana", Role.KID)
        repo.addPerson(f1, "Rui", Role.FATHER)
        repo.addPerson(f2, "Zé", Role.KID)
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
        repo.addPerson(f, "José", Role.FATHER, notes = "Joga futebol")
        val row = repo.observeAllPersons().first().single()

        assertTrue(row.matches(normalizeForSearch("jose")))
        assertTrue(row.matches(normalizeForSearch("conceicao pinheiros")))
        assertTrue(row.matches(normalizeForSearch("FUTEBOL")))
        assertFalse(row.matches(normalizeForSearch("maria")))
    }

    @Test
    fun quizOptionsContainTargetAndUniqueNames() = runTest {
        val f = repo.addFamily(repo.addGroup("G"), "")
        listOf("Ana", "Bia", "Caio", "Duda", "Eva", "Ana").forEach { repo.addPerson(f, it, Role.KID) }
        val all = repo.observeAllPersons().first()
        val target = all.first()
        repeat(20) {
            val options = pickOptions(target, all)
            assertEquals(4, options.size)
            assertTrue(options.any { it.person.id == target.person.id })
            assertEquals(4, options.map { it.person.name }.distinct().size)
        }
    }
}
