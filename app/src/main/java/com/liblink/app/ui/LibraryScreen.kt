package com.liblink.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liblink.app.R
import com.liblink.app.data.LibItem
import com.liblink.app.data.LibraryRepository
import com.liblink.app.data.suggestLibName
import com.liblink.app.network.GitHubApi
import com.liblink.app.sync.SyncManager

// اسم التصنيف: الافتراضي مترجم، والمخصص كما كتبه المستخدم
@Composable
fun categoryLabel(key: String): String = when (key) {
    "android" -> stringResource(R.string.cat_android)
    "java" -> stringResource(R.string.cat_java)
    "python" -> stringResource(R.string.cat_python)
    "ui" -> stringResource(R.string.cat_ui)
    "web" -> stringResource(R.string.cat_web)
    "other" -> stringResource(R.string.cat_other)
    else -> key
}

private val STAR = Color(0xFFF59E0B)

// مكتبتي: حفظ المستودعات والمكتبات المفضلة محليًا وتصنيفها والرجوع إليها بسرعة
@Composable
fun LibraryScreen(share: String? = null, onBack: () -> Unit, onClone: (url: String, name: String) -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val lib = remember { LibraryRepository(ctx) }
    val access = rememberAccessGranted()
    val requestAccess = rememberAccessRequester()
    var tick by remember { mutableIntStateOf(0) }
    val all = remember(tick, SyncManager.changeCount) { lib.items() }
    val categories = remember(tick, SyncManager.changeCount) { lib.categories() }
    val missing = remember(tick, access) { if (access) lib.backupMissingCount() else 0 }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") } // "" = الكل، "*" = المفضلة، غير ذلك = تصنيف
    var adding by remember { mutableStateOf(share != null) }
    var prefill by remember { mutableStateOf(share ?: "") } // رابط جاء من المشاركة
    var editing by remember { mutableStateOf<LibItem?>(null) }
    var deleting by remember { mutableStateOf<LibItem?>(null) }
    var msg by remember { mutableStateOf("") }

    // عند منح صلاحية الملفات نكتب النسخة الاحتياطية فورًا
    LaunchedEffect(access) { if (access && lib.backupMissingCount() == 0) lib.writeBackup() }

    val shown = remember(all, query, filter) {
        val q = query.trim().lowercase()
        all.filter {
            (q.isEmpty() || it.name.lowercase().contains(q) || it.url.lowercase().contains(q) ||
                it.note.lowercase().contains(q)) &&
                when (filter) { "" -> true; "*" -> it.favorite; else -> it.category == filter }
        }.sortedWith(compareByDescending<LibItem> { it.favorite }.thenByDescending { it.addedAt })
    }

    fun open(url: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { msg = ctx.getString(R.string.error_prefix, e.message ?: e.toString()) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppBar(stringResource(R.string.library_title), onBack, actions = {
                TextButton({ adding = true }) { Text("+  " + stringResource(R.string.lib_add)) }
            })
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            // استعادة من نسخة احتياطية قديمة (بعد إعادة تثبيت التطبيق مثلًا)
            if (missing > 0) {
                StepCard(stringResource(R.string.lib_restore_title), stringResource(R.string.lib_restore_msg, missing)) {
                    Button({
                        val n = lib.restoreFromBackup()
                        msg = ctx.getString(R.string.restore_done, n); tick++
                    }) { Text(stringResource(R.string.lib_restore_btn)) }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (!access) {
                StepCard(stringResource(R.string.lib_backup_title), stringResource(R.string.lib_backup_desc)) {
                    OutlinedButton({ requestAccess() }) { Text(stringResource(R.string.grant)) }
                }
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text(stringResource(R.string.search_hint)) }, singleLine = true)
            LazyRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(filter == "", { filter = "" }, { Text(stringResource(R.string.f_all)) }) }
                item { FilterChip(filter == "*", { filter = "*" }, { Text("\u2605 " + stringResource(R.string.lib_favorites)) }) }
                items(categories) { c -> FilterChip(filter == c, { filter = c }, { Text(categoryLabel(c)) }) }
            }
            if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(if (all.isEmpty()) R.string.lib_empty else R.string.no_results))
                }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(shown, key = { it.id }) { item ->
                    val gh = GitHubApi.parse(item.url)
                    val isLink = item.url.startsWith("http", ignoreCase = true)
                    var menu by remember { mutableStateOf(false) }
                    Card(Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton({ lib.toggleFavorite(item.id); tick++ }, contentPadding = PaddingValues(0.dp)) {
                                    Text(if (item.favorite) "\u2605" else "\u2606",
                                        color = if (item.favorite) STAR else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleLarge)
                                }
                                Text(item.name, style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
                                Surface(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(50)) {
                                    Text(categoryLabel(item.category), style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                                }
                            }
                            Text(item.url, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isLink) TextButton({ open(item.url) }) { Text(stringResource(R.string.lib_open)) }
                                TextButton({
                                    clipboard.setText(AnnotatedString(item.url)); msg = ctx.getString(R.string.link_copied)
                                }) { Text(stringResource(R.string.copy_link)) }
                                if (gh != null) TextButton({ onClone(item.url, item.name) }) {
                                    Text(stringResource(R.string.lib_clone))
                                }
                                Spacer(Modifier.weight(1f))
                                Box {
                                    TextButton({ menu = true }) { Text("\u22EE") }
                                    DropdownMenu(menu, { menu = false }) {
                                        DropdownMenuItem({ Text(stringResource(R.string.edit)) }, { menu = false; editing = item })
                                        DropdownMenuItem({ Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                            { menu = false; deleting = item })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        LibItemDialog(
            initial = editing, categories = categories, prefillUrl = prefill,
            onSave = { name, url, cat, note, newCat, clone ->
                val category = newCat.trim().ifEmpty { cat }
                if (newCat.isNotBlank()) lib.addCategory(newCat)
                val finalName = name.trim().ifEmpty { suggestLibName(url).ifBlank { url.trim() } }
                val e = editing
                if (e == null) {
                    if (lib.exists(url)) msg = ctx.getString(R.string.lib_exists)
                    else { lib.add(finalName, url.trim(), category, note.trim()); msg = ctx.getString(R.string.lib_saved) }
                } else lib.update(e.copy(name = finalName, url = url.trim(), category = category, note = note.trim()))
                adding = false; editing = null; prefill = ""; tick++
                if (clone) onClone(url.trim(), finalName)
            },
            onDismiss = { adding = false; editing = null; prefill = "" }
        )
    }

    deleting?.let { d ->
        ConfirmDialog(stringResource(R.string.delete), stringResource(R.string.delete_confirm, d.name),
            onConfirm = { lib.remove(d.id); deleting = null; tick++ }, onDismiss = { deleting = null })
    }
}

@Composable
private fun LibItemDialog(
    initial: LibItem?,
    prefillUrl: String,
    categories: List<String>,
    onSave: (name: String, url: String, category: String, note: String, newCategory: String, clone: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var name by remember { mutableStateOf(initial?.name ?: suggestLibName(prefillUrl)) }
    var url by remember { mutableStateOf(initial?.url ?: prefillUrl) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var cat by remember { mutableStateOf(initial?.category ?: "other") }
    var newCat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    // الاسم يُؤخذ تلقائيًا من آخر الرابط ما لم يعدّله المستخدم بنفسه
    var autoName by remember { mutableStateOf(initial == null) }
    val suggested = suggestLibName(url)

    fun setUrl(v: String) {
        url = v; error = false
        if (autoName) name = suggestLibName(v)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.lib_add else R.string.lib_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(url, { setUrl(it) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.lib_url)) }, isError = error,
                    supportingText = { if (error) Text(stringResource(R.string.lib_url_required)) },
                    trailingIcon = {
                        TextButton({ clipboard.getText()?.text?.let { setUrl(it.trim()) } }) {
                            Text(stringResource(R.string.paste))
                        }
                    })
                OutlinedTextField(name, { name = it; autoName = false }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.lib_name)) }, singleLine = true)
                // موافقة المستخدم على الاسم المأخوذ من الرابط
                if (suggested.isNotBlank()) {
                    if (name != suggested) {
                        TextButton({ name = suggested; autoName = true }) {
                            Text(stringResource(R.string.lib_use_name, suggested))
                        }
                    } else if (initial == null) {
                        Text(stringResource(R.string.lib_name_from_link), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(stringResource(R.string.lib_category), style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { c ->
                        FilterChip(cat == c && newCat.isBlank(), { cat = c; newCat = "" }, { Text(categoryLabel(c)) })
                    }
                }
                OutlinedTextField(newCat, { newCat = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.lib_new_category)) }, singleLine = true)
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.lib_note)) })
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton({ if (url.isBlank()) error = true else onSave(name, url, cat, note, newCat, false) }) {
                    Text(stringResource(R.string.save))
                }
                // "الاستدعاء": حفظ ثم إضافة المستودع كمشروع مباشرة
                if (initial == null && GitHubApi.parse(url) != null) {
                    TextButton({ onSave(name, url, cat, note, newCat, true) }) {
                        Text(stringResource(R.string.lib_save_clone))
                    }
                }
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
