package com.liblink.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liblink.app.R
import com.liblink.app.doctor.Diagnosis

// "لماذا فشل البناء؟": المرحلة والسبب المحتمل والإجراء المقترح، وسطور الدليل فقط
@Composable
fun DiagnosisDialog(d: Diagnosis, onCopy: (String) -> Unit, onDismiss: () -> Unit) {
    var all by remember { mutableStateOf(false) }
    val copyText = remember(d) {
        buildString {
            append(d.stage)
            if (d.step.isNotBlank()) append(" — ").append(d.step)
            append("\n").append(d.cause).append("\n").append(d.action).append("\n\n")
            append((if (d.allLines.isNotEmpty()) d.allLines else d.evidence).joinToString("\n"))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bd_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Labeled(R.string.bd_stage, d.stage + if (d.step.isNotBlank()) "\n" + d.step else "")
                Labeled(R.string.bd_cause, d.cause)
                Labeled(R.string.bd_action, d.action)
                if (d.evidence.isNotEmpty()) {
                    Text(stringResource(R.string.bd_evidence), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(d.evidence.joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                if (d.allLines.size > d.evidence.size) {
                    TextButton({ all = !all }) {
                        Text(stringResource(if (all) R.string.bd_hide_all else R.string.bd_show_all))
                    }
                    if (all) Text(d.allLines.joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        },
        confirmButton = { TextButton({ onCopy(copyText) }) { Text(stringResource(R.string.copy_error)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
private fun Labeled(label: Int, text: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text)
    }
}
