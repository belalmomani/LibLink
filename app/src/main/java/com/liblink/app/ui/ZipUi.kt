package com.liblink.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import com.liblink.app.R
import com.liblink.app.storage.OpJournal
import com.liblink.app.storage.ReviewVerdict
import com.liblink.app.storage.ZipPlan
import com.liblink.app.storage.ZipUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ZipUi(val pickZip: () -> Unit)

// تحديث المشروع من ملف ZIP: مراجعة الإصدار (هل الخطأ في الإصدار أم في التطبيق؟) ثم تأكيد الاستبدال
@Composable
fun rememberZipUi(target: File, projectPath: String, onDone: (String) -> Unit): ZipUi {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val targetNow = rememberUpdatedState(target)
    val doneNow = rememberUpdatedState(onDone)
    var zipFile by remember { mutableStateOf<File?>(null) }
    var plan by remember { mutableStateOf<ZipPlan?>(null) }
    var strip by remember { mutableStateOf(true) }

    fun fail(e: Exception) {
        zipFile?.delete(); zipFile = null; plan = null
        doneNow.value(if (e is java.util.zip.ZipException) ctx.getString(R.string.zip_bad)
        else ctx.getString(R.string.error_prefix, e.message ?: e.toString()))
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val f = withContext(Dispatchers.IO) { ZipUpdate.copyToCache(ctx, uri) }
                zipFile = f; strip = true
                plan = withContext(Dispatchers.IO) { ZipUpdate.plan(ctx, targetNow.value, f, true) }
            } catch (e: Exception) { fail(e) }
        }
    }

    val p = plan
    val z = zipFile
    if (p != null && z != null) {
        val changes = p.added.size + p.modified.size
        val rv = p.review
        val risky = rv.verdict == ReviewVerdict.OTHER_APP || rv.verdict == ReviewVerdict.OLDER
        val color = when (rv.verdict) {
            ReviewVerdict.OK -> Color(0xFF22C55E)
            ReviewVerdict.BIG -> Color(0xFFF59E0B)
            ReviewVerdict.OLDER -> Color(0xFFF59E0B)
            ReviewVerdict.OTHER_APP -> Color(0xFFEF4444)
        }
        val title = when (rv.verdict) {
            ReviewVerdict.OK -> R.string.rv_title_ok
            ReviewVerdict.BIG -> R.string.rv_title_big
            ReviewVerdict.OLDER -> R.string.rv_title_old
            ReviewVerdict.OTHER_APP -> R.string.rv_title_app
        }
        val advice = when (rv.verdict) {
            ReviewVerdict.OK -> R.string.rv_adv_ok
            ReviewVerdict.BIG -> R.string.rv_adv_big
            ReviewVerdict.OLDER -> R.string.rv_adv_old
            ReviewVerdict.OTHER_APP -> R.string.rv_adv_app
        }
        AlertDialog(
            onDismissRequest = { z.delete(); zipFile = null; plan = null },
            title = { Text(stringResource(R.string.zip_title)) },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // الحكم: هل يشبه هذا الـ ZIP مشروعك؟
                    Surface(color = color.copy(alpha = 0.18f), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(title), color = color, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(advice), style = MaterialTheme.typography.bodySmall)
                            rv.lines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    Text(stringResource(R.string.zip_summary, p.added.size, p.modified.size, p.same))
                    if (p.topFolder != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.zip_strip, p.topFolder), Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall)
                            Switch(strip, { v ->
                                strip = v
                                scope.launch {
                                    try { plan = withContext(Dispatchers.IO) { ZipUpdate.plan(ctx, targetNow.value, z, v) } }
                                    catch (e: Exception) { fail(e) }
                                }
                            })
                        }
                    }
                    if (changes == 0) Text(stringResource(R.string.zip_none), color = MaterialTheme.colorScheme.primary)
                    if (p.added.isNotEmpty()) {
                        Text(stringResource(R.string.zip_new), style = MaterialTheme.typography.titleSmall)
                        Text(p.added.take(8).joinToString("\n") + if (p.added.size > 8) "\n…" else "",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (p.modified.isNotEmpty()) {
                        Text(stringResource(R.string.zip_modified), style = MaterialTheme.typography.titleSmall)
                        Text(p.modified.take(8).joinToString("\n") + if (p.modified.size > 8) "\n…" else "",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text(stringResource(R.string.undo_note_short), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                TextButton(enabled = changes > 0, onClick = {
                    zipFile = null; plan = null
                    scope.launch {
                        val text = try {
                            val r = withContext(Dispatchers.IO) { ZipUpdate.apply(ctx, targetNow.value, z, p.strip) }
                            // تسجيل العملية للتراجع عنها لاحقًا
                            OpJournal.record(ctx, projectPath, ctx.getString(R.string.op_zip, r.added + r.replaced), r.actions)
                            ctx.getString(R.string.zip_done, r.added, r.replaced)
                        } catch (e: Exception) { ctx.getString(R.string.error_prefix, e.message ?: e.toString()) }
                        doneNow.value(text)
                    }
                }) { Text(stringResource(if (risky) R.string.zip_apply_anyway else R.string.zip_apply)) }
            },
            dismissButton = {
                TextButton({ z.delete(); zipFile = null; plan = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    return ZipUi(pickZip = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) })
}
