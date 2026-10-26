package com.liblink.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liblink.app.R
import com.liblink.app.data.ProjectRepository
import com.liblink.app.data.libKey
import com.liblink.app.sync.SyncManager

// مشاريع أجهزتك الأخرى (اسم وعنوان فقط): استنساخ بنقرة أو إخفاء من القائمة
@Composable
fun CloudProjectsScreen(repo: ProjectRepository, onBack: () -> Unit, onClone: (url: String, name: String) -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val version = SyncManager.changeCount
    val items = remember(tick, version) {
        val local = repo.projects().map { libKey(it.remoteUrl) }.toSet()
        repo.cloudProjects().filter { libKey(it.url) !in local }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppBar(stringResource(R.string.cloud_title), onBack) }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.cloud_note), style = MaterialTheme.typography.bodySmall)
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.cloud_empty)) }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(items, key = { libKey(it.url) }) { p ->
                    Card(Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(p.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button({ onClone(p.url, p.name) }) { Text(stringResource(R.string.cloud_clone)) }
                                OutlinedButton({ repo.hideCloudProject(p.url); tick++ }) { Text(stringResource(R.string.cloud_hide)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
