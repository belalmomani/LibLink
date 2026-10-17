package com.liblink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liblink.app.R
import com.liblink.app.data.Project
import com.liblink.app.git.GitManager
import com.liblink.app.git.RepoSummary
import java.io.File
import java.text.DateFormat
import java.util.Date

// حالة المشروع: متزامن / توجد تغييرات / فشل آخر رفع / محلي
private enum class PState(val color: Color, val label: Int) {
    SYNCED(Color(0xFF22C55E), R.string.st_synced),
    CHANGES(Color(0xFFF59E0B), R.string.st_changes),
    FAILED(Color(0xFFEF4444), R.string.st_failed),
    LOCAL(Color(0xFF94A3B8), R.string.st_local),
    BROKEN(Color(0xFFEF4444), R.string.st_broken),
    UNKNOWN(Color(0xFF64748B), R.string.st_checking)
}

private enum class Filter(val label: Int) {
    ALL(R.string.f_all), SYNCED(R.string.st_synced), CHANGES(R.string.st_changes),
    FAILED(R.string.st_failed), LOCAL(R.string.st_local)
}

// computed = اكتمل الفحص؛ s == null بعده يعني أن المجلد ليس مستودعًا صالحًا
private fun classify(p: Project, s: RepoSummary?, computed: Boolean): PState = when {
    !computed -> PState.UNKNOWN
    s == null -> PState.BROKEN
    p.pushState == "failed" -> PState.FAILED
    !s.hasRemote -> PState.LOCAL
    s.changes > 0 || s.ahead > 0 -> PState.CHANGES
    else -> PState.SYNCED
}

// اسم المستودع من الرابط: https://github.com/user/repo.git ← repo
private fun repoName(url: String) =
    url.trim().trimEnd('/').substringAfterLast('/').removeSuffix(".git")

private data class HomeRow(val p: Project, val s: RepoSummary?, val state: PState, val last: Long)

@Composable
fun HomeScreen(
    projects: List<Project>,
    onAdd: () -> Unit,
    onLibrary: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var byDate by remember { mutableStateOf(true) }
    var summaries by remember { mutableStateOf<Map<String, RepoSummary?>>(emptyMap()) }

    // حساب حالة كل مشروع تدريجيًا في الخلفية
    LaunchedEffect(projects) {
        val gm = GitManager()
        for (p in projects) summaries = summaries + (p.id to gm.summary(File(p.path)))
    }

    val rows = remember(projects, summaries, query, filter, byDate) {
        projects.map { p ->
            val s = summaries[p.id]
            HomeRow(p, s, classify(p, s, summaries.containsKey(p.id)), maxOf(p.updatedAt, s?.lastCommitMs ?: 0L))
        }.filter { r ->
            val q = query.trim().lowercase()
            (q.isEmpty() || r.p.name.lowercase().contains(q) || repoName(r.p.remoteUrl).lowercase().contains(q) ||
                r.p.remoteUrl.lowercase().contains(q)) &&
                when (filter) {
                    Filter.ALL -> true
                    Filter.SYNCED -> r.state == PState.SYNCED
                    Filter.CHANGES -> r.state == PState.CHANGES
                    Filter.FAILED -> r.state == PState.FAILED
                    Filter.LOCAL -> r.state == PState.LOCAL
                }
        }.let { l -> if (byDate) l.sortedByDescending { it.last } else l.sortedBy { it.p.name.lowercase() } }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppBar(stringResource(R.string.home_title), actions = {
                TextButton(onClick = onLibrary) { Text(stringResource(R.string.library)) }
                TextButton(onClick = onSettings) { Text(stringResource(R.string.settings)) }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd) { Text("+  " + stringResource(R.string.add_project)) }
        },
        bottomBar = { AdBanner(Modifier.fillMaxWidth().navigationBarsPadding()) }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            if (projects.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_projects))
                }
                return@Column
            }
            // بحث سريع
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text(stringResource(R.string.search_hint)) }, singleLine = true)
            // تصنيف حسب الحالة + ترتيب
            Row(verticalAlignment = Alignment.CenterVertically) {
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Filter.values().toList()) { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f },
                            label = { Text(stringResource(f.label)) })
                    }
                }
                TextButton({ byDate = !byDate }) {
                    Text(stringResource(if (byDate) R.string.sort_date else R.string.sort_name))
                }
            }

            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_results))
                }
            } else LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(rows, key = { it.p.id }) { r ->
                    Card(
                        Modifier.fillMaxWidth().clickable { onOpen(r.p.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(Modifier.height(IntrinsicSize.Min)) {
                            // شريط لوني يدل على الحالة
                            Box(Modifier.width(6.dp).fillMaxHeight().background(r.state.color))
                            Column(Modifier.padding(14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(r.p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Surface(color = r.state.color.copy(alpha = 0.2f), shape = RoundedCornerShape(50)) {
                                        Text(stringResource(r.state.label), color = r.state.color,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                                    }
                                }
                                val repo = repoName(r.p.remoteUrl)
                                if (repo.isNotBlank())
                                    Text(stringResource(R.string.repo_label, repo), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary)
                                if (r.last > 0)
                                    Text(stringResource(R.string.last_update,
                                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(r.last))),
                                        style = MaterialTheme.typography.bodySmall)
                                r.s?.let { s ->
                                    if (s.branch.isNotBlank())
                                        Text("\u2442 " + s.branch, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
