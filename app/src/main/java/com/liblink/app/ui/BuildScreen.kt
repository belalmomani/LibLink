package com.liblink.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.liblink.app.R
import com.liblink.app.data.Project
import com.liblink.app.data.ProjectRepository
import com.liblink.app.git.GitManager
import com.liblink.app.network.GitHubApi
import com.liblink.app.network.GitHubException
import com.liblink.app.doctor.BuildDiagnosis
import com.liblink.app.doctor.Diagnosis
import com.liblink.app.storage.ProjectStorage
import com.liblink.app.storage.ReleaseStore
import com.liblink.app.storage.Templates
import com.liblink.app.storage.WorkflowInfo
import com.liblink.app.storage.WorkflowScan
import com.liblink.app.work.BuildWatchWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter


internal fun fmtTime(iso: String): String = runCatching {
    ZonedDateTime.ofInstant(Instant.parse(iso), ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}.getOrDefault(iso)

// تثبيت APK عبر FileProvider بعد التأكد من إذن التثبيت من مصادر غير معروفة
internal fun installApk(ctx: Context, apk: File): String? {
    if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return ctx.getString(R.string.allow_install)
    }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
    ctx.startActivity(
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    return null
}

@Composable
fun BuildScreen(project: Project, repo: ProjectRepository, onBack: () -> Unit, onReleases: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val dir = remember(project.path) { File(project.path) }
    val parsed = remember(project.remoteUrl) { GitHubApi.parse(project.remoteUrl) }
    val token = remember { repo.credentials().token }
    val store = remember { ReleaseStore(ctx) } // سجل الإصدارات

    var workflow by remember { mutableStateOf(repo.workflow(project.id)) }
    var branch by remember { mutableStateOf("") }
    var runs by remember { mutableStateOf<List<GitHubApi.Run>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var apk by remember { mutableStateOf<File?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var diagnosis by remember { mutableStateOf<Diagnosis?>(null) }
    // إذن الإشعارات (أندرويد 13+) لإشعار انتهاء البناء
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var boost by remember { mutableIntStateOf(0) } // مرات إضافية للفحص بعد التشغيل حتى يظهر التنفيذ الجديد
    val wfExists = remember(tick, workflow) { File(dir, ".github/workflows/$workflow").exists() }
    // ملفات workflows الموجودة في المشروع مع محتوى كل ملف
    val workflows = remember(tick) { WorkflowScan.scan(dir) }
    val current = workflows.firstOrNull { it.file == workflow }
    val canRun = current?.manual != false
    var pickOpen by remember { mutableStateOf(false) }
    var typeOther by remember { mutableStateOf(false) }
    var showContent by remember { mutableStateOf(false) }
    // إن لم يوجد الملف المحفوظ نختار تلقائيًا أنسب ملف: قابل للتشغيل اليدوي ويبني APK
    LaunchedEffect(workflows) {
        if (workflows.isNotEmpty() && workflows.none { it.file == workflow }) {
            workflow = (workflows.firstOrNull { it.manual && it.builds } ?: workflows.first()).file
        }
    }

    fun describe(e: Exception): String = when {
        e is GitHubException && e.code == 404 -> ctx.getString(R.string.api_404)
        e is GitHubException && e.message == "NO_APK" -> ctx.getString(R.string.no_apk_in_zip)
        e is GitHubException -> ctx.getString(R.string.api_error, e.code, e.message ?: "")
        else -> ctx.getString(R.string.error_prefix, e.message ?: e.toString())
    }

    LaunchedEffect(Unit) { branch = runCatching { GitManager().currentBranch(dir) }.getOrDefault("") }

    // فحص حالة البناء، ومتابعتها تلقائيًا ما دام قيد التنفيذ
    LaunchedEffect(tick, workflow) {
        if (parsed == null || token.isBlank()) return@LaunchedEffect
        var extra = boost
        boost = 0
        while (true) {
            try {
                runs = GitHubApi(token).runs(parsed.first, parsed.second, workflow)
                runs.forEach { store.upsertRun(project.id, it) }
            }
            catch (e: Exception) { msg = describe(e); break }
            val active = runs.firstOrNull()?.let { it.status != "completed" } == true
            if (!active && extra <= 0) break
            extra--
            delay(6000)
        }
    }

    fun guard(): Boolean {
        if (parsed == null) { msg = ctx.getString(R.string.no_github_remote); return false }
        if (token.isBlank()) { msg = ctx.getString(R.string.no_credentials); return false }
        return true
    }

    fun runBuild() {
        if (!guard()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        scope.launch {
            busy = true
            msg = try {
                val startedAt = System.currentTimeMillis()
                GitHubApi(token).dispatch(parsed!!.first, parsed.second, workflow.trim(), branch.ifBlank { "main" })
                repo.setWorkflow(project.id, workflow.trim())
                // مراقبة في الخلفية: يصلك إشعار عند انتهاء البناء
                BuildWatchWorker.enqueue(ctx, project.id, project.name, parsed.first, parsed.second, workflow.trim(), startedAt)
                boost = 10; tick++
                ctx.getString(R.string.build_started)
            } catch (e: Exception) { describe(e) }
            busy = false
        }
    }

    fun createWorkflow() {
        try {
            val f = File(dir, ".github/workflows/${workflow.trim()}")
            f.parentFile?.mkdirs()
            if (!f.exists()) f.writeText(Templates.WORKFLOW)
            repo.setWorkflow(project.id, workflow.trim())
            msg = ctx.getString(R.string.workflow_created); tick++
        } catch (e: Exception) { msg = describe(e) }
    }

    // لماذا فشل البناء؟ المرحلة والسبب المحتمل والإجراء المقترح
    fun viewError(run: GitHubApi.Run) {
        if (!guard()) return
        scope.launch {
            busy = true
            msg = ctx.getString(R.string.loading_log)
            msg = try {
                val d = BuildDiagnosis.analyze(ctx, GitHubApi(token).failureReport(parsed!!.first, parsed.second, run.id))
                store.upsertRun(project.id, run)
                store.update(project.id, run.id) { it.copy(failReason = d.cause) }
                diagnosis = d
                ""
            } catch (e: Exception) { describe(e) }
            busy = false
        }
    }

    fun download(run: GitHubApi.Run) {
        if (!guard()) return
        scope.launch {
            busy = true
            msg = ctx.getString(R.string.downloading)
            msg = try {
                val api = GitHubApi(token)
                val art = api.artifacts(parsed!!.first, parsed.second, run.id).firstOrNull { !it.expired }
                    ?: throw IllegalStateException(ctx.getString(R.string.no_artifact))
                val out = File(ProjectStorage.organizedRoot(ctx), "builds").apply { mkdirs() }
                val base = "${parsed.second}-${run.number}"
                val f = api.downloadApk(art, File(out, "$base.zip"), File(out, "$base.apk"))
                apk = f
                ctx.getString(R.string.apk_saved, f.absolutePath)
            } catch (e: Exception) { describe(e) }
            busy = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppBar(stringResource(R.string.build_title), onBack, actions = {
                TextButton(onReleases) { Text(stringResource(R.string.releases_title)) }
                TextButton({ tick++ }) { Text(stringResource(R.string.refresh)) }
            })
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                (parsed?.let { "${it.first}/${it.second}" } ?: stringResource(R.string.no_github_remote)) +
                    if (branch.isNotBlank()) "   \u2442 $branch" else "",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary
            )
            Text(stringResource(R.string.workflow_hint), style = MaterialTheme.typography.bodySmall)

            if (workflows.isEmpty()) {
                OutlinedTextField(workflow, { workflow = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.workflow_file)) }, singleLine = true)
            } else {
                Text(stringResource(R.string.workflow_found, workflows.size), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                // اختيار الملف من القائمة: كل ملف مقترن بملخص محتواه
                Box {
                    OutlinedButton({ pickOpen = true }, Modifier.fillMaxWidth()) { Text("$workflow  \u25BE") }
                    DropdownMenu(pickOpen, { pickOpen = false }) {
                        workflows.forEach { w ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(w.file, style = MaterialTheme.typography.titleSmall)
                                        Text(workflowSummary(w), style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = { workflow = w.file; repo.setWorkflow(project.id, w.file); pickOpen = false }
                            )
                        }
                        DropdownMenuItem({ Text(stringResource(R.string.workflow_other)) }, { pickOpen = false; typeOther = true })
                    }
                }
                val w = current
                if (w == null) Text(stringResource(R.string.wf_not_local), style = MaterialTheme.typography.bodySmall)
                else StepCard(w.file, w.name.ifBlank { "—" }) {
                    Text(workflowSummary(w), style = MaterialTheme.typography.bodySmall)
                    if (!w.manual) Text(stringResource(R.string.workflow_no_manual), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                    OutlinedButton({ showContent = true }) { Text(stringResource(R.string.wf_view)) }
                }
            }

            if (!wfExists) {
                StepCard(stringResource(R.string.create_workflow), stringResource(R.string.workflow_missing)) {
                    OutlinedButton({ createWorkflow() }) { Text(stringResource(R.string.create_workflow)) }
                }
            }

            Button(onClick = { runBuild() }, enabled = !busy && canRun, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.run_build))
            }
            Busy(busy)
            if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall)

            apk?.let { f ->
                StepCard(stringResource(R.string.download_apk), f.absolutePath) {
                    Button({ installApk(ctx, f)?.let { msg = it } }) { Text(stringResource(R.string.install_apk)) }
                }
            }

            Text(stringResource(R.string.recent_builds), style = MaterialTheme.typography.titleMedium)
            if (runs.isEmpty()) Text(stringResource(R.string.no_runs), style = MaterialTheme.typography.bodySmall)

            runs.forEach { r ->
                val (color, label) = when {
                    r.status != "completed" ->
                        if (r.status == "in_progress") Color(0xFFF59E0B) to R.string.run_running
                        else Color(0xFF60A5FA) to R.string.run_queued
                    r.conclusion == "success" -> Color(0xFF22C55E) to R.string.run_success
                    r.conclusion == "cancelled" -> Color(0xFF94A3B8) to R.string.run_cancelled
                    else -> Color(0xFFEF4444) to R.string.run_failed
                }
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(6.dp).fillMaxHeight().background(color))
                        Column(Modifier.padding(14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.run_label, r.number, r.branch),
                                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(50)) {
                                    Text(stringResource(label), color = color, style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                                }
                            }
                            Text(fmtTime(r.createdAt), style = MaterialTheme.typography.bodySmall)
                            if (r.sha.isNotBlank()) Text(stringResource(R.string.rel_commit, r.sha.take(7)) +
                                if (r.message.isNotBlank()) " · " + r.message else "", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (r.conclusion == "success") {
                                    Button({ download(r) }, enabled = !busy) { Text(stringResource(R.string.download_apk)) }
                                }
                                OutlinedButton({
                                    clipboard.setText(AnnotatedString(r.url)); msg = ctx.getString(R.string.link_copied)
                                }) { Text(stringResource(R.string.copy_link)) }
                            }
                            if (r.status == "completed" && r.conclusion == "failure") {
                                OutlinedButton({ viewError(r) }, enabled = !busy) { Text(stringResource(R.string.view_error)) }
                            }
                            TextButton({
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }) { Text(stringResource(R.string.open_browser)) }
                        }
                    }
                }
            }
        }
    }

    if (showContent) InfoDialog(workflow, current?.content.orEmpty()) { showContent = false }
    if (typeOther) {
        FieldsDialog(stringResource(R.string.workflow_other), listOf(stringResource(R.string.workflow_file)), listOf(workflow),
            onConfirm = { v -> if (v[0].isNotBlank()) workflow = v[0].trim(); typeOther = false },
            onDismiss = { typeOther = false })
    }

    diagnosis?.let { d ->
        DiagnosisDialog(d, onCopy = { clipboard.setText(AnnotatedString(it)); msg = ctx.getString(R.string.link_copied) },
            onDismiss = { diagnosis = null })
    }
}

// ملخص محتوى ملف workflow: تشغيل يدوي؟ يبني APK؟ يعمل عند Push؟
@Composable
private fun workflowSummary(w: WorkflowInfo): String = listOf(
    stringResource(if (w.manual) R.string.wf_manual_yes else R.string.wf_manual_no),
    stringResource(if (w.builds) R.string.wf_builds_yes else R.string.wf_builds_no),
    if (w.onPush) stringResource(R.string.wf_push) else ""
).filter { it.isNotEmpty() }.joinToString(" · ")
