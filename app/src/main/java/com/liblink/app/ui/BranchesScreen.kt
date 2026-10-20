package com.liblink.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liblink.app.R
import com.liblink.app.data.Project
import com.liblink.app.data.ProjectRepository
import com.liblink.app.git.BranchInfo
import com.liblink.app.git.GitManager
import kotlinx.coroutines.launch
import java.io.File

private sealed interface BDlg {
    object New : BDlg
    data class Delete(val name: String) : BDlg
}

// اختيار الفرع: الانتقال، إنشاء فرع جديد، حذف فرع محلي (مثل Choose Branch في MGit)
@Composable
fun BranchesScreen(project: Project, repo: ProjectRepository, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dir = remember(project.path) { File(project.path) }
    var list by remember { mutableStateOf<List<BranchInfo>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var dlg by remember { mutableStateOf<BDlg?>(null) }

    LaunchedEffect(tick) {
        list = runCatching { GitManager().branches(dir) }.getOrDefault(emptyList())
    }

    fun run(ok: String, block: suspend (GitManager) -> Unit) {
        scope.launch {
            busy = true
            msg = try { block(GitManager(repo.credentials())); repo.markUpdated(project.id); tick++; ok }
            catch (e: Exception) { ctx.getString(R.string.error_prefix, e.message ?: e.toString()) }
            busy = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppBar(stringResource(R.string.branch_title), onBack, actions = {
                TextButton({ dlg = BDlg.New }) { Text("+  " + stringResource(R.string.new_branch)) }
            })
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            Busy(busy)
            if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                for (remote in listOf(false, true)) {
                    val group = list.filter { it.remote == remote }
                    if (group.isNotEmpty()) {
                        item {
                            Text(stringResource(if (remote) R.string.remote_branches else R.string.local_branches),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp))
                        }
                        items(group, key = { (if (it.remote) "r:" else "l:") + it.name }) { b ->
                            Card(Modifier.fillMaxWidth().clickable(enabled = !busy && !b.current) {
                                run(ctx.getString(R.string.checkout_ok, b.name)) { gm -> gm.checkout(dir, b) }
                            }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text((if (b.current) "\u2713  " else "\u2442  ") + b.name, Modifier.weight(1f),
                                        color = if (b.current) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface)
                                    if (!b.remote && !b.current) {
                                        TextButton({ dlg = BDlg.Delete(b.name) }) {
                                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    when (val d = dlg) {
        null -> {}
        BDlg.New -> FieldsDialog(stringResource(R.string.new_branch),
            listOf(stringResource(R.string.branch_name)), listOf(""),
            onConfirm = { v ->
                dlg = null
                if (v[0].isNotBlank()) run(ctx.getString(R.string.branch_created)) { gm -> gm.createBranch(dir, v[0]) }
            }, onDismiss = { dlg = null })
        is BDlg.Delete -> ConfirmDialog(stringResource(R.string.delete),
            stringResource(R.string.delete_branch_confirm, d.name),
            onConfirm = { dlg = null; run(ctx.getString(R.string.branch_deleted)) { gm -> gm.deleteBranch(dir, d.name) } },
            onDismiss = { dlg = null })
    }
}
