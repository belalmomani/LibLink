package com.liblink.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.liblink.app.data.ProjectRepository

sealed interface Screen {
    object Home : Screen
    object Add : Screen
    object Settings : Screen
    data class Project(val id: String) : Screen
    data class Files(val id: String) : Screen
}

@Composable
fun LibLinkRoot() {
    val ctx = LocalContext.current
    val repo = remember { ProjectRepository(ctx) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var version by remember { mutableIntStateOf(0) } // لتحديث القائمة بعد أي تغيير

    // الرجوع: من الملفات إلى المشروع، ومن أي شاشة أخرى إلى الرئيسية
    BackHandler(enabled = screen != Screen.Home) {
        screen = when (val s = screen) {
            is Screen.Files -> Screen.Project(s.id)
            else -> Screen.Home
        }
    }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            projects = remember(version) { repo.projects() },
            onAdd = { screen = Screen.Add },
            onOpen = { screen = Screen.Project(it) },
            onSettings = { screen = Screen.Settings }
        )
        Screen.Add -> AddProjectScreen(repo,
            onBack = { screen = Screen.Home },
            onDone = { version++; screen = Screen.Home })
        Screen.Settings -> SettingsScreen(repo, onBack = { screen = Screen.Home })
        is Screen.Project -> {
            val p = repo.find(s.id)
            if (p == null) screen = Screen.Home
            else ProjectScreen(p, repo,
                onBack = { version++; screen = Screen.Home },
                onFiles = { screen = Screen.Files(s.id) },
                onRemoved = { version++; screen = Screen.Home })
        }
        is Screen.Files -> {
            val p = repo.find(s.id)
            if (p == null) screen = Screen.Home
            else FilesScreen(p, onBack = { screen = Screen.Project(s.id) })
        }
    }
}
