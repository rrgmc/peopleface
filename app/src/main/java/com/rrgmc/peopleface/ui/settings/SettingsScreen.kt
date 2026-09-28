package com.rrgmc.peopleface.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rrgmc.peopleface.MainActivity
import com.rrgmc.peopleface.R
import com.rrgmc.peopleface.appContainer
import com.rrgmc.peopleface.data.BackupManager
import com.rrgmc.peopleface.data.db.AppDatabase
import com.rrgmc.peopleface.image.RecentPhotos
import com.rrgmc.peopleface.ui.common.ConfirmDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val backup = appContainer().backup
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pendingImport by rememberSaveable { mutableStateOf<String?>(null) }
    var restored by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var recentCount by remember { mutableIntStateOf(RecentPhotos.list(context).size) }
    val recentCleared = stringResource(R.string.recent_photos_cleared)

    val exportDone = stringResource(R.string.export_done)
    val exportFailed = stringResource(R.string.export_failed)
    val importInvalid = stringResource(R.string.import_invalid)
    val importFailed = stringResource(R.string.import_failed)

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val msg = try {
                backup.export(uri); exportDone
            } catch (e: Exception) {
                exportFailed
            }
            busy = false
            snackbar.showSnackbar(msg)
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.backup_explanation), style = MaterialTheme.typography.bodyMedium)
            val size = context.getDatabasePath(AppDatabase.FILE_NAME).length()
            Text(
                stringResource(R.string.database_size, Formatter.formatShortFileSize(context, size)),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                enabled = !busy,
                onClick = {
                    val date = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    exporter.launch("peopleface-$date.db")
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Upload, null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.export_db))
            }
            OutlinedButton(
                enabled = !busy,
                onClick = { importer.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Download, null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.import_db))
            }

            Text(
                stringResource(R.string.recent_photos),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(stringResource(R.string.recent_photos_explanation), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                enabled = recentCount > 0,
                onClick = {
                    RecentPhotos.clear(context)
                    recentCount = 0
                    scope.launch { snackbar.showSnackbar(recentCleared) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.DeleteSweep, null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.clear_recent_photos, recentCount))
            }
        }
    }

    pendingImport?.let { uriString ->
        ConfirmDialog(
            title = stringResource(R.string.import_db),
            text = stringResource(R.string.import_confirm),
            confirmLabel = stringResource(R.string.import_replace),
            onConfirm = {
                busy = true
                scope.launch {
                    try {
                        backup.import(Uri.parse(uriString))
                        restored = true
                    } catch (e: BackupManager.InvalidBackupException) {
                        snackbar.showSnackbar(importInvalid)
                    } catch (e: Exception) {
                        snackbar.showSnackbar(importFailed)
                    } finally {
                        busy = false
                    }
                }
            },
            onDismiss = { pendingImport = null },
        )
    }
    if (restored) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.import_done_title)) },
            text = { Text(stringResource(R.string.import_done_text)) },
            confirmButton = { TextButton(onClick = { restartApp(context) }) { Text(stringResource(R.string.restart)) } },
        )
    }
}

/** The database was replaced underneath Room, so start a fresh process. */
private fun restartApp(context: Context) {
    val intent = Intent.makeRestartActivityTask(ComponentName(context, MainActivity::class.java))
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
