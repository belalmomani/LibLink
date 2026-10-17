package com.liblink.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liblink.app.R
import com.liblink.app.data.Project
import com.liblink.app.data.ProjectRepository
import com.liblink.app.git.GitManager
import com.liblink.app.storage.ProjectStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// الحوارات التي تفتحها قائمة العمليات
private sealed interface Dlg {
    object Commit : Dlg
    object Reset : Dlg
    object Merge : Dlg
    object Rebase : Dlg
    object CherryPick : Dlg
    object NewFile : Dlg
    object NewDir : Dlg
    object AddRemote : Dlg
    object Delete : Dlg
    data class Raw(val text: String) : Dlg
    data class Cfg(val name: String, val email: String) : Dlg
    data class Info(val title: String, val text: String) : Dlg
}

@Composable
fun ProjectScreen(
    project: Project,
    repo: ProjectRepository,
    onBack: () -> Unit,
    onFiles: () -> Unit,
    onBranches: () -> Unit,
    onRemoved: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dir = remember(project.path) { File(project.path) }
    var remote by remember { mutableStateOf(project.remoteUrl) }
    var commitMsg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    var menu by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableIntStateOf(0) } // ملاحظة "؟" المفتوحة حاليًا
    var branch by remember { mutableStateOf("") }

    // تحديث اسم الفرع بعد كل عملية
    LaunchedEffect(busy) {
        if (!busy) branch = runCatching { GitManager().currentBranch(dir) }.getOrDefault("")
    }

    fun err(e: Exception) = ctx.getString(R.string.error_prefix, e.message ?: e.toString())

    // تشغيل عملية Git وعرض النتيجة أو الخطأ
    fun op(block: suspend (GitManager) -> String) {
        scope.launch {
            busy = true
            log = try { block(GitManager(repo.credentials())) } catch (e: Exception) { err(e) }
            busy = false
        }
    }

    // نفس الشيء لكن النتيجة تُعرض في حوار
    fun opDlg(block: suspend (GitManager) -> Dlg) {
        scope.launch {
            busy = true
            try { dlg = block(GitManager(repo.credentials())) } catch (e: Exception) { log = err(e) }
            busy = false
        }
    }

    suspend fun statusText(gm: GitManager): String {
        val s = gm.status(dir)
        return if (s.total == 0) ctx.getString(R.string.status_clean)
        else ctx.getString(R.string.status_changes, s.total, s.added, s.modified, s.deleted)
    }

    // يرجع true إذا لم يُضبط الـ Token
    fun missingCreds(): Boolean {
        val m = repo.credentials().token.isBlank()
        if (m) log = ctx.getString(R.string.no_credentials)
        return m
    }

    fun doStage() = op { gm -> gm.stageAll(dir); ctx.getString(R.string.staged_ok) + "\n" + statusText(gm) }

    fun doCommit(msg: String) {
        if (msg.isBlank()) { log = ctx.getString(R.string.commit_empty); return }
        op { gm ->
            val id = gm.commit(dir, msg.trim())
            commitMsg = ""
            repo.markUpdated(project.id)
            ctx.getString(R.string.commit_ok, id)
        }
    }

    // الرفع مع تسجيل نجاحه أو فشله ليظهر في قائمة المشاريع
    fun doPush() {
        if (remote.isBlank()) log = ctx.getString(R.string.no_remote)
        else if (!missingCreds()) op { gm ->
            gm.setRemote(dir, remote.trim())
            repo.setRemote(project.id, remote.trim())
            try { gm.push(dir) } catch (e: Exception) { repo.setPushState(project.id, "failed"); throw e }
            repo.setPushState(project.id, "ok")
            ctx.getString(R.string.push_ok)
        }
    }

    fun doPull() {
        if (!missingCreds()) op { gm -> gm.pull(dir); repo.markUpdated(project.id); ctx.getString(R.string.pull_ok) }
    }
    fun doFetch() { if (!missingCreds()) op { gm -> gm.fetch(dir); ctx.getString(R.string.fetch_ok) } }

    fun saveRemote(url: String) {
        if (url.isBlank()) { log = ctx.getString(R.string.no_remote); return }
        op { gm ->
            gm.setRemote(dir, url.trim())
            repo.setRemote(project.id, url.trim())
            remote = url.trim()
            ctx.getString(R.string.remote_added)
        }
    }

    fun removeRemote() = op { gm ->
        gm.removeRemote(dir)
        repo.setRemote(project.id, "")
        remote = ""
        ctx.getString(R.string.remote_removed)
    }

    // إنشاء ملف أو مجلد في جذر المشروع
    fun create(name: String, isDir: Boolean) = op { _ ->
        withContext(Dispatchers.IO) {
            val f = ProjectStorage.resolveInside(dir, name)
                ?: return@withContext ctx.getString(R.string.invalid_name)
            if (isDir) f.mkdirs() else { f.parentFile?.mkdirs(); f.createNewFile() }
            ctx.getString(R.string.created)
        }
    }

    fun deleteProject() {
        scope.launch {
            busy = true
            withContext(Dispatchers.IO) { dir.deleteRecursively() }
            repo.remove(project.id)
            busy = false
            onRemoved()
        }
    }

    // الخطوة 1: نسخ ملفات من الهاتف إلى مجلد المشروع (بأسماء غير مكررة)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) op { gm ->
            val n = withContext(Dispatchers.IO) { ProjectStorage.copyInto(ctx, dir, uris) }
            repo.markUpdated(project.id)
            ctx.getString(R.string.files_copied, n) + "\n" + statusText(gm)
        }
    }

    // عنصر قائمة مع علامة "؟" تعرض ملاحظة صغيرة عن عمله
    @Composable
    fun MenuItem(res: Int, help: Int, action: () -> Unit) {
        DropdownMenuItem(
            text = { Text(stringResource(res)) },
            onClick = { menu = false; helpOpen = 0; action() },
            trailingIcon = {
                Text(
                    "?", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { helpOpen = if (helpOpen == help) 0 else help }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        )
        if (helpOpen == help) {
            Text(
                stringResource(help), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).widthIn(max = 280.dp)
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppBar(project.name, onBack, actions = {
                Box {
                    TextButton({ menu = true }) { Text("\u22EE") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false; helpOpen = 0 }) {
                        MenuItem(R.string.m_pull, R.string.h_pull) { doPull() }
                        MenuItem(R.string.m_push, R.string.h_push) { doPush() }
                        MenuItem(R.string.m_stage, R.string.h_stage) { doStage() }
                        MenuItem(R.string.m_commit, R.string.h_commit) { dlg = Dlg.Commit }
                        MenuItem(R.string.m_reset, R.string.h_reset) { dlg = Dlg.Reset }
                        MenuItem(R.string.m_merge, R.string.h_merge) { dlg = Dlg.Merge }
                        MenuItem(R.string.m_fetch, R.string.h_fetch) { doFetch() }
                        MenuItem(R.string.m_rebase, R.string.h_rebase) { dlg = Dlg.Rebase }
                        MenuItem(R.string.m_cherry, R.string.h_cherry) { dlg = Dlg.CherryPick }
                        MenuItem(R.string.m_branches, R.string.h_branches) { onBranches() }
                        MenuItem(R.string.m_diff, R.string.h_diff) {
                            opDlg { gm ->
                                val t = gm.diff(dir)
                                Dlg.Info(ctx.getString(R.string.m_diff),
                                    if (t.isBlank()) ctx.getString(R.string.diff_empty) else t.take(30000))
                            }
                        }
                        MenuItem(R.string.m_new_file, R.string.h_new_file) { dlg = Dlg.NewFile }
                        MenuItem(R.string.m_new_dir, R.string.h_new_dir) { dlg = Dlg.NewDir }
                        MenuItem(R.string.m_add_remote, R.string.h_add_remote) { dlg = Dlg.AddRemote }
                        MenuItem(R.string.m_remove_remote, R.string.h_remove_remote) { removeRemote() }
                        MenuItem(R.string.m_delete, R.string.h_delete) { dlg = Dlg.Delete }
                        MenuItem(R.string.m_raw_config, R.string.h_raw_config) {
                            opDlg { gm -> Dlg.Raw(gm.readConfig(dir)) }
                        }
                        MenuItem(R.string.m_config, R.string.h_config) {
                            opDlg { gm -> val (n, e) = gm.getUser(dir); Dlg.Cfg(n, e) }
                        }
                    }
                }
            })
        },
        // أسفل المشروع: الفرع الحالي (النقر يفتح خيارات الفروع) ثم الإعلان
        bottomBar = {
            Column {
                BranchBar(branch, onBranches)
                AdBanner(Modifier.fillMaxWidth().navigationBarsPadding())
            }
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.path_label, project.path), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onFiles, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.browse_files))
            }
            Text(stringResource(R.string.steps_title), style = MaterialTheme.typography.titleLarge)

            StepCard(stringResource(R.string.step1_title), stringResource(R.string.step1_desc)) {
                Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy) {
                    Text(stringResource(R.string.step1_btn))
                }
            }

            StepCard(stringResource(R.string.step2_title), stringResource(R.string.step2_desc)) {
                Button(onClick = { doStage() }, enabled = !busy) { Text(stringResource(R.string.step2_btn)) }
            }

            StepCard(stringResource(R.string.step3_title)) {
                OutlinedTextField(commitMsg, { commitMsg = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.commit_message)) })
                Button(onClick = { doCommit(commitMsg) }, enabled = !busy) {
                    Text(stringResource(R.string.step3_btn))
                }
            }

            StepCard(stringResource(R.string.step4_title), stringResource(R.string.step4_desc)) {
                OutlinedTextField(remote, { remote = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.remote_url)) })
                Button(onClick = { doPush() }, enabled = !busy) { Text(stringResource(R.string.step4_btn)) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { op { gm -> statusText(gm) } }, enabled = !busy) {
                    Text(stringResource(R.string.status_btn))
                }
                OutlinedButton(onClick = { doPull() }, enabled = !busy) {
                    Text(stringResource(R.string.pull_btn))
                }
            }

            Busy(busy)
            if (log.isNotBlank()) Text(log)

            TextButton(onClick = { repo.remove(project.id); onRemoved() }) {
                Text(stringResource(R.string.remove_project), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    // ---- عرض الحوارات ----
    val close = { dlg = null }
    when (val d = dlg) {
        null -> {}
        Dlg.Commit -> FieldsDialog(stringResource(R.string.m_commit),
            listOf(stringResource(R.string.commit_message)), listOf(commitMsg),
            onConfirm = { doCommit(it[0]); close() }, onDismiss = close)
        Dlg.Reset -> ConfirmDialog(stringResource(R.string.m_reset), stringResource(R.string.reset_confirm),
            onConfirm = { op { gm -> gm.resetHard(dir); ctx.getString(R.string.reset_ok) }; close() },
            onDismiss = close)
        Dlg.Merge -> FieldsDialog(stringResource(R.string.m_merge),
            listOf(stringResource(R.string.ref_label)), listOf("origin/main"),
            onConfirm = { v -> op { gm -> ctx.getString(R.string.result_fmt, gm.merge(dir, v[0])) }; close() },
            onDismiss = close)
        Dlg.Rebase -> FieldsDialog(stringResource(R.string.m_rebase),
            listOf(stringResource(R.string.ref_label)), listOf("origin/main"),
            onConfirm = { v -> op { gm -> ctx.getString(R.string.result_fmt, gm.rebase(dir, v[0])) }; close() },
            onDismiss = close)
        Dlg.CherryPick -> FieldsDialog(stringResource(R.string.m_cherry),
            listOf(stringResource(R.string.commit_id)), listOf(""),
            onConfirm = { v -> op { gm -> ctx.getString(R.string.result_fmt, gm.cherryPick(dir, v[0])) }; close() },
            onDismiss = close)
        Dlg.NewFile -> FieldsDialog(stringResource(R.string.m_new_file),
            listOf(stringResource(R.string.file_name)), listOf(""),
            onConfirm = { create(it[0], false); close() }, onDismiss = close)
        Dlg.NewDir -> FieldsDialog(stringResource(R.string.m_new_dir),
            listOf(stringResource(R.string.dir_name)), listOf(""),
            onConfirm = { create(it[0], true); close() }, onDismiss = close)
        Dlg.AddRemote -> FieldsDialog(stringResource(R.string.m_add_remote),
            listOf(stringResource(R.string.remote_url)), listOf(remote),
            onConfirm = { saveRemote(it[0]); close() }, onDismiss = close)
        Dlg.Delete -> ConfirmDialog(stringResource(R.string.m_delete),
            stringResource(R.string.delete_project_confirm),
            onConfirm = { close(); deleteProject() }, onDismiss = close)
        is Dlg.Raw -> FieldsDialog(stringResource(R.string.m_raw_config),
            listOf(".git/config"), listOf(d.text), multiline = true,
            onConfirm = { v ->
                op { gm -> gm.writeConfig(dir, v[0]); ctx.getString(R.string.config_saved) }
                close()
            }, onDismiss = close)
        is Dlg.Cfg -> FieldsDialog(stringResource(R.string.m_config),
            listOf(stringResource(R.string.git_name), stringResource(R.string.git_email)),
            listOf(d.name, d.email),
            onConfirm = { v ->
                op { gm -> gm.setUser(dir, v[0].trim(), v[1].trim()); ctx.getString(R.string.config_saved) }
                close()
            }, onDismiss = close)
        is Dlg.Info -> InfoDialog(d.title, d.text, close)
    }
}
