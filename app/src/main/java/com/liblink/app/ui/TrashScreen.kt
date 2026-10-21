package com.liblink.app.ui

import android.content.ClipData
import android.content.Intent
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
import androidx.core.content.FileProvider
import com.liblink.app.R
import com.liblink.app.data.ProjectRepository
import com.liblink.app.storage.ProjectStorage
import com.liblink.app.storage.Trash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// سلة المحذوفات: استعادة في مكانها، أو استعادة كملف ZIP بمجلداتها، أو حذف نهائي.
// ما مضى عليه 3 أيام يُحذف تلقائيًا
@Composable
fun TrashScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }
    var confirmEmpty by remember { mutableStateOf(false) }
    var zipFile by remember { mutableStateOf<File?>(null) }
    val entries = remember(tick) { Trash.purge(ctx); Trash.list(ctx) }

    // إنشاء ZIP بالعناصر المحددة مع مجلداتها الأصلية
    fun makeZip(list: List<Trash.Entry>) {
        scope.launch {
            msg = try {
                val projects = ProjectRepository(ctx).projects()
                val dir = File(ProjectStorage.organizedRoot(ctx), "restored").apply { mkdirs() }
                val name = "liblink-restore-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".zip"
                val out = File(dir, name)
                val n = withContext(Dispatchers.IO) { Trash.exportZip(list, projects, out) }
                zipFile = out
                ctx.getString(R.string.trash_zip_done, n, out.absolutePath)
            } catch (e: Exception) {
                ctx.getString(R.string.error_prefix, e.message ?: e.toString())
            }
        }
    }

    fun share(f: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("", uri)
        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppBar(stringResource(R.string.trash_title), onBack, actions = {
                if (entries.isNotEmpty()) {
                    TextButton({ makeZip(entries) }) { Text(stringResource(R.string.trash_zip_all)) }
                    TextButton({ confirmEmpty = true }) { Text(stringResource(R.string.trash_empty_btn)) }
                }
            })
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.trash_note), style = MaterialTheme.typography.bodySmall)
            if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
            zipFile?.let { f ->
                if (f.exists()) {
                    Text(stringResource(R.string.trash_zip_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton({ share(f) }) { Text(stringResource(R.string.trash_share)) }
                }
            }
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.trash_empty))
                }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(entries, key = { it.dir.name }) { e ->
                    Card(Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text((if (e.item.isDirectory) "\uD83D\uDCC1  " else "\uD83D\uDCC4  ") + e.orig.name,
                                style = MaterialTheme.typography.titleSmall)
                            Text(e.orig.parent ?: "", style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(if (e.reason == "replaced") R.string.trash_reason_replaced
                            else R.string.trash_reason_deleted) + " · " +
                                stringResource(R.string.trash_days_left, Trash.daysLeft(e)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button({
                                    scope.launch {
                                        msg = try {
                                            val f = withContext(Dispatchers.IO) { Trash.restore(e) }
                                            ctx.getString(R.string.trash_restored, f.absolutePath)
                                        } catch (ex: Exception) {
                                            ctx.getString(R.string.error_prefix, ex.message ?: ex.toString())
                                        }
                                        tick++
                                    }
                                }) { Text(stringResource(R.string.trash_restore)) }
                                OutlinedButton({ makeZip(listOf(e)) }) { Text(stringResource(R.string.trash_zip_one)) }
                                OutlinedButton({ Trash.delete(e); tick++ }) {
                                    Text(stringResource(R.string.trash_delete_now), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty) ConfirmDialog(stringResource(R.string.trash_empty_btn), stringResource(R.string.trash_empty_confirm),
        onConfirm = { Trash.emptyAll(ctx); confirmEmpty = false; tick++ }, onDismiss = { confirmEmpty = false })
}
