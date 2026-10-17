package com.liblink.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.liblink.app.R
import com.liblink.app.data.Project
import com.liblink.app.data.ProjectRepository
import com.liblink.app.git.GitManager
import com.liblink.app.network.GitHubApi
import com.liblink.app.network.GitHubException
import com.liblink.app.storage.ProjectStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// قالب workflow جاهز لبناء APK (يُنشأ داخل المشروع ثم يُرفع إلى GitHub)
private val WORKFLOW_TEMPLATE = """name: Build APK
on:
  push:
    branches: [ main ]
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: 8.7
      - name: Build debug APK
        run: gradle :app:assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/*.apk
"""

private fun fmtTime(iso: String): String = runCatching {
    ZonedDateTime.ofInstant(Instant.parse(iso), ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}.getOrDefault(iso)

// تثبيت APK عبر FileProvider بعد التأكد من إذن التثبيت من مصادر غير معروفة
private fun installApk(ctx: Context, apk: File): String? {
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
fun BuildScreen(project: Project, repo: ProjectRepository, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val dir = remember(project.path) { File(project.path) }
    val parsed = remember(project.remoteUrl) { GitHubApi.parse(project.remoteUrl) }
    val token = remember { repo.credentials().token }

    var workflow by remember { mutableStateOf(repo.workflow(project.id)) }
    var branch by remember { mutableStateOf("") }
    var runs by remember { mutableStateOf<List<GitHubApi.Run>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var apk by remember { mutableStateOf<File?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var boost by remember { mutableIntStateOf(0) } // مرات إضافية للفحص بعد التشغيل حتى يظهر التنفيذ الجديد
    val wfExists = remember(tick, workflow) { File(dir, ".github/workflows/$workflow").exists() }

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
            try { runs = GitHubApi(token).runs(parsed.first, parsed.second, workflow) }
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
        scope.launch {
            busy = true
            msg = try {
                GitHubApi(token).dispatch(parsed!!.first, parsed.second, workflow.trim(), branch.ifBlank { "main" })
                repo.setWorkflow(project.id, workflow.trim())
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
            if (!f.exists()) f.writeText(WORKFLOW_TEMPLATE)
            repo.setWorkflow(project.id, workflow.trim())
            msg = ctx.getString(R.string.workflow_created); tick++
        } catch (e: Exception) { msg = describe(e) }
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

            OutlinedTextField(workflow, { workflow = it }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.workflow_file)) }, singleLine = true)

            if (!wfExists) {
                StepCard(stringResource(R.string.create_workflow), stringResource(R.string.workflow_missing)) {
                    OutlinedButton({ createWorkflow() }) { Text(stringResource(R.string.create_workflow)) }
                }
            }

            Button(onClick = { runBuild() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
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
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (r.conclusion == "success") {
                                    Button({ download(r) }, enabled = !busy) { Text(stringResource(R.string.download_apk)) }
                                }
                                OutlinedButton({
                                    clipboard.setText(AnnotatedString(r.url)); msg = ctx.getString(R.string.link_copied)
                                }) { Text(stringResource(R.string.copy_link)) }
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
}
