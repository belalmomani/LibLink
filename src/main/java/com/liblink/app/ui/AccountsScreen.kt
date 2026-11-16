package com.liblink.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.liblink.app.data.Credentials
import com.liblink.app.data.ProjectRepository
import com.liblink.app.network.GitHubApi
import kotlinx.coroutines.launch

// إدارة عدة حسابات GitHub: إضافة، تبديل الحساب النشط، إزالة، وتصفح مستودعات كل حساب
@Composable
fun AccountsScreen(repo: ProjectRepository, onBack: () -> Unit, onRepos: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val accounts = remember(tick) { repo.accounts() }
    val active = remember(tick) { repo.credentials().user }
    var token by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var toRemove by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppBar(stringResource(R.string.acc_title), onBack) }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            accounts.forEach { a ->
                val isActive = a.user.equals(active, true)
                StepCard(a.user + if (isActive) "  ●  " + stringResource(R.string.acc_active) else "") {
                    Text(stringResource(R.string.acc_repos_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { onRepos(a.user) }) { Text(stringResource(R.string.acc_repos_btn)) }
                    if (!isActive) {
                        Text(stringResource(R.string.acc_switch_hint), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            if (repo.switchAccount(a.user)) { msg = ctx.getString(R.string.acc_switched, a.user); tick++ }
                        }) { Text(stringResource(R.string.acc_switch_btn)) }
                        Text(stringResource(R.string.acc_remove_hint), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { toRemove = a.user }) { Text(stringResource(R.string.acc_remove_btn)) }
                    }
                }
            }

            StepCard(stringResource(R.string.acc_add_title), stringResource(R.string.acc_add_note)) {
                OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.github_token)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.git_name)) }, singleLine = true)
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.git_email)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                Text(stringResource(R.string.acc_add_hint), style = MaterialTheme.typography.bodySmall)
                Button(enabled = !busy && token.isNotBlank(), onClick = {
                    busy = true; msg = ""
                    scope.launch {
                        try {
                            val t = token.trim()
                            val login = GitHubApi(t).login()
                            if (login.isBlank()) throw IllegalStateException("empty login")
                            repo.addAccount(Credentials(
                                login, t,
                                name.trim().ifBlank { login },
                                email.trim().ifBlank { "$login@users.noreply.github.com" }
                            ))
                            val cur = repo.credentials()
                            // أول حساب يصبح نشطًا، وإعادة إضافة الحساب النشط تحدّث الـ Token
                            if (cur.token.isBlank() || cur.user.equals(login, true)) repo.switchAccount(login)
                            token = ""; name = ""; email = ""
                            msg = ctx.getString(R.string.acc_added, login)
                            tick++
                        } catch (e: Exception) { msg = ghError(ctx, e) }
                        busy = false
                    }
                }) { Text(stringResource(R.string.acc_add_btn)) }
                Busy(busy)
                if (msg.isNotBlank()) Text(msg, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    toRemove?.let { u ->
        ConfirmDialog(
            stringResource(R.string.acc_remove_title),
            stringResource(R.string.acc_remove_text, u),
            onConfirm = { repo.removeAccount(u); toRemove = null; tick++ },
            onDismiss = { toRemove = null }
        )
    }
}

// مستودعات حساب واحد (قراءة فقط) مع بحث واستنساخ
@Composable
fun AccountReposScreen(repo: ProjectRepository, user: String, onBack: () -> Unit, onClone: (url: String, name: String) -> Unit) {
    val ctx = LocalContext.current
    val token = remember(user) { repo.accounts().firstOrNull { it.user.equals(user, true) }?.token.orEmpty() }
    var items by remember { mutableStateOf<List<GitHubApi.RepoItem>?>(null) }
    var error by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(user) {
        try { items = GitHubApi(token).repos() }
        catch (e: Exception) { error = ghError(ctx, e); items = emptyList() }
    }
    val shown = remember(items, query) {
        items.orEmpty().filter { query.isBlank() || it.fullName.contains(query.trim(), true) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppBar(stringResource(R.string.acc_repos_title, user), onBack) }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.acc_search)) }, singleLine = true)
            if (items == null) {
                Text(stringResource(R.string.acc_loading)); Busy(true)
            } else {
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.acc_repos_count, shown.size), style = MaterialTheme.typography.bodySmall)
                if (shown.isEmpty() && error.isBlank()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.acc_empty)) }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(shown, key = { it.fullName }) { r ->
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(r.fullName, style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(if (r.isPrivate) R.string.acc_private else R.string.acc_public),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                if (r.description.isNotBlank()) Text(r.description, style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.acc_clone_hint), style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { onClone(r.cloneUrl, r.name) }) { Text(stringResource(R.string.acc_clone_btn)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
