package com.liblink.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liblink.app.R
import com.liblink.app.data.Project

@Composable
fun HomeScreen(
    projects: List<Project>,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppBar(stringResource(R.string.home_title), actions = {
                TextButton(onClick = onSettings) { Text(stringResource(R.string.settings)) }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd) { Text("+  " + stringResource(R.string.add_project)) }
        },
        bottomBar = { AdBanner(Modifier.fillMaxWidth().navigationBarsPadding()) }
    ) { pad ->
        if (projects.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_projects))
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(projects, key = { it.id }) { p ->
                    Card(
                        Modifier.fillMaxWidth().clickable { onOpen(p.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(p.path, style = MaterialTheme.typography.bodySmall)
                            if (p.remoteUrl.isNotBlank())
                                Text(p.remoteUrl, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
