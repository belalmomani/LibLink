package com.liblink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liblink.app.R
import com.liblink.app.data.DraftStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// محرر أكواد: تلوين، ترقيم أسطر، بحث/استبدال، حفظ تلقائي كمسودة
@Composable
fun EditorScreen(file: File, drafts: DraftStore, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val ext = file.extension.lowercase()

    var original by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var tf by remember { mutableStateOf(TextFieldValue("")) }
    var restore by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var showFind by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf("") }
    var repl by remember { mutableStateOf("") }
    var mark by remember { mutableStateOf<IntRange?>(null) }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()

    // تحميل الملف ثم فحص وجود مسودة غير محفوظة
    LaunchedEffect(file) {
        val text = withContext(Dispatchers.IO) { runCatching { file.readText() }.getOrNull() }
        if (text == null) status = ctx.getString(R.string.unreadable)
        original = text ?: ""
        tf = TextFieldValue(original)
        val d = drafts.get(file.path)
        if (d != null && d != original) restore = d
        loaded = true
    }

    val modified = loaded && tf.text != original

    // حفظ تلقائي للمسودة بعد توقف الكتابة
    LaunchedEffect(tf.text, loaded, restore) {
        if (loaded && restore == null) {
            if (tf.text != original) { delay(800); drafts.put(file.path, tf.text) }
            else drafts.clear(file.path)
        }
    }
    // حفظ المسودة عند مغادرة الشاشة
    val textNow = rememberUpdatedState(tf.text)
    val origNow = rememberUpdatedState(original)
    val loadedNow = rememberUpdatedState(loaded)
    val restoreNow = rememberUpdatedState(restore)
    DisposableEffect(Unit) {
        onDispose {
            if (loadedNow.value && restoreNow.value == null && textNow.value != origNow.value)
                drafts.put(file.path, textNow.value)
        }
    }

    fun save() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { file.writeText(tf.text) }
                original = tf.text
                drafts.clear(file.path)
                status = ctx.getString(R.string.saved)
            } catch (e: Exception) {
                status = ctx.getString(R.string.error_prefix, e.message ?: e.toString())
            }
        }
    }

    fun matches(): List<Int> {
        if (find.isEmpty()) return emptyList()
        val out = mutableListOf<Int>()
        var i = tf.text.indexOf(find, ignoreCase = true)
        while (i >= 0) { out.add(i); i = tf.text.indexOf(find, i + find.length, ignoreCase = true) }
        return out
    }

    fun goTo(pos: Int) {
        tf = tf.copy(selection = TextRange(pos, pos + find.length))
        mark = pos until pos + find.length
        val line = tf.text.substring(0, pos).count { it == '\n' }
        val px = with(density) { (line * 19).sp.toPx() }.toInt()
        scope.launch { vScroll.animateScrollTo((px - 120).coerceAtLeast(0)) }
    }

    fun next() {
        val m = matches()
        if (m.isEmpty()) { status = ctx.getString(R.string.no_match); return }
        status = ctx.getString(R.string.matches, m.size)
        goTo(m.firstOrNull { it >= tf.selection.max } ?: m.first())
    }

    fun replaceOne() {
        val sel = tf.selection
        val t = tf.text
        if (find.isNotEmpty() && !sel.collapsed && sel.max <= t.length &&
            t.substring(sel.min, sel.max).equals(find, ignoreCase = true)) {
            val nt = t.substring(0, sel.min) + repl + t.substring(sel.max)
            tf = TextFieldValue(nt, TextRange(sel.min + repl.length))
            mark = null
        }
        next()
    }

    fun replaceAll() {
        val n = matches().size
        if (n == 0) { status = ctx.getString(R.string.no_match); return }
        tf = TextFieldValue(tf.text.replace(find, repl, ignoreCase = true))
        mark = null
        status = ctx.getString(R.string.replaced, n)
    }

    val code = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)
    val transform = remember(ext, mark) {
        VisualTransformation { t -> TransformedText(Highlighter.highlight(t.text, ext, mark), OffsetMapping.Identity) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppBar(file.name + if (modified) " \u2022" else "", onBack, actions = {
                TextButton({ showFind = !showFind }) { Text(stringResource(R.string.editor_find)) }
                TextButton({ save() }, enabled = modified) { Text(stringResource(R.string.save)) }
            })
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (showFind) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(find, { find = it; mark = null }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.find_label)) }, singleLine = true)
                    OutlinedTextField(repl, { repl = it }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.replace_label)) }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton({ next() }) { Text(stringResource(R.string.next)) }
                        OutlinedButton({ replaceOne() }) { Text(stringResource(R.string.replace)) }
                        OutlinedButton({ replaceAll() }) { Text(stringResource(R.string.replace_all)) }
                    }
                }
            }

            // المحرر دائمًا من اليسار لليمين حتى مع الواجهة العربية
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(vScroll)) {
                    Row(Modifier.fillMaxWidth()) {
                        val lines = tf.text.count { it == '\n' } + 1
                        Text(
                            (1..lines).joinToString("\n"),
                            style = code.copy(color = Color(0xFF5C6BA0), textAlign = TextAlign.End),
                            modifier = Modifier.background(Color(0xFF081038)).padding(horizontal = 8.dp)
                                .widthIn(min = 28.dp)
                        )
                        Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                            BasicTextField(
                                value = tf,
                                onValueChange = { tf = it },
                                textStyle = code.copy(color = Color.White),
                                cursorBrush = SolidColor(Color(0xFF22D3EE)),
                                visualTransformation = transform,
                                modifier = Modifier.defaultMinSize(minWidth = 320.dp).padding(start = 8.dp, end = 24.dp)
                            )
                        }
                    }
                }
            }

            // شريط الحالة: اللغة والسطر والعمود
            val before = tf.text.take(tf.selection.start.coerceAtMost(tf.text.length))
            val ln = before.count { it == '\n' } + 1
            val col = before.length - before.lastIndexOf('\n')
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(Highlighter.languageName(ext), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.line_col, ln, col), style = MaterialTheme.typography.labelMedium)
                    Text(if (modified) stringResource(R.string.unsaved) else status,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    // استرجاع المسودة غير المحفوظة
    restore?.let { draft ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.draft_title)) },
            text = { Text(stringResource(R.string.draft_text)) },
            confirmButton = {
                TextButton({ tf = TextFieldValue(draft); restore = null }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = {
                TextButton({ drafts.clear(file.path); restore = null }) { Text(stringResource(R.string.discard)) }
            }
        )
    }
}
