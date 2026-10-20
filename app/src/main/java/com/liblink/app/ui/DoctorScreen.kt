package com.liblink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liblink.app.R
import com.liblink.app.data.Project
import com.liblink.app.data.ProjectRepository
import com.liblink.app.doctor.Doctor
import com.liblink.app.doctor.Finding
import com.liblink.app.doctor.Fix
import com.liblink.app.doctor.Severity
import kotlinx.coroutines.launch

private fun colorOf(s: Severity) = when (s) {
    Severity.ERROR -> Color(0xFFEF4444)
    Severity.WARN -> Color(0xFFF59E0B)
    Severity.INFO -> Color(0xFF60A5FA)
    Severity.OK -> Color(0xFF22C55E)
}

// مركز تشخيص المشروع: تشخيص مفهوم + خطة إنقاذ + معاينة ما سيتغير قبل أي إصلاح
@Composable
fun DoctorScreen(project: Project, repo: ProjectRepository, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var findings by remember { mutableStateOf<List<Finding>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    var msg by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Fix?>(null) }

    LaunchedEffect(tick) {
        busy = true
        val p = repo.find(project.id) ?: project
        findings = try {
            Doctor.diagnose(ctx, p, repo.credentials(), "${p.name} ${repo.versionLabel(p.id)}")
        } catch (e: Exception) {
            listOf(Finding(Severity.ERROR, ctx.getString(R.string.error_prefix, e.message ?: e.toString()), ""))
        }
        busy = false
    }

    fun apply(fix: Fix) {
        scope.launch {
            busy = true
            msg = try {
                Doctor.apply(ctx, repo.find(project.id) ?: project, repo, fix)
            } catch (e: Exception) {
                ctx.getString(R.string.error_prefix, e.message ?: e.toString())
            }
            tick++ // إعادة الفحص بعد التنفيذ
        }
    }

    val problems = findings.count { it.severity == Severity.ERROR || it.severity == Severity.WARN }
    val steps = findings.filter { it.fix != null && (it.severity == Severity.ERROR || it.severity == Severity.WARN) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppBar(stringResource(R.string.doc_title), onBack, actions = {
                TextButton({ tick++ }, enabled = !busy) { Text(stringResource(R.string.doc_recheck)) }
            })
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (busy) {
                Busy(true)
                Text(stringResource(R.string.doc_checking), style = MaterialTheme.typography.bodySmall)
            }
            if (msg.isNotBlank()) Text(msg, color = MaterialTheme.colorScheme.primary)

            if (!busy) {
                // الملخص
                val sumColor = if (problems == 0) colorOf(Severity.OK) else colorOf(Severity.WARN)
                Surface(color = sumColor.copy(alpha = 0.18f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                    Text(
                        if (problems == 0) stringResource(R.string.doc_summary_ok) else stringResource(R.string.doc_summary_bad, problems),
                        color = sumColor, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().padding(14.dp)
                    )
                }

                // خطة الإنقاذ بالترتيب
                if (steps.isNotEmpty()) {
                    StepCard(stringResource(R.string.doc_plan_title)) {
                        steps.forEachIndexed { i, f ->
                            Text(stringResource(R.string.doc_plan_step, i + 1, f.fix!!.label),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                findings.forEach { f ->
                    val color = colorOf(f.severity)
                    Card(Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.height(IntrinsicSize.Min)) {
                            Box(Modifier.width(6.dp).fillMaxHeight().background(color))
                            Column(Modifier.padding(14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(f.title, style = MaterialTheme.typography.titleSmall, color = color)
                                if (f.detail.isNotBlank()) Text(f.detail, style = MaterialTheme.typography.bodySmall)
                                f.fix?.let { fix ->
                                    Button({ pending = fix }, enabled = !busy) { Text(stringResource(R.string.doc_preview_btn)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // معاينة ما سيتغير قبل التنفيذ
    pending?.let { fix ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(fix.label) },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.doc_preview_title), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(fix.preview.joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton({ pending = null; apply(fix) }) { Text(stringResource(R.string.doc_apply)) } },
            dismissButton = { TextButton({ pending = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
