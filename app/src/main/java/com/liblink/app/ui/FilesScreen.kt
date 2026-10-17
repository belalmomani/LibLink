package com.liblink.app.ui

import androidx.activity.compose.BackHandler
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
import com.liblink.app.storage.ProjectStorage
import java.io.File

private sealed interface FDlg {
    object NewFile : FDlg
    object NewDir : FDlg
    data class Edit(val file: File, val text: String) : FDlg
    data class Delete(val file: File) : FDlg
}

// متصفح ملفات المشروع: فتح المجلدات، إنشاء ملف/مجلد، تحرير نص، حذف
@Composable
fun FilesScreen(project: Project, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val root = remember(project.path) { File(project.path) }
    var cur by remember { mutableStateOf(root) }
    var tick by remember { mutableIntStateOf(0) }
    var dlg by remember { mutableStateOf<FDlg?>(null) }
    var msg by remember { mutableStateOf("") }

    // مجلد .git مخفي لتجنب العبث به، المجلدات أولاً ثم الملفات
    val entries = remember(cur, tick) {
        (cur.listFiles()?.filter { it.name != ".git" } ?: emptyList())
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun up() { if (cur != root) cur = cur.parentFile ?: root else onBack() }
    BackHandler(enabled = cur != root) { up() }

    fun create(name: String, dir: Boolean) {
        val f = ProjectStorage.resolveInside(cur, name)
        if (f == null) { msg = ctx.getString(R.string.invalid_name); return }
        try {
            if (dir) f.mkdirs() else { f.parentFile?.mkdirs(); f.createNewFile() }
            msg = ctx.getString(R.string.created); tick++
        } catch (e: Exception) {
            msg = ctx.getString(R.string.error_prefix, e.message ?: e.toString())
        }
    }

    fun open(f: File) {
        if (f.isDirectory) { cur = f; return }
        try {
            val bytes = f.readBytes()
            if (bytes.size > 200_000 || bytes.contains(0)) msg = ctx.getString(R.string.unreadable)
            else dlg = FDlg.Edit(f, String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            msg = ctx.getString(R.string.error_prefix, e.message ?: e.toString())
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppBar(if (cur == root) project.name else cur.name, onBack = { up() }, actions = {
                TextButton({ dlg = FDlg.NewFile }) { Text(stringResource(R.string.m_new_file)) }
                TextButton({ dlg = FDlg.NewDir }) { Text(stringResource(R.string.m_new_dir)) }
            })
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.empty_folder))
                }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 8.dp)) {
                items(entries, key = { it.absolutePath }) { f ->
                    Card(Modifier.fillMaxWidth().clickable { open(f) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text((if (f.isDirectory) "\uD83D\uDCC1  " else "\uD83D\uDCC4  ") + f.name,
                                Modifier.weight(1f))
                            TextButton({ dlg = FDlg.Delete(f) }) {
                                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    when (val d = dlg) {
        null -> {}
        FDlg.NewFile -> FieldsDialog(stringResource(R.string.m_new_file),
            listOf(stringResource(R.string.file_name)), listOf(""),
            onConfirm = { create(it[0], false); dlg = null }, onDismiss = { dlg = null })
        FDlg.NewDir -> FieldsDialog(stringResource(R.string.m_new_dir),
            listOf(stringResource(R.string.dir_name)), listOf(""),
            onConfirm = { create(it[0], true); dlg = null }, onDismiss = { dlg = null })
        is FDlg.Edit -> FieldsDialog(d.file.name, listOf(d.file.name), listOf(d.text), multiline = true,
            onConfirm = {
                try { d.file.writeText(it[0]); msg = ctx.getString(R.string.saved) }
                catch (e: Exception) { msg = ctx.getString(R.string.error_prefix, e.message ?: e.toString()) }
                dlg = null
            }, onDismiss = { dlg = null })
        is FDlg.Delete -> ConfirmDialog(stringResource(R.string.delete),
            stringResource(R.string.delete_confirm, d.file.name),
            onConfirm = {
                d.file.deleteRecursively(); msg = ctx.getString(R.string.deleted); tick++; dlg = null
            }, onDismiss = { dlg = null })
    }
}
