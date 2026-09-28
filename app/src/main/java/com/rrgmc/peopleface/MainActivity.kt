package com.rrgmc.peopleface

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rrgmc.peopleface.data.AppContainer
import com.rrgmc.peopleface.ui.addpeople.AddPeopleScreen
import com.rrgmc.peopleface.ui.crop.FaceCropScreen
import com.rrgmc.peopleface.ui.families.FamilyListScreen
import com.rrgmc.peopleface.ui.family.FamilyDetailScreen
import com.rrgmc.peopleface.ui.groups.GroupListScreen
import com.rrgmc.peopleface.ui.person.PersonDetailScreen
import com.rrgmc.peopleface.ui.quiz.QuizScreen
import com.rrgmc.peopleface.ui.search.SearchScreen
import com.rrgmc.peopleface.ui.settings.SettingsScreen
import com.rrgmc.peopleface.ui.theme.PeopleFaceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Show "Ultra HDR" photos (gain map) as bright as the gallery does; no effect on other content.
            window.colorMode = ActivityInfo.COLOR_MODE_HDR
        }
        setContent {
            PeopleFaceTheme { AppNavigation() }
        }
    }
}

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as PeopleFaceApp).container

object Routes {
    const val GROUPS = "groups"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    fun group(id: Long) = "group/$id"
    fun family(id: Long) = "family/$id"
    fun person(id: Long) = "person/$id"
    fun quiz(groupId: Long = 0) = "quiz?groupId=$groupId"

    /** Add several people to [familyId], or to a new family in [groupId] when [familyId] is 0. */
    fun addPeople(groupId: Long, familyId: Long = 0) = "addPeople?groupId=$groupId&familyId=$familyId"

    /**
     * Crop a face for [personId], or (with [familyId] / [groupId]) for anyone of that family / group.
     * [fileName] is a temporary copy of the picture in the app's cache (see rememberPhotoSource).
     */
    fun crop(fileNames: List<String>, personId: Long = 0, familyId: Long = 0, groupId: Long = 0) =
        "crop?files=${Uri.encode(fileNames.joinToString(","))}&personId=$personId&familyId=$familyId&groupId=$groupId"
}

@Composable
private fun AppNavigation() {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }

    NavHost(navController = nav, startDestination = Routes.GROUPS) {
        composable(Routes.GROUPS) {
            GroupListScreen(
                onOpenGroup = { nav.navigate(Routes.group(it)) },
                onSearch = { nav.navigate(Routes.SEARCH) },
                onQuiz = { nav.navigate(Routes.quiz()) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable("group/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
            FamilyListScreen(
                groupId = it.arguments!!.getLong("id"),
                onBack = back,
                onOpenFamily = { id -> nav.navigate(Routes.family(id)) },
                onOpenPerson = { id -> nav.navigate(Routes.person(id)) },
                onQuiz = { id -> nav.navigate(Routes.quiz(id)) },
                onNewFamily = { groupId -> nav.navigate(Routes.addPeople(groupId)) },
                onCropGroupPhoto = { groupId, files -> nav.navigate(Routes.crop(files, groupId = groupId)) },
            )
        }
        composable(
            "addPeople?groupId={groupId}&familyId={familyId}",
            listOf(
                navArgument("groupId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("familyId") { type = NavType.LongType; defaultValue = 0L },
            ),
        ) {
            val args = it.arguments!!
            val familyId = args.getLong("familyId")
            AddPeopleScreen(
                groupId = args.getLong("groupId"),
                familyId = familyId,
                onBack = back,
                onSaved = { savedFamilyId ->
                    if (familyId == 0L && savedFamilyId != null) {
                        // New family: show it, and don't come back to this form.
                        nav.navigate(Routes.family(savedFamilyId)) {
                            popUpTo(it.destination.id) { inclusive = true }
                        }
                    } else {
                        nav.popBackStack()
                    }
                },
            )
        }
        composable("family/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
            val familyId = it.arguments!!.getLong("id")
            FamilyDetailScreen(
                familyId = familyId,
                onBack = back,
                onOpenPerson = { id -> nav.navigate(Routes.person(id)) },
                onAddPeople = { groupId -> nav.navigate(Routes.addPeople(groupId, familyId)) },
                onCropGroupPhoto = { files -> nav.navigate(Routes.crop(files, familyId = familyId)) },
            )
        }
        composable("person/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
            val personId = it.arguments!!.getLong("id")
            PersonDetailScreen(
                personId = personId,
                onBack = back,
                onCrop = { files -> nav.navigate(Routes.crop(files, personId = personId)) },
            )
        }
        composable(
            "crop?files={files}&personId={personId}&familyId={familyId}&groupId={groupId}",
            listOf(
                navArgument("files") { type = NavType.StringType },
                navArgument("personId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("familyId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("groupId") { type = NavType.LongType; defaultValue = 0L },
            ),
        ) {
            val args = it.arguments!!
            FaceCropScreen(
                fileNames = args.getString("files")!!.split(",").filter { it.isNotBlank() },
                personId = args.getLong("personId"),
                familyId = args.getLong("familyId"),
                groupId = args.getLong("groupId"),
                onDone = back,
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(onBack = back, onOpenPerson = { nav.navigate(Routes.person(it)) })
        }
        composable(
            "quiz?groupId={groupId}",
            listOf(navArgument("groupId") { type = NavType.LongType; defaultValue = 0L }),
        ) {
            QuizScreen(groupId = it.arguments!!.getLong("groupId"), onBack = back)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = back)
        }
    }
}
