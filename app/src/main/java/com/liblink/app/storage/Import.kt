package com.liblink.app.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.File

// عنصر للاستيراد: مصدره (SAF) ومساره النسبي داخل مجلد المشروع
data class ImportItem(val uri: Uri, val rel: String)

enum class ImportMode { REPLACE, KEEP_BOTH }

data class ImportResult(val copied: Int, val replaced: Int)

// استيراد ملفات أو مجلدات كاملة من الهاتف إلى مجلد المشروع
object Import {

    fun fromFiles(ctx: Context, uris: List<Uri>): List<ImportItem> = uris.map { ImportItem(it, displayName(ctx, it)) }

    private fun displayName(ctx: Context, uri: Uri): String =
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) c.getString(i) else null
            } else null
        } ?: "file_${System.currentTimeMillis()}"

    // مجلد كامل: يُنسخ داخل مجلد باسمه مع كل محتوياته (ويُتجاهل مجلد .git)
    fun fromTree(ctx: Context, tree: Uri): List<ImportItem> {
        val root = DocumentFile.fromTreeUri(ctx, tree) ?: return emptyList()
        val base = ProjectStorage.safeName(root.name ?: "folder")
        val out = mutableListOf<ImportItem>()
        fun walk(d: DocumentFile, prefix: String) {
            for (f in d.listFiles()) {
                val n = f.name ?: continue
                if (f.isDirectory) { if (n != ".git") walk(f, "$prefix$n/") }
                else out.add(ImportItem(f.uri, "$prefix$n"))
            }
        }
        walk(root, "$base/")
        return out
    }

    // العناصر التي لها نسخة موجودة مسبقًا في المشروع
    fun conflicts(dir: File, items: List<ImportItem>): List<ImportItem> =
        items.filter { File(dir, it.rel).exists() }

    fun run(ctx: Context, dir: File, items: List<ImportItem>, mode: ImportMode): ImportResult {
        var copied = 0
        var replaced = 0
        for (item in items) {
            var dest = File(dir, item.rel)
            if (dest.exists()) {
                if (mode == ImportMode.REPLACE) {
                    Trash.put(ctx, dest, "replaced") // النسخة القديمة تذهب إلى سلة المحذوفات 3 أيام
                    replaced++
                } else {
                    dest = ProjectStorage.uniqueFile(dest.parentFile ?: dir, dest.name)
                }
            }
            dest.parentFile?.mkdirs()
            ctx.contentResolver.openInputStream(item.uri)?.use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
                copied++
            }
        }
        return ImportResult(copied, replaced)
    }
}
