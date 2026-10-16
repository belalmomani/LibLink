package com.liblink.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

@Composable
fun ProjectScreen(project: Project, repo: ProjectRepository, onBack: () -> Unit, onRemoved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dir = remember(project.path) { File(project.path) }
    var remote by remember { mutableStateOf(project.remoteUrl) }
    var commitMsg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }

    // تشغيل عملية Git مع عرض النتيجة أو الخطأ
    fun op(block: suspend (GitManager) -> String) {
        scope.launch {
            busy = true
            log = try {
                block(GitManager(repo.credentials()))
            } catch (e: Exception) {
                ctx.getString(R.string.error_prefix, e.message ?: e.toString())
            }
            busy = false
        }
    }

    suspend fun statusText(gm: GitManager): String {
        val s = gm.status(dir)
        return if (s.total == 0) ctx.getString(R.string.status_clean)
        else ctx.getString(R.string.status_changes, s.total, s.added, s.modified, s.deleted)
    }

    // الخطوة 1: نسخ ملفات من الهاتف إلى مجلد المشروع
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) op { gm ->
            val n = withContext(Dispatchers.IO) { ProjectStorage.copyInto(ctx, dir, uris) }
            ctx.getString(R.string.files_copied, n) + "\n" + statusText(gm)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppBar(project.name, onBack) },
        bottomBar = { AdBanner(Modifier.fillMaxWidth().navigationBarsPadding()) }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.path_label, project.path), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.steps_title), style = MaterialTheme.typography.titleLarge)

            StepCard(stringResource(R.string.step1_title), stringResource(R.string.step1_desc)) {
                Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy) {
                    Text(stringResource(R.string.step1_btn))
                }
            }

            StepCard(stringResource(R.string.step2_title), stringResource(R.string.step2_desc)) {
                Button(onClick = {
                    op { gm -> gm.stageAll(dir); ctx.getString(R.string.staged_ok) + "\n" + statusText(gm) }
                }, enabled = !busy) { Text(stringResource(R.string.step2_btn)) }
            }

            StepCard(stringResource(R.string.step3_title)) {
                OutlinedTextField(commitMsg, { commitMsg = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.commit_message)) })
                Button(onClick = {
                    if (commitMsg.isBlank()) log = ctx.getString(R.string.commit_empty)
                    else op { gm ->
                        val id = gm.commit(dir, commitMsg.trim())
                        commitMsg = ""
                        ctx.getString(R.string.commit_ok, id)
                    }
                }, enabled = !busy) { Text(stringResource(R.string.step3_btn)) }
            }

            StepCard(stringResource(R.string.step4_title), stringResource(R.string.step4_desc)) {
                OutlinedTextField(remote, { remote = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.remote_url)) })
                Button(onClick = {
                    val creds = repo.credentials()
                    when {
                        remote.isBlank() -> log = ctx.getString(R.string.no_remote)
                        creds.token.isBlank() -> log = ctx.getString(R.string.no_credentials)
                        else -> op { gm ->
                            gm.setRemote(dir, remote.trim())
                            repo.update(project.copy(remoteUrl = remote.trim()))
                            gm.push(dir)
                            ctx.getString(R.string.push_ok)
                        }
                    }
                }, enabled = !busy) { Text(stringResource(R.string.step4_btn)) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { op { gm -> statusText(gm) } }, enabled = !busy) {
                    Text(stringResource(R.string.status_btn))
                }
                OutlinedButton(onClick = {
                    if (repo.credentials().token.isBlank()) log = ctx.getString(R.string.no_credentials)
                    else op { gm -> gm.pull(dir); ctx.getString(R.string.pull_ok) }
                }, enabled = !busy) { Text(stringResource(R.string.pull_btn)) }
            }

            Busy(busy)
            if (log.isNotBlank()) Text(log)

            TextButton(onClick = { repo.remove(project.id); onRemoved() }) {
                Text(stringResource(R.string.remove_project), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
