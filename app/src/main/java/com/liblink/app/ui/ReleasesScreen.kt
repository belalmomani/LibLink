package com.liblink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.liblink.app.R
import com.liblink.app.data.Project
import com.liblink.app.data.ProjectRepository
import com.liblink.app.doctor.BuildDiagnosis
import com.liblink.app.doctor.Diagnosis
import com.liblink.app.network.GitHubApi
import com.liblink.app.network.GitHubException
import com.liblink.app.storage.ProjectStorage
import com.liblink.app.storage.ReleaseStore
import com.liblink.app.storage.formatDelta
import com.liblink.app.storage.formatSize
import kotlinx.coroutines.launch
import java.io.File

// سجل الإصدارات: رقم النسخة والـ Commit وحجم APK والفرق عن السابقة ورابط التنزيل
@Composable
fun ReleasesScreen(project: Project, repo: ProjectRepository, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val store = remember { ReleaseStore(ctx) }
    val parsed = remember(project.remoteUrl) { GitHubApi.parse(project.remoteUrl) }
    val token = remember { repo.credentials().token }
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var diagnosis by remember { mutableStateOf<Diagnosis?>(null) }
    val records = remember(tick) { store.list(project.id) }

    fun describe(e: Exception): String = when {
        e is GitHubException && e.code == 404 -> ctx.getString(R.string.api_404)
        e is GitHubException && e.message == "NO_APK" -> ctx.getString(R.string.no_apk_in_zip)
        e is GitHubException -> ctx.getString(R.string.api_error, e.code, e.message ?: "")
        else -> ctx.getString(R.string.error_prefix, e.message ?: e.toString())
    }

    fun guard(): Boolean {
        if (parsed == null) { msg = ctx.getString(R.string.no_github_remote); return false }
        if (token.isBlank()) { msg = ctx.getString(R.string.no_credentials); return false }
        return true
    }

    // تحديث السجل من GitHub: آخر 30 بناء، ثم أحجام الـ artifacts للناجح منها
    fun refresh() {
        if (!guard()) return
        scope.launch {
            busy = true
            msg = try {
                val api = GitHubApi(token)
                val runs = api.runs(parsed!!.first, parsed.second, repo.workflow(project.id), 30)
                runs.forEach { store.upsertRun(project.id, it) }
                var calls = 0
                for (r in runs.filter { it.conclusion == "success" }) {
                    val rec = store.list(project.id).firstOrNull { it.runId == r.id }
                    if (rec != null && rec.sizeBytes == 0L && calls < 12) {
                        calls++
                        val art = api.artifacts(parsed.first, parsed.second, r.id).firstOrNull { !it.expired }
                        if (art != null) store.update(project.id, r.id) { it.copy(sizeBytes = art.size, artifactId = art.id) }
                    }
                }
                ""
            } catch (e: Exception) { describe(e) }
            tick++
            busy = false
        }
    }
    LaunchedEffect(Unit) { refresh() }

    fun download(runId: Long, number: Int) {
        if (!guard()) return
        scope.launch {
            busy = true
            msg = ctx.getString(R.string.downloading)
            msg = try {
                val api = GitHubApi(token)
                val art = api.artifacts(parsed!!.first, parsed.second, runId).firstOrNull { !it.expired }
                    ?: throw IllegalStateException(ctx.getString(R.string.no_artifact))
                val out = File(ProjectStorage.organizedRoot(ctx), "builds").apply { mkdirs() }
                val base = "${parsed.second}-$number"
                val f = api.downloadApk(art, File(out, "$base.zip"), File(out, "$base.apk"))
                store.update(project.id, runId) { it.copy(apkPath = f.absolutePath) }
                ctx.getString(R.string.apk_saved, f.absolutePath)
            } catch (e: Exception) { describe(e) }
            tick++
            busy = false
        }
    }

    fun why(runId: Long) {
        if (!guard()) return
        scope.launch {
            busy = true
            msg = ctx.getString(R.string.loading_log)
            msg = try {
                val d = BuildDiagnosis.analyze(ctx, GitHubApi(token).failureReport(parsed!!.first, parsed.second, runId))
                store.update(project.id, runId) { it.copy(failReason = d.cause) }
                diagnosis = d
                ""
            } catch (e: Exception) { describe(e) }
            tick++
            busy = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppBar(stringResource(R.string.releases_title), onBack, actions = {
                TextButton({ refresh() }, enabled = !busy) { Text(stringResource(R.string.refresh)) }
            })
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            Busy(busy)
            if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (records.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.rel_empty)) }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(records, key = { _, r -> r.runId }) { i, rec ->
                    // أقرب نسخة أقدم ناجحة لها حجم معروف للمقارنة
                    val prev = records.drop(i + 1).firstOrNull { it.success && it.sizeBytes > 0 }
                    val color = if (rec.success) Color(0xFF22C55E) else Color(0xFFEF4444)
                    val link = if (parsed != null && rec.artifactId > 0)
                        "https://github.com/${parsed.first}/${parsed.second}/actions/runs/${rec.runId}/artifacts/${rec.artifactId}"
                    else rec.runUrl
                    Card(Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.height(IntrinsicSize.Min)) {
                            Box(Modifier.width(6.dp).fillMaxHeight().background(color))
                            Column(Modifier.padding(14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("v${rec.versionName.removePrefix("v")}  ·  #${rec.number}",
                                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(50)) {
                                        Text(stringResource(if (rec.success) R.string.run_success else R.string.run_failed),
                                            color = color, style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                                    }
                                }
                                Text(fmtTime(rec.createdAt), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.rel_commit, rec.sha) + if (rec.message.isNotBlank()) " · " + rec.message else "",
                                    style = MaterialTheme.typography.bodySmall)

                                if (rec.success) {
                                    if (rec.sizeBytes > 0) {
                                        Text(stringResource(R.string.rel_size, formatSize(rec.sizeBytes)),
                                            style = MaterialTheme.typography.bodyMedium)
                                        if (prev != null) {
                                            val bigger = rec.sizeBytes > prev.sizeBytes
                                            Text(stringResource(R.string.rel_delta, formatDelta(rec.sizeBytes, prev.sizeBytes), prev.versionName),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (bigger) Color(0xFFF59E0B) else Color(0xFF22C55E))
                                        } else Text(stringResource(R.string.rel_first), style = MaterialTheme.typography.bodySmall)
                                    } else Text(stringResource(R.string.rel_size_unknown), style = MaterialTheme.typography.bodySmall)

                                    Text(stringResource(R.string.rel_link), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary)
                                    Text(link, style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button({ download(rec.runId, rec.number) }, enabled = !busy) {
                                            Text(stringResource(R.string.download_apk))
                                        }
                                        OutlinedButton({
                                            clipboard.setText(AnnotatedString(link)); msg = ctx.getString(R.string.link_copied)
                                        }) { Text(stringResource(R.string.copy_link)) }
                                    }
                                    if (rec.apkPath.isNotBlank() && File(rec.apkPath).exists()) {
                                        OutlinedButton({ installApk(ctx, File(rec.apkPath))?.let { msg = it } }) {
                                            Text(stringResource(R.string.install_apk))
                                        }
                                    }
                                } else {
                                    if (rec.failReason.isNotBlank()) Text(rec.failReason, style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFEF4444))
                                    OutlinedButton({ why(rec.runId) }, enabled = !busy) { Text(stringResource(R.string.view_error)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    diagnosis?.let { d ->
        DiagnosisDialog(d, onCopy = { clipboard.setText(AnnotatedString(it)); msg = ctx.getString(R.string.link_copied) },
            onDismiss = { diagnosis = null })
    }
}
