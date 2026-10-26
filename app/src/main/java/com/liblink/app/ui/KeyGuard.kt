package com.liblink.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.liblink.app.data.Project
import com.liblink.app.data.ProjectRepository
import com.liblink.app.storage.ReleaseStore
import com.liblink.app.storage.Signing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private class KeyPending(
    val apk: File, val runId: Long, val oldFp: String, val newFp: String, val pkg: String, val install: Boolean
)

class KeyGuard(val check: (apk: File, runId: Long, install: Boolean) -> Unit)

// حارس مفتاح التوقيع: يقارن مفتاح كل APK بمفتاح نسخك السابقة، وإن اختلف ينبّهك
// ويعرض: إعادة التوقيع بمفتاحك القديم، أو التثبيت رغم ذلك، أو اعتماد المفتاح الجديد
@Composable
fun rememberKeyGuard(project: Project, repo: ProjectRepository, onMessage: (String) -> Unit): KeyGuard {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ReleaseStore(ctx) }
    val msg = rememberUpdatedState(onMessage)
    var pending by remember { mutableStateOf<KeyPending?>(null) }
    var resign by remember { mutableStateOf<KeyPending?>(null) }

    fun doInstall(apk: File) { installApk(ctx, apk)?.let { msg.value(it) } }

    fun check(apk: File, runId: Long, install: Boolean) {
        scope.launch {
            val k = withContext(Dispatchers.IO) { Signing.ofApk(ctx, apk) }
            if (k == null) {
                msg.value(ctx.getString(R.string.key_unreadable))
                if (install) doInstall(apk)
                return@launch
            }
            val ref = withContext(Dispatchers.IO) { Signing.reference(ctx, repo, store, project.id, k) }
            if (runId != 0L) store.update(project.id, runId) { it.copy(keyFp = k.fp) }
            if (k.fp == ref) {
                msg.value(ctx.getString(R.string.key_same))
                if (install) doInstall(apk)
            } else pending = KeyPending(apk, runId, ref, k.fp, k.pkg, install)
        }
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.key_changed_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.key_changed_msg, Signing.short(p.oldFp), Signing.short(p.newFp)))
                    Button({ resign = p; pending = null }) { Text(stringResource(R.string.key_resign)) }
                    if (p.install) OutlinedButton({ pending = null; doInstall(p.apk) }) { Text(stringResource(R.string.key_install_anyway)) }
                    OutlinedButton({
                        repo.setKeyRef(project.id, p.newFp)
                        pending = null
                        if (p.install) doInstall(p.apk)
                    }) { Text(stringResource(R.string.key_trust)) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ pending = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    resign?.let { p ->
        ResignDialog(project, repo, p,
            onDone = { out ->
                resign = null
                if (p.runId != 0L) store.update(project.id, p.runId) { it.copy(apkPath = out.absolutePath, keyFp = p.oldFp) }
                msg.value(ctx.getString(R.string.key_resigned, out.absolutePath))
                if (p.install) doInstall(out)
            },
            onDismiss = { resign = null })
    }
    return KeyGuard { apk, runId, install -> check(apk, runId, install) }
}

// إعادة توقيع APK بمفتاحك القديم: لا يقبل إلا ملف مفتاح بصمته تطابق المفتاح السابق
@Composable
private fun ResignDialog(project: Project, repo: ProjectRepository, p: KeyPending, onDone: (File) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = remember { repo.savedKey(project.id)?.takeIf { repo.keyFile(project.id).exists() } }
    val found = remember { Signing.findKeystores(File(project.path)) }
    var chosen by remember { mutableStateOf<File?>(if (saved != null) repo.keyFile(project.id) else found.firstOrNull()) }
    var storePass by remember { mutableStateOf(saved?.storePass ?: "") }
    var alias by remember { mutableStateOf(saved?.alias ?: "") }
    var keyPass by remember { mutableStateOf(saved?.keyPass ?: "") }
    var keep by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    // اختيار ملف المفتاح من الهاتف
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val f = File(ctx.cacheDir, "picked_old.ks")
            ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            chosen = f
        }
    }

    fun go() {
        val ks = chosen ?: return
        scope.launch {
            busy = true; error = ""
            try {
                val out = withContext(Dispatchers.IO) {
                    val store = Signing.loadKeyStore(ks, storePass)
                    val a = alias.ifBlank { Signing.firstAlias(store) }
                    val fp = Signing.keystoreFp(store, a) ?: throw IllegalStateException(ctx.getString(R.string.key_bad_alias))
                    // نتأكد أنه فعلًا مفتاحك القديم قبل التوقيع
                    if (fp != p.oldFp) throw IllegalStateException(ctx.getString(R.string.key_not_match, Signing.short(fp), Signing.short(p.oldFp)))
                    val outFile = File(p.apk.parentFile, p.apk.nameWithoutExtension + "-oldkey.apk")
                    Signing.resign(p.apk, outFile, store, a, keyPass.ifBlank { storePass })
                    if (Signing.ofApk(ctx, outFile)?.fp != p.oldFp) throw IllegalStateException("signature check failed")
                    if (keep) {
                        val dest = repo.keyFile(project.id)
                        if (ks.absolutePath != dest.absolutePath) ks.copyTo(dest, overwrite = true)
                        repo.saveKey(project.id, a, storePass, keyPass)
                    }
                    outFile
                }
                onDone(out)
            } catch (e: Exception) { error = e.message ?: e.toString() }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.key_resign_title)) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.key_resign_desc), style = MaterialTheme.typography.bodySmall)
                if (saved != null) Text(stringResource(R.string.key_use_saved), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall)
                if (found.isNotEmpty()) {
                    Text(stringResource(R.string.key_found), style = MaterialTheme.typography.labelMedium)
                    found.forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(chosen?.absolutePath == f.absolutePath, { chosen = f })
                            Text(f.relativeTo(File(project.path)).path, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                OutlinedButton({ picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.key_pick)) }
                chosen?.let { Text(stringResource(R.string.key_picked, it.name), style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(storePass, { storePass = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.key_store_pass)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                OutlinedTextField(alias, { alias = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.key_alias)) })
                OutlinedTextField(keyPass, { keyPass = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.key_key_pass)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.key_remember), Modifier.weight(1f))
                    Switch(keep, { keep = it })
                }
                Text(stringResource(R.string.key_remember_note), style = MaterialTheme.typography.bodySmall)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Busy(busy)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && chosen != null && storePass.isNotEmpty(), onClick = { go() }) {
                Text(stringResource(R.string.key_do_resign))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } }
    )
}
