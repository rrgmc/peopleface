package com.rrgmc.peopleface

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rrgmc.peopleface.data.AppContainer
import com.rrgmc.peopleface.ui.addpeople.AddPeopleScreen
import com.rrgmc.peopleface.ui.common.ChooseGroupDialog
import com.rrgmc.peopleface.ui.common.MAX_PICKED_PHOTOS
import com.rrgmc.peopleface.ui.common.copyToCache
import com.rrgmc.peopleface.ui.common.pickedFile
import com.rrgmc.peopleface.ui.crop.FaceCropScreen
import com.rrgmc.peopleface.ui.families.FamilyListScreen
import com.rrgmc.peopleface.ui.family.FamilyDetailScreen
import com.rrgmc.peopleface.ui.groups.GroupListScreen
import com.rrgmc.peopleface.ui.person.PersonDetailScreen
import com.rrgmc.peopleface.ui.quiz.QuizScreen
import com.rrgmc.peopleface.ui.search.SearchScreen
import com.rrgmc.peopleface.ui.settings.SettingsScreen
import com.rrgmc.peopleface.ui.theme.PeopleFaceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** Pictures shared from another app (copied like [copyToCache] does), waiting for a group to be chosen. */
    private var sharedFiles by mutableStateOf<List<String>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Show "Ultra HDR" photos (gain map) as bright as the gallery does; no effect on other content.
            window.colorMode = ActivityInfo.COLOR_MODE_HDR
        }
        if (savedInstanceState == null) {
            receiveShared(intent)
        } else {
            sharedFiles = savedInstanceState.getStringArrayList(KEY_SHARED_FILES).orEmpty()
        }
        setContent {
            PeopleFaceTheme { AppNavigation(sharedFiles, onSharedHandled = { sharedFiles = emptyList() }) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(KEY_SHARED_FILES, ArrayList(sharedFiles))
    }

    /** Copies images sent with "Share" (e.g. from Google Drive or Dropbox) while their read permission lasts. */
    private fun receiveShared(intent: Intent) {
        val uris = when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> return
        }.take(MAX_PICKED_PHOTOS)
        if (uris.isEmpty()) return
        lifecycleScope.launch {
            val names = withContext(Dispatchers.IO) {
                uris.mapIndexedNotNull { i, uri ->
                    try {
                        copyToCache(this@MainActivity, uri, i).name
                    } catch (e: Exception) {
                        Log.e("PeopleFace", "Cannot copy shared image $uri", e)
                        null
                    }
                }
            }
            if (names.size < uris.size) {
                Toast.makeText(this@MainActivity, R.string.image_load_error, Toast.LENGTH_LONG).show()
            }
            if (names.isNotEmpty()) sharedFiles = names
        }
    }

    private companion object {
        const val KEY_SHARED_FILES = "sharedFiles"
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

/** [sharedFiles]: pictures shared from another app; a group is asked for, then their faces are cropped. */
@Composable
private fun AppNavigation(sharedFiles: List<String>, onSharedHandled: () -> Unit) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    val context = LocalContext.current

    if (sharedFiles.isNotEmpty()) {
        ChooseGroupDialog(
            onPick = { groupId ->
                nav.navigate(Routes.crop(sharedFiles, groupId = groupId))
                onSharedHandled()
            },
            onDismiss = {
                sharedFiles.forEach { pickedFile(context, it).delete() }
                onSharedHandled()
            },
        )
    }

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
