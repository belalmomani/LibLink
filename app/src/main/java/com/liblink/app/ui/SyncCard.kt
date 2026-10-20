package com.liblink.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.liblink.app.R
import com.liblink.app.data.LibraryRepository
import com.liblink.app.data.ProjectRepository
import com.liblink.app.sync.SyncManager
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

// المزامنة السحابية: سجّل بنفس البريد على كل جهاز فتتطابق المكتبة والإعدادات.
// قبل أول مزامنة تُعرض معاينة بما سيُضاف وما سيُزال، ويمكن التراجع بعد أي دمج
@Composable
fun SyncCard(repo: ProjectRepository) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lib = remember { LibraryRepository(ctx) }
    val available = remember { SyncManager.available(ctx) }
    var tick by remember { mutableIntStateOf(0) }
    val signedIn = remember(tick) { SyncManager.signedIn() }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var auto by remember { mutableStateOf(repo.syncAuto()) }
    var tokenSync by remember { mutableStateOf(repo.tokenSync()) }
    var askPass by remember { mutableStateOf(false) }
    var confirmOut by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<SyncManager.Preview?>(null) }
    val undoAt = remember(tick, SyncManager.changeCount) { lib.preSyncAt() }

    fun run(ok: String, block: suspend () -> Unit) {
        scope.launch {
            busy = true
            msg = try { block(); ok } catch (e: Exception) { SyncManager.describe(ctx, e) }
            tick++
            busy = false
        }
    }

    // عرض ما سيحدث قبل أي دمج
    fun startPreview() {
        scope.launch {
            busy = true; msg = ""
            try { preview = SyncManager.preview(ctx) } catch (e: Exception) { msg = SyncManager.describe(ctx, e) }
            busy = false
        }
    }

    // تسجيل الدخول أو إنشاء الحساب ثم المعاينة (لا مزامنة قبل موافقتك)
    fun authThenPreview(create: Boolean) {
        scope.launch {
            busy = true; msg = ""
            try {
                if (create) SyncManager.register(email, pass) else SyncManager.signIn(email, pass)
                repo.setSyncApproved(false)
                tick++
                preview = SyncManager.preview(ctx)
            } catch (e: Exception) { msg = SyncManager.describe(ctx, e); tick++ }
            busy = false
        }
    }

    StepCard(stringResource(R.string.sync_title), stringResource(if (available) R.string.sync_desc else R.string.sync_unavailable)) {
        if (!available) return@StepCard

        if (!signedIn) {
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.sync_email)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.sync_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy && email.isNotBlank() && pass.isNotEmpty(), onClick = { authThenPreview(false) }) {
                    Text(stringResource(R.string.sync_signin))
                }
                OutlinedButton(enabled = !busy && email.isNotBlank() && pass.isNotEmpty(), onClick = { authThenPreview(true) }) {
                    Text(stringResource(R.string.sync_register))
                }
            }
            TextButton(enabled = !busy && email.isNotBlank(), onClick = {
                run(ctx.getString(R.string.sync_reset_sent)) { SyncManager.resetPassword(email) }
            }) { Text(stringResource(R.string.sync_forgot)) }
        } else {
            Text(stringResource(R.string.sync_signed_as, SyncManager.email()), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            val last = repo.lastSync()
            Text(if (last > 0) stringResource(R.string.sync_last,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(last)))
            else stringResource(R.string.sync_never), style = MaterialTheme.typography.bodySmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = {
                    if (repo.syncApproved()) run(ctx.getString(R.string.sync_done)) { SyncManager.syncNow(ctx) }
                    else startPreview()
                }) { Text(stringResource(R.string.sync_now)) }
                OutlinedButton(enabled = !busy, onClick = { startPreview() }) { Text(stringResource(R.string.sync_preview_btn)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sync_auto), Modifier.weight(1f))
                Switch(auto, { auto = it; repo.setSyncAuto(it) })
            }
            // الـ Token حساس: لا يُرفع إلا مشفرًا بعبارة مرور تختارها
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sync_token), Modifier.weight(1f))
                Switch(tokenSync, { on ->
                    if (on) askPass = true
                    else {
                        repo.setTokenSync(false); repo.setSyncPassphrase(""); tokenSync = false
                        run(ctx.getString(R.string.sync_done)) { SyncManager.syncNow(ctx) }
                    }
                })
            }
            Text(stringResource(R.string.sync_token_note), style = MaterialTheme.typography.bodySmall)
            if (SyncManager.warningRes != 0) Text(stringResource(SyncManager.warningRes),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

            // تراجع عن آخر دمج غيّر مكتبتك أو إعداداتك
            if (undoAt > 0) {
                OutlinedButton(enabled = !busy, onClick = { confirmUndo = true }) { Text(stringResource(R.string.sync_undo)) }
            }
            TextButton(enabled = !busy, onClick = { confirmOut = true }) {
                Text(stringResource(R.string.sync_signout), color = MaterialTheme.colorScheme.error)
            }
        }
        Busy(busy)
        if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall)
    }

    // معاينة قبل أول مزامنة: ما سيُضاف وما سيُزال
    preview?.let { p ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.sync_preview_title)) },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.sync_preview_counts, p.localCount, p.remoteCount, p.mergedCount))
                    if (p.added.isEmpty() && p.removed.isEmpty()) Text(stringResource(R.string.sync_preview_none))
                    if (p.added.isNotEmpty()) Text(stringResource(R.string.sync_preview_added,
                        p.added.take(8).joinToString(", ") + if (p.added.size > 8) "…" else ""))
                    if (p.removed.isNotEmpty()) Text(stringResource(R.string.sync_preview_removed,
                        p.removed.take(8).joinToString(", ") + if (p.removed.size > 8) "…" else ""),
                        color = MaterialTheme.colorScheme.error)
                    if (p.settingsFromCloud) Text(stringResource(R.string.sync_preview_settings))
                    Text(stringResource(R.string.sync_preview_safe), color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton({
                    preview = null
                    repo.setSyncApproved(true)
                    run(ctx.getString(R.string.sync_done)) { SyncManager.syncNow(ctx) }
                }) { Text(stringResource(R.string.sync_merge_btn)) }
            },
            dismissButton = {
                // الإلغاء لا يغيّر شيئًا: نخرج من الحساب ولا تتم أي مزامنة
                TextButton({
                    preview = null
                    if (!repo.syncApproved()) { SyncManager.signOut(); tick++ }
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (askPass) {
        FieldsDialog(stringResource(R.string.sync_pass_title), listOf(stringResource(R.string.sync_pass_label)), listOf(""),
            onConfirm = { v ->
                askPass = false
                if (v[0].length < 6) msg = ctx.getString(R.string.sync_pass_short)
                else {
                    repo.setSyncPassphrase(v[0]); repo.setTokenSync(true); tokenSync = true
                    run(ctx.getString(R.string.sync_done)) { SyncManager.syncNow(ctx) }
                }
            }, onDismiss = { askPass = false })
    }
    if (confirmUndo) {
        ConfirmDialog(stringResource(R.string.sync_undo), stringResource(R.string.sync_undo_confirm),
            onConfirm = {
                confirmUndo = false
                val n = lib.undoLastSync()
                repo.undoPreSyncSettings()
                msg = ctx.getString(R.string.sync_undone, n); tick++
            }, onDismiss = { confirmUndo = false })
    }
    if (confirmOut) {
        ConfirmDialog(stringResource(R.string.sync_signout), stringResource(R.string.sync_signout_confirm),
            onConfirm = { confirmOut = false; SyncManager.signOut(); repo.setSyncApproved(false); tick++ },
            onDismiss = { confirmOut = false })
    }
}
