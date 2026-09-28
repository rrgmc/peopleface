package com.rrgmc.peopleface

import android.net.Uri
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

    /** Crop a face for [personId], or (with [familyId]) for any member of a family from a group photo. */
    fun crop(uri: Uri, temporary: Boolean, personId: Long = 0, familyId: Long = 0) =
        "crop?uri=${Uri.encode(uri.toString())}&temp=$temporary&personId=$personId&familyId=$familyId"
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
            )
        }
        composable("family/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
            val familyId = it.arguments!!.getLong("id")
            FamilyDetailScreen(
                familyId = familyId,
                onBack = back,
                onOpenPerson = { id -> nav.navigate(Routes.person(id)) },
                onCropGroupPhoto = { uri, temp -> nav.navigate(Routes.crop(uri, temp, familyId = familyId)) },
            )
        }
        composable("person/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
            val personId = it.arguments!!.getLong("id")
            PersonDetailScreen(
                personId = personId,
                onBack = back,
                onCrop = { uri, temp -> nav.navigate(Routes.crop(uri, temp, personId = personId)) },
            )
        }
        composable(
            "crop?uri={uri}&temp={temp}&personId={personId}&familyId={familyId}",
            listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("temp") { type = NavType.BoolType; defaultValue = false },
                navArgument("personId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("familyId") { type = NavType.LongType; defaultValue = 0L },
            ),
        ) {
            val args = it.arguments!!
            FaceCropScreen(
                uri = Uri.parse(args.getString("uri")!!),
                temporary = args.getBoolean("temp"),
                personId = args.getLong("personId"),
                familyId = args.getLong("familyId"),
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
