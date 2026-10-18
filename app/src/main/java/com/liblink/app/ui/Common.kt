package com.liblink.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.liblink.app.storage.ProjectStorage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liblink.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.primary,
            actionIconContentColor = MaterialTheme.colorScheme.primary
        )
    )
}

// بطاقة خطوة مرقمة
@Composable
fun StepCard(title: String, desc: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (desc != null) Text(desc, style = MaterialTheme.typography.bodySmall)
            content()
        }
    }
}

@Composable
fun Busy(show: Boolean) {
    if (show) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(stringResource(R.string.working))
    }
}

// ---------- حوارات مشتركة ----------

@Composable
fun FieldsDialog(
    title: String,
    labels: List<String>,
    initial: List<String>,
    multiline: Boolean = false,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val values = remember { mutableStateListOf(*initial.toTypedArray()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { i, l ->
                    OutlinedTextField(
                        value = values[i], onValueChange = { values[i] = it },
                        label = { Text(l) }, singleLine = !multiline,
                        modifier = Modifier.fillMaxWidth()
                            .then(if (multiline) Modifier.heightIn(min = 220.dp) else Modifier)
                    )
                }
            }
        },
        confirmButton = { TextButton({ onConfirm(values.toList()) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onConfirm) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.ok)) } }
    )
}

// حالة صلاحية الوصول للملفات، تتحدث عند العودة من شاشة الإعدادات
@Composable
fun rememberAccessGranted(): Boolean {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ProjectStorage.hasAllFilesAccess(ctx)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) granted = ProjectStorage.hasAllFilesAccess(ctx)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return granted
}

// شريط الفرع الحالي أسفل الشاشة (مثل MAIN في MGit) — النقر يفتح خيارات الفروع
@Composable
fun BranchBar(branch: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("\u2442", color = MaterialTheme.colorScheme.primary)
            Text(
                branch.ifBlank { "—" }.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text("\u25BE", color = MaterialTheme.colorScheme.primary)
        }
    }
}

// دالة لطلب صلاحية الوصول للملفات (أندرويد 11+ عبر الإعدادات، وأقل عبر نافذة الصلاحية)
@Composable
fun rememberAccessRequester(): () -> Unit {
    val ctx = LocalContext.current
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    return {
        if (Build.VERSION.SDK_INT >= 30)
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        else legacy.launch(arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }
}
