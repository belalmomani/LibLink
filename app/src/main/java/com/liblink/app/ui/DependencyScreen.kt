package com.liblink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liblink.app.R
import com.liblink.app.data.Project
import com.liblink.app.storage.DepRef
import com.liblink.app.storage.DepStatus
import com.liblink.app.storage.Deps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// فاحص تحديثات المكتبات: يقرأ build.gradle ويعرض أحدث الإصدارات مع إمكانية تحديث سطر بسطر
@Composable
fun DependencyScreen(project: Project, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dir = remember(project.path) { File(project.path) }
    var refs by remember { mutableStateOf<List<DepRef>>(emptyList()) }
    var results by remember { mutableStateOf<List<DepStatus>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var onlyUpdates by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<DepStatus?>(null) }

    fun rescan() { scope.launch { refs = withContext(Dispatchers.IO) { Deps.scan(dir) } } }

    fun check() {
        scope.launch {
            busy = true
            msg = ctx.getString(R.string.deps_checking)
            val scanned = withContext(Dispatchers.IO) { Deps.scan(dir) }
            refs = scanned
            results = try { Deps.check(scanned) } catch (e: Exception) { emptyList() }
            msg = ""
            busy = false
        }
    }
    LaunchedEffect(Unit) { rescan() }

    val updates = results.count { it.level >= 1 }
    val majors = results.count { it.level == 2 }
    val shown = if (results.isEmpty()) refs.map { DepStatus(it, null, -2) } else results.filter { !onlyUpdates || it.level >= 1 }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppBar(stringResource(R.string.deps_title), onBack, actions = {
                TextButton({ check() }, enabled = !busy) { Text(stringResource(R.string.deps_check)) }
            })
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Busy(busy)
            if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (results.isNotEmpty()) {
                Text(stringResource(R.string.deps_summary, updates, majors), style = MaterialTheme.typography.titleSmall,
                    color = if (updates > 0) Color(0xFFF59E0B) else Color(0xFF22C55E))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.deps_updates_only), Modifier.weight(1f))
                    Switch(onlyUpdates, { onlyUpdates = it })
                }
                if (majors > 0) Text(stringResource(R.string.deps_warn_major), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            if (refs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.deps_none)) }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(shown, key = { it.ref.file + "|" + it.ref.original }) { st ->
                    val (color, label) = when (st.level) {
                        0 -> Color(0xFF22C55E) to R.string.deps_ok
                        1 -> Color(0xFFF59E0B) to R.string.deps_minor
                        2 -> Color(0xFFEF4444) to R.string.deps_major
                        -1 -> Color(0xFF94A3B8) to R.string.deps_unknown
                        else -> Color(0xFF64748B) to R.string.deps_unchecked
                    }
                    Card(Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.height(IntrinsicSize.Min)) {
                            Box(Modifier.width(6.dp).fillMaxHeight().background(color))
                            Column(Modifier.padding(12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(st.ref.coord, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(50)) {
                                        Text(stringResource(label), color = color, style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                                    }
                                }
                                Text(st.ref.current + if (st.latest != null && st.level >= 1) "  →  " + st.latest else "",
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(R.string.deps_file, st.ref.file), style = MaterialTheme.typography.bodySmall)
                                if (st.level >= 1) OutlinedButton({ pending = st }, enabled = !busy) { Text(stringResource(R.string.deps_update)) }
                            }
                        }
                    }
                }
            }
        }
    }

    // معاينة التعديل قبل تنفيذه
    pending?.let { st ->
        ConfirmDialog(stringResource(R.string.deps_preview_title, st.ref.coord),
            Deps.previewLine(st) + "\n" + stringResource(R.string.deps_file, st.ref.file) +
                (if (st.level == 2) "\n\n" + stringResource(R.string.deps_warn_major) else ""),
            onConfirm = {
                pending = null
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Deps.apply(ctx, project.path, dir, st) }
                    msg = if (ok) ctx.getString(R.string.deps_applied, st.ref.coord) else ctx.getString(R.string.error_prefix, "—")
                    if (ok) check()
                }
            }, onDismiss = { pending = null })
    }
}
