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
import com.liblink.app.git.GitManager
import com.liblink.app.storage.ProjectStorage
import com.liblink.app.storage.Trash
import java.io.File

private sealed interface FDlg {
    object NewFile : FDlg
    object NewDir : FDlg
    data class Rename(val file: File) : FDlg
    data class Delete(val file: File) : FDlg
}

// متصفح ملفات المشروع: فتح، محرر، نسخ، إعادة تسمية، حذف (إلى السلة)، استيراد ملفات/مجلدات
@Composable
fun FilesScreen(
    project: Project,
    startDir: String?,
    onBack: () -> Unit,
    onOpenFile: (File) -> Unit,
    onBranches: () -> Unit,
    onTrash: () -> Unit
) {
    val ctx = LocalContext.current
    val root = remember(project.path) { File(project.path) }
    var cur by remember { mutableStateOf(startDir?.let { File(it) }?.takeIf { it.isDirectory } ?: root) }
    var tick by remember { mutableIntStateOf(0) }
    var dlg by remember { mutableStateOf<FDlg?>(null) }
    var msg by remember { mutableStateOf("") }
    var addMenu by remember { mutableStateOf(false) }
    var branch by remember { mutableStateOf("") }

    LaunchedEffect(tick) { branch = runCatching { GitManager().currentBranch(root) }.getOrDefault("") }

    // مجلد .git مخفي لتجنب العبث به، المجلدات أولاً ثم الملفات
    val entries = remember(cur, tick) {
        (cur.listFiles()?.filter { it.name != ".git" } ?: emptyList())
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun up() { if (cur != root) cur = cur.parentFile ?: root else onBack() }
    BackHandler(enabled = cur != root) { up() }

    fun err(e: Exception) { msg = ctx.getString(R.string.error_prefix, e.message ?: e.toString()) }

    fun create(name: String, dir: Boolean) {
        val f = ProjectStorage.resolveInside(cur, name)
        if (f == null) { msg = ctx.getString(R.string.invalid_name); return }
        if (f.exists()) { msg = ctx.getString(R.string.exists); return }
        try {
            if (dir) f.mkdirs() else { f.parentFile?.mkdirs(); f.createNewFile() }
            msg = ctx.getString(R.string.created); tick++
        } catch (e: Exception) { err(e) }
    }

    fun rename(f: File, name: String) {
        if (name.isBlank() || name.contains('/') || name.contains('\\')) { msg = ctx.getString(R.string.invalid_name); return }
        val t = File(f.parentFile, name.trim())
        if (t.exists()) { msg = ctx.getString(R.string.exists); return }
        if (f.renameTo(t)) { msg = ctx.getString(R.string.renamed); tick++ }
        else msg = ctx.getString(R.string.invalid_name)
    }

    // نسخ داخل نفس المجلد باسم جديد: name(1).ext
    fun copy(f: File) {
        try {
            val t = ProjectStorage.uniqueFile(f.parentFile ?: cur, f.name)
            f.copyRecursively(t)
            msg = ctx.getString(R.string.copied) + ": " + t.name; tick++
        } catch (e: Exception) { err(e) }
    }

    fun open(f: File) {
        if (f.isDirectory) { cur = f; return }
        try {
            val bytes = f.readBytes()
            if (bytes.size > 500_000 || bytes.contains(0)) msg = ctx.getString(R.string.unreadable)
            else onOpenFile(f)
        } catch (e: Exception) { err(e) }
    }

    // استيراد ملفات أو مجلد إلى المجلد الحالي مع خيار الاستبدال
    val importer = rememberImportUi(cur) { m -> msg = m; tick++ }
    val zipper = rememberZipUi(cur) { m -> msg = m; tick++ }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppBar(if (cur == root) project.name else cur.name, onBack = { up() }, actions = {
                Box {
                    TextButton({ addMenu = true }) { Text("+  " + stringResource(R.string.m_add)) }
                    DropdownMenu(addMenu, { addMenu = false }) {
                        DropdownMenuItem({ Text(stringResource(R.string.m_new_file)) }, { addMenu = false; dlg = FDlg.NewFile })
                        DropdownMenuItem({ Text(stringResource(R.string.m_new_dir)) }, { addMenu = false; dlg = FDlg.NewDir })
                        DropdownMenuItem({ Text(stringResource(R.string.import_files)) }, { addMenu = false; importer.pickFiles() })
                        DropdownMenuItem({ Text(stringResource(R.string.import_folder)) }, { addMenu = false; importer.pickFolder() })
                        DropdownMenuItem({ Text(stringResource(R.string.import_zip)) }, { addMenu = false; zipper.pickZip() })
                        DropdownMenuItem({ Text(stringResource(R.string.trash_title)) }, { addMenu = false; onTrash() })
                    }
                }
            })
        },
        bottomBar = { BranchBar(branch, onBranches) }
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
                    var menu by remember { mutableStateOf(false) }
                    Card(Modifier.fillMaxWidth().clickable { open(f) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text((if (f.isDirectory) "\uD83D\uDCC1  " else "\uD83D\uDCC4  ") + f.name,
                                Modifier.weight(1f))
                            Box {
                                TextButton({ menu = true }) { Text("\u22EE") }
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem(
                                        { Text(stringResource(if (f.isDirectory) R.string.open_folder else R.string.open_edit)) },
                                        { menu = false; open(f) })
                                    DropdownMenuItem({ Text(stringResource(R.string.copy)) }, { menu = false; copy(f) })
                                    DropdownMenuItem({ Text(stringResource(R.string.rename)) },
                                        { menu = false; dlg = FDlg.Rename(f) })
                                    DropdownMenuItem({ Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                        { menu = false; dlg = FDlg.Delete(f) })
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
        FDlg.NewFile -> FieldsDialog(stringResource(R.string.m_new_file),
            listOf(stringResource(R.string.file_name)), listOf(""),
            onConfirm = { create(it[0], false); dlg = null }, onDismiss = { dlg = null })
        FDlg.NewDir -> FieldsDialog(stringResource(R.string.m_new_dir),
            listOf(stringResource(R.string.dir_name)), listOf(""),
            onConfirm = { create(it[0], true); dlg = null }, onDismiss = { dlg = null })
        is FDlg.Rename -> FieldsDialog(stringResource(R.string.rename),
            listOf(stringResource(R.string.new_name)), listOf(d.file.name),
            onConfirm = { rename(d.file, it[0]); dlg = null }, onDismiss = { dlg = null })
        // الحذف ينقل العنصر إلى سلة المحذوفات (يبقى 3 أيام ثم يُحذف تلقائيًا)
        is FDlg.Delete -> ConfirmDialog(stringResource(R.string.delete),
            stringResource(R.string.delete_confirm, d.file.name) + "\n" + stringResource(R.string.trash_moved),
            onConfirm = {
                try { Trash.put(ctx, d.file, "deleted"); msg = ctx.getString(R.string.trash_moved); tick++ }
                catch (e: Exception) { err(e) }
                dlg = null
            }, onDismiss = { dlg = null })
    }
}
