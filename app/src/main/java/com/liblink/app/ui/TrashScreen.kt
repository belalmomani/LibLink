package com.liblink.app.ui

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
import com.liblink.app.storage.Trash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// سلة المحذوفات: استعادة أو حذف نهائي. ما مضى عليه 3 أيام يُحذف تلقائيًا
@Composable
fun TrashScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }
    var confirmEmpty by remember { mutableStateOf(false) }
    val entries = remember(tick) { Trash.purge(ctx); Trash.list(ctx) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppBar(stringResource(R.string.trash_title), onBack, actions = {
                if (entries.isNotEmpty())
                    TextButton({ confirmEmpty = true }) { Text(stringResource(R.string.trash_empty_btn)) }
            })
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.trash_note), style = MaterialTheme.typography.bodySmall)
            if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
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
