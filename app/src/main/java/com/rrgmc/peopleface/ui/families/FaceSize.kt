package com.rrgmc.peopleface.ui.families

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rrgmc.peopleface.R

/** Size of the people's pictures in the family cards of a group; [width] also leaves room for the name. */
enum class FaceSize(@StringRes val label: Int, val avatar: Dp, val width: Dp) {
    SMALL(R.string.face_size_small, 48.dp, 60.dp),
    NORMAL(R.string.face_size_normal, 64.dp, 72.dp),
    LARGE(R.string.face_size_large, 88.dp, 96.dp),
}

private const val PREFS = "settings"
private const val KEY_FACE_SIZE = "group_face_size"

/** The chosen [FaceSize], the same for every group. Kept in the phone's preferences, not in the database. */
fun loadFaceSize(context: Context): FaceSize {
    val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FACE_SIZE, null)
    return FaceSize.entries.firstOrNull { it.name == saved } ?: FaceSize.NORMAL
}

fun saveFaceSize(context: Context, size: FaceSize) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_FACE_SIZE, size.name).apply()
}
