package com.liblink.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.liblink.app.R
import com.liblink.app.storage.Import
import com.liblink.app.storage.ImportItem
import com.liblink.app.storage.ImportMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImportUi(val pickFiles: () -> Unit, val pickFolder: () -> Unit)

// استيراد ملفات أو مجلد إلى target مع سؤال المستخدم عن الاستبدال عند وجود ملفات بنفس الاسم
@Composable
fun rememberImportUi(target: File, onDone: (String) -> Unit): ImportUi {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val targetNow = rememberUpdatedState(target)
    val doneNow = rememberUpdatedState(onDone)
    var pending by remember { mutableStateOf<List<ImportItem>?>(null) }
    var conflicts by remember { mutableStateOf<List<ImportItem>>(emptyList()) }

    fun execute(items: List<ImportItem>, mode: ImportMode) {
        scope.launch {
            val text = try {
                val r = withContext(Dispatchers.IO) { Import.run(ctx, targetNow.value, items, mode) }
                ctx.getString(R.string.import_done, r.copied, r.replaced)
            } catch (e: Exception) {
                ctx.getString(R.string.error_prefix, e.message ?: e.toString())
            }
            doneNow.value(text)
        }
    }

    fun start(items: List<ImportItem>) {
        if (items.isEmpty()) return
        scope.launch {
            val c = withContext(Dispatchers.IO) { Import.conflicts(targetNow.value, items) }
            if (c.isEmpty()) execute(items, ImportMode.REPLACE) else { conflicts = c; pending = items }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch { start(withContext(Dispatchers.IO) { Import.fromFiles(ctx, uris) }) }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch { start(withContext(Dispatchers.IO) { Import.fromTree(ctx, uri) }) }
    }

    pending?.let { items ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.replace_title)) },
            text = {
                Text(stringResource(R.string.replace_msg, conflicts.size) + "\n\n" +
                    conflicts.take(5).joinToString("\n") { it.rel } +
                    if (conflicts.size > 5) "\n…" else "")
            },
            confirmButton = {
                Row {
                    TextButton({ pending = null; execute(items, ImportMode.KEEP_BOTH) }) { Text(stringResource(R.string.keep_both)) }
                    TextButton({ pending = null; execute(items, ImportMode.REPLACE) }) { Text(stringResource(R.string.replace_btn)) }
                }
            },
            dismissButton = { TextButton({ pending = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    return ImportUi(
        pickFiles = { filePicker.launch(arrayOf("*/*")) },
        pickFolder = { folderPicker.launch(null) }
    )
}
