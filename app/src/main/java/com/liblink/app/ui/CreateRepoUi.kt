package com.liblink.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liblink.app.R
import com.liblink.app.data.Project
import com.liblink.app.git.CreateOptions
import com.liblink.app.git.sanitizeRepoName
import com.liblink.app.storage.Signing
import java.io.File

// حالة نموذج إنشاء المستودع (تُشارك بين شاشة إضافة مشروع وشاشة المشروع)
class CreateRepoState(initialName: String) {
    var name by mutableStateOf(initialName)
    var description by mutableStateOf("")
    var org by mutableStateOf("")
    var isPrivate by mutableStateOf(true) // الافتراضي خاص للأمان
    var readme by mutableStateOf(true)
    var pushNow by mutableStateOf(true)

    val valid: Boolean get() = sanitizeRepoName(name).isNotEmpty()

    fun options() = CreateOptions(sanitizeRepoName(name), description.trim(), isPrivate, org.trim(), readme, pushNow)
}

@Composable
fun rememberCreateRepoState(initialName: String): CreateRepoState = remember { CreateRepoState(sanitizeRepoName(initialName)) }

// ملفات حساسة داخل المشروع: يُحذَّر من رفعها إلى مستودع عام
fun sensitiveFiles(dir: File): List<String> =
    Signing.findKeystores(dir).map { it.name } +
        listOfNotNull(
            File(dir, "app/google-services.json").takeIf { it.exists() }?.name,
            File(dir, "local.properties").takeIf { it.exists() }?.name
        )

@Composable
fun CreateRepoFields(st: CreateRepoState, sensitive: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val clean = sanitizeRepoName(st.name)
        OutlinedTextField(
            st.name, { st.name = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.repo_name)) }, isError = !st.valid,
            supportingText = {
                Text(if (clean.isEmpty()) stringResource(R.string.repo_invalid_name) else stringResource(R.string.repo_name_fixed, clean))
            }
        )
        OutlinedTextField(st.description, { st.description = it }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.repo_desc)) })
        OutlinedTextField(st.org, { st.org = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.repo_org)) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.repo_private), Modifier.weight(1f))
            Switch(st.isPrivate, { st.isPrivate = it })
        }
        if (!st.isPrivate) {
            Text(stringResource(R.string.repo_public_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (sensitive.isNotEmpty()) Text(stringResource(R.string.repo_sensitive_warn, sensitive.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.repo_readme), Modifier.weight(1f))
            Switch(st.readme, { st.readme = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.repo_push_now), Modifier.weight(1f))
            Switch(st.pushNow, { st.pushNow = it })
        }
    }
}

// نافذة إنشاء مستودع لمشروع موجود
@Composable
fun CreateRepoDialog(project: Project, st: CreateRepoState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val sensitive = remember(project.path) { sensitiveFiles(File(project.path)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_repo_title)) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.create_repo_desc), style = MaterialTheme.typography.bodySmall)
                CreateRepoFields(st, sensitive)
            }
        },
        confirmButton = { TextButton(enabled = st.valid, onClick = onConfirm) { Text(stringResource(R.string.repo_create_confirm)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
