package com.liblink.app.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.liblink.app.BuildConfig
import com.liblink.app.LibLinkApp
import com.liblink.app.R
import com.liblink.app.data.Credentials
import com.liblink.app.data.ProjectRepository

@Composable
fun SettingsScreen(repo: ProjectRepository, onBack: () -> Unit, onTrash: () -> Unit) {
    val c = remember { repo.credentials() }
    var user by remember { mutableStateOf(c.user) }
    var token by remember { mutableStateOf(c.token) }
    var name by remember { mutableStateOf(c.authorName) }
    var email by remember { mutableStateOf(c.authorEmail) }
    var saved by remember { mutableStateOf(false) }
    var testAds by remember { mutableStateOf(repo.testAds()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppBar(stringResource(R.string.settings), onBack) }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // تبديل اللغة عربي / إنجليزي
            StepCard(stringResource(R.string.language)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        repo.setLanguage("ar"); AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
                    }) { Text(stringResource(R.string.lang_ar)) }
                    Button(onClick = {
                        repo.setLanguage("en"); AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                    }) { Text(stringResource(R.string.lang_en)) }
                }
            }

            // مزامنة المكتبة والإعدادات بين أجهزتك
            SyncCard(repo)

            StepCard(stringResource(R.string.github_account), stringResource(R.string.github_token_hint)) {
                OutlinedTextField(user, { user = it; saved = false }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.github_user)) }, singleLine = true)
                OutlinedTextField(token, { token = it; saved = false }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.github_token)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                OutlinedTextField(name, { name = it; saved = false }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.git_name)) }, singleLine = true)
                OutlinedTextField(email, { email = it; saved = false }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.git_email)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                Button(onClick = {
                    repo.saveCredentials(Credentials(user.trim(), token.trim(), name.trim(), email.trim()))
                    saved = true
                }) { Text(stringResource(R.string.save)) }
                if (saved) Text(stringResource(R.string.saved), color = MaterialTheme.colorScheme.primary)
            }

            StepCard(stringResource(R.string.trash_title), stringResource(R.string.trash_note)) {
                OutlinedButton(onClick = onTrash) { Text(stringResource(R.string.trash_title)) }
            }

            StepCard("Status") {
                Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                Text(stringResource(if (BuildConfig.HAS_FIREBASE) R.string.firebase_on else R.string.firebase_off))
                val adsOn = LibLinkApp.adsEnabled(stringResource(R.string.start_io_app_id))
                Text(stringResource(if (adsOn) R.string.ads_on else R.string.ads_off))
                // إعلانات تجريبية: تتأكد بها أن الربط يعمل (تُطبَّق بعد إعادة تشغيل التطبيق)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.test_ads), Modifier.weight(1f))
                    Switch(checked = testAds, onCheckedChange = { testAds = it; repo.setTestAds(it) })
                }
                Text(stringResource(R.string.test_ads_note), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
