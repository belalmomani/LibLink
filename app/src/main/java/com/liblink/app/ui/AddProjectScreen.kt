package com.liblink.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liblink.app.R
import com.liblink.app.data.ProjectRepository
import com.liblink.app.data.StorageMode
import com.liblink.app.git.GitManager
import com.liblink.app.storage.ProjectStorage
import kotlinx.coroutines.launch

@Composable
fun AddProjectScreen(
    repo: ProjectRepository,
    initialUrl: String = "",
    initialName: String = "",
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    var mode by remember { mutableStateOf(StorageMode.ORGANIZED) }
    var parent by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val access = rememberAccessGranted() // يتحدث عند العودة من إعدادات الصلاحية

    // اختيار المجلد الأب في الوضع اليدوي
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            val p = ProjectStorage.treeUriToPath(uri)
            if (p == null) message = ctx.getString(R.string.folder_unsupported) else { parent = p; message = "" }
        }
    }
    // صلاحية التخزين لأندرويد 10 وأقل
    val legacyPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    // طلب صلاحية الوصول لكل الملفات (أندرويد 11+) أو صلاحية التخزين القديمة
    fun requestAccess() {
        if (Build.VERSION.SDK_INT >= 30)
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${ctx.packageName}")))
        else legacyPerm.launch(arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }

    fun targetDir() = if (mode == StorageMode.ORGANIZED) ProjectStorage.organizedDir(ctx, name)
    else ProjectStorage.manualDir(parent ?: "", name)

    fun submit() {
        if (name.isBlank()) { message = ctx.getString(R.string.name_required); return }
        if (mode == StorageMode.MANUAL) {
            if (parent == null) { message = ctx.getString(R.string.folder_required); return }
            if (!access) { message = ctx.getString(R.string.need_storage_permission); return }
        }
        val dir = targetDir()
        val cloning = url.isNotBlank()
        if (cloning && !ProjectStorage.isEmptyOrMissing(dir)) {
            message = ctx.getString(R.string.target_not_empty); return
        }
        scope.launch {
            busy = true; message = ""
            try {
                val gm = GitManager(repo.credentials())
                if (cloning) gm.clone(url.trim(), dir) else gm.init(dir, "")
                repo.add(name.trim(), dir.absolutePath, url.trim(), mode)
                onDone()
            } catch (e: Exception) {
                message = ctx.getString(R.string.error_prefix, e.message ?: e.toString())
            }
            busy = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppBar(stringResource(R.string.add_project), onBack) }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.project_name)) }, singleLine = true)
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.remote_url)) },
                supportingText = { Text(stringResource(R.string.remote_url_hint)) })

            StepCard(stringResource(R.string.storage_location)) {
                Row { RadioButton(mode == StorageMode.ORGANIZED, { mode = StorageMode.ORGANIZED })
                    Text(stringResource(R.string.storage_organized), Modifier.align(Alignment.CenterVertically)) }
                Row { RadioButton(mode == StorageMode.MANUAL, { mode = StorageMode.MANUAL })
                    Text(stringResource(R.string.storage_manual), Modifier.align(Alignment.CenterVertically)) }

                if (mode == StorageMode.ORGANIZED) {
                    Text(stringResource(R.string.organized_note), style = MaterialTheme.typography.bodySmall)
                }
                if (mode == StorageMode.MANUAL) {
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT < 30) requestAccess()
                        treePicker.launch(null)
                    }) { Text(stringResource(R.string.choose_folder)) }
                    parent?.let { Text(stringResource(R.string.folder_selected, it), style = MaterialTheme.typography.bodySmall) }
                }
                if (!access) {
                    OutlinedButton(onClick = { requestAccess() }) { Text(stringResource(R.string.grant)) }
                }
                // عرض مجلد المشروع المستقل الناتج
                if (name.isNotBlank() && (mode == StorageMode.ORGANIZED || parent != null))
                    Text(stringResource(R.string.project_folder, targetDir().absolutePath),
                        style = MaterialTheme.typography.bodySmall)
            }

            Busy(busy)
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
            Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (url.isNotBlank()) R.string.clone_action else R.string.create_action))
            }
        }
    }
}
