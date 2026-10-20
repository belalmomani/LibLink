package com.liblink.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.liblink.app.R
import com.liblink.app.data.DraftStore
import com.liblink.app.data.LibraryRepository
import com.liblink.app.data.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Screen {
    object Home : Screen
    data class Add(val url: String = "", val name: String = "") : Screen
    data class Library(val share: String? = null) : Screen
    object Settings : Screen
    data class Trash(val from: Screen) : Screen
    data class Project(val id: String) : Screen
    data class Files(val id: String, val dir: String? = null) : Screen
    data class Editor(val id: String, val path: String) : Screen
    data class Branches(val id: String) : Screen
    data class Build(val id: String) : Screen
    data class Doctor(val id: String) : Screen
    data class Releases(val id: String) : Screen
}

@Composable
fun LibLinkRoot(
    shared: String? = null,
    openBuild: String? = null,
    onShareConsumed: () -> Unit = {},
    onBuildConsumed: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val repo = remember { ProjectRepository(ctx) }
    val drafts = remember { DraftStore(ctx) }
    val lib = remember { LibraryRepository(ctx) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var version by remember { mutableIntStateOf(0) } // لتحديث القائمة بعد أي تغيير

    // ---- استعادة المكتبة بعد إعادة تثبيت التطبيق ----
    val access = rememberAccessGranted()
    val requestAccess = rememberAccessRequester()
    var askAccess by remember { mutableStateOf(false) }
    var restoreCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(access) {
        if (!lib.backupHandled()) {
            if (access) {
                val n = withContext(Dispatchers.IO) { lib.backupMissingCount() }
                if (n > 0) restoreCount = n else { lib.markBackupHandled(); lib.writeBackup() }
            } else if (lib.items().isEmpty() && !lib.restoreAsked()) askAccess = true
        } else if (access) lib.writeBackup()
    }

    // رابط مُشارَك من تطبيق آخر ← شاشة المكتبة مع نافذة إضافة جاهزة
    LaunchedEffect(shared) {
        if (shared != null) { screen = Screen.Library(shared); onShareConsumed() }
    }
    // نقر على إشعار البناء ← شاشة بناء المشروع
    LaunchedEffect(openBuild) {
        if (openBuild != null) {
            if (repo.find(openBuild) != null) screen = Screen.Build(openBuild)
            onBuildConsumed()
        }
    }

    // الرجوع: المحرر ← الملفات ← المشروع ← الرئيسية
    BackHandler(enabled = screen != Screen.Home) {
        screen = when (val s = screen) {
            is Screen.Editor -> Screen.Files(s.id, File(s.path).parent)
            is Screen.Files -> Screen.Project(s.id)
            is Screen.Branches -> Screen.Project(s.id)
            is Screen.Build -> Screen.Project(s.id)
            is Screen.Doctor -> Screen.Project(s.id)
            is Screen.Releases -> Screen.Build(s.id)
            is Screen.Trash -> s.from
            else -> Screen.Home
        }
    }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            projects = remember(version) { repo.projects() },
            onAdd = { screen = Screen.Add() },
            onLibrary = { screen = Screen.Library() },
            onOpen = { screen = Screen.Project(it) },
            onSettings = { screen = Screen.Settings }
        )
        is Screen.Add -> AddProjectScreen(repo, s.url, s.name,
            onBack = { screen = Screen.Home },
            onDone = { version++; screen = Screen.Home })
        is Screen.Library -> LibraryScreen(
            share = s.share,
            onBack = { screen = Screen.Home },
            onClone = { url, name -> screen = Screen.Add(url, name) })
        Screen.Settings -> SettingsScreen(repo, onBack = { screen = Screen.Home },
            onTrash = { screen = Screen.Trash(Screen.Settings) })
        is Screen.Trash -> TrashScreen(onBack = { screen = s.from })
        is Screen.Project -> {
            val p = repo.find(s.id)
            if (p == null) screen = Screen.Home
            else ProjectScreen(p, repo,
                onBack = { version++; screen = Screen.Home },
                onFiles = { screen = Screen.Files(s.id) },
                onBranches = { screen = Screen.Branches(s.id) },
                onBuild = { screen = Screen.Build(s.id) },
                onDoctor = { screen = Screen.Doctor(s.id) },
                onRemoved = { version++; screen = Screen.Home })
        }
        is Screen.Files -> {
            val p = repo.find(s.id)
            if (p == null) screen = Screen.Home
            else FilesScreen(p, s.dir,
                onBack = { screen = Screen.Project(s.id) },
                onOpenFile = { f -> screen = Screen.Editor(s.id, f.absolutePath) },
                onBranches = { screen = Screen.Branches(s.id) },
                onTrash = { screen = Screen.Trash(s) })
        }
        is Screen.Editor -> EditorScreen(File(s.path), drafts,
            onBack = { screen = Screen.Files(s.id, File(s.path).parent) })
        is Screen.Build -> {
            val p = repo.find(s.id)
            if (p == null) screen = Screen.Home
            else BuildScreen(p, repo, onBack = { screen = Screen.Project(s.id) },
                onReleases = { screen = Screen.Releases(s.id) })
        }
        is Screen.Doctor -> {
            val p = repo.find(s.id)
            if (p == null) screen = Screen.Home
            else DoctorScreen(p, repo, onBack = { screen = Screen.Project(s.id) })
        }
        is Screen.Releases -> {
            val p = repo.find(s.id)
            if (p == null) screen = Screen.Home
            else ReleasesScreen(p, repo, onBack = { screen = Screen.Build(s.id) })
        }
        is Screen.Branches -> {
            val p = repo.find(s.id)
            if (p == null) screen = Screen.Home
            else BranchesScreen(p, repo, onBack = { screen = Screen.Project(s.id) })
        }
    }

    // سؤال عن منح صلاحية الملفات لفحص وجود نسخة احتياطية من المكتبة
    if (askAccess) {
        AlertDialog(
            onDismissRequest = { lib.setRestoreAsked(); askAccess = false },
            title = { Text(stringResource(R.string.restore_ask_title)) },
            text = { Text(stringResource(R.string.restore_ask_msg)) },
            confirmButton = {
                TextButton({ lib.setRestoreAsked(); askAccess = false; requestAccess() }) {
                    Text(stringResource(R.string.grant))
                }
            },
            dismissButton = {
                TextButton({ lib.setRestoreAsked(); askAccess = false }) { Text(stringResource(R.string.not_now)) }
            }
        )
    }

    // وُجدت مكتبات محفوظة من تثبيت سابق
    if (restoreCount > 0) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.restore_found_title)) },
            text = { Text(stringResource(R.string.restore_found_msg, restoreCount)) },
            confirmButton = {
                TextButton({
                    val n = lib.restoreFromBackup()
                    restoreCount = 0
                    Toast.makeText(ctx, ctx.getString(R.string.restore_done, n), Toast.LENGTH_LONG).show()
                }) { Text(stringResource(R.string.lib_restore_btn)) }
            },
            dismissButton = {
                TextButton({ restoreCount = 0 }) {
                    Text(stringResource(R.string.not_now))
                }
            }
        )
    }
}
