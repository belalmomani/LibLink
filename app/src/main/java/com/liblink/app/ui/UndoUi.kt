package com.liblink.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liblink.app.R
import com.liblink.app.storage.OpJournal
import com.liblink.app.storage.Operation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UndoUi(val last: Operation?, val ask: () -> Unit)

// "تراجع عن آخر عملية": معاينة ما سيتغير ثم التنفيذ
@Composable
fun rememberUndo(projectPath: String, onDone: (String) -> Unit): UndoUi {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val doneNow = rememberUpdatedState(onDone)
    var dialog by remember { mutableStateOf(false) }
    val version = OpJournal.version // يتغير عند أي تسجيل أو تراجع
    val last = remember(projectPath, version) { OpJournal.last(ctx, projectPath) }

    if (dialog && last != null) {
        val lines = remember(last) { OpJournal.preview(ctx, last, projectPath) }
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text(stringResource(R.string.undo_title)) },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(last.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.undo_note), style = MaterialTheme.typography.bodySmall)
                    Text(lines.take(14).joinToString("\n") + if (lines.size > 14) "\n…" else "",
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton({
                    dialog = false
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { OpJournal.undo(ctx, projectPath) }
                        doneNow.value(if (r == null) ctx.getString(R.string.undo_nothing)
                        else ctx.getString(R.string.undo_done, r.done, r.skipped))
                    }
                }) { Text(stringResource(R.string.undo_apply)) }
            },
            dismissButton = { TextButton({ dialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    return UndoUi(last) { dialog = true }
}
