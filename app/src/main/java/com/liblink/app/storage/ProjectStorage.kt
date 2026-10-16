package com.liblink.app.storage

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File

object ProjectStorage {

    // تنظيف الاسم ليصلح كاسم مجلد
    fun safeName(name: String): String =
        name.trim().replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").trim('_').ifEmpty { "project" }

    // الوضع المنظم: LibLink/<اسم المشروع> داخل مجلد التطبيق، ولكل مشروع مجلد مستقل
    fun organizedDir(ctx: Context, name: String): File {
        val root = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "LibLink").apply { mkdirs() }
        return File(root, safeName(name))
    }

    // الوضع اليدوي: مجلد مستقل باسم المشروع داخل المجلد المختار
    fun manualDir(parent: String, name: String): File = File(parent, safeName(name))

    // تحويل رابط المجلد المختار (SAF) إلى مسار فعلي
    fun treeUriToPath(uri: Uri): String? {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":", limit = 2)
        val volume = parts[0]
        val rel = parts.getOrElse(1) { "" }
        val base = if (volume == "primary") Environment.getExternalStorageDirectory().absolutePath
        else if (volume.matches(Regex("[A-Za-z0-9-]+"))) "/storage/$volume" else return null
        return if (rel.isEmpty()) base else "$base/$rel"
    }

    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true

    fun isEmptyOrMissing(dir: File) = !dir.exists() || dir.list().isNullOrEmpty()

    // نسخ الملفات المختارة إلى مجلد المشروع
    fun copyInto(ctx: Context, dir: File, uris: List<Uri>): Int {
        dir.mkdirs()
        var n = 0
        for (uri in uris) {
            val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            } ?: "file_${System.currentTimeMillis()}"
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                File(dir, name).outputStream().use { input.copyTo(it) }
                n++
            }
        }
        return n
    }
}
