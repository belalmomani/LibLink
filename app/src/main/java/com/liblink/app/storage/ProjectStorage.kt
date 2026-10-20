package com.liblink.app.storage

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import java.io.File

object ProjectStorage {

    // تنظيف الاسم ليصلح كاسم مجلد
    fun safeName(name: String): String =
        name.trim().replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").trim('_').ifEmpty { "project" }

    // الوضع المنظم: Download/LibLink/<اسم المشروع> عند توفر صلاحية الوصول للملفات،
    // وإلا مجلد التطبيق. لكل مشروع مجلد مستقل
    fun organizedRoot(ctx: Context): File {
        val base = if (hasAllFilesAccess(ctx))
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        else ctx.getExternalFilesDir(null) ?: ctx.filesDir
        return File(base, "LibLink").apply { mkdirs() }
    }

    fun organizedDir(ctx: Context, name: String): File = File(organizedRoot(ctx), safeName(name))

    // التأكد أن المسار النسبي داخل مجلد المشروع (منع ../)
    fun resolveInside(base: File, rel: String): File? {
        if (rel.isBlank()) return null
        val f = File(base, rel.trim())
        return if (f.canonicalPath.startsWith(base.canonicalPath + File.separator)) f else null
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

    fun hasAllFilesAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    fun isEmptyOrMissing(dir: File) = !dir.exists() || dir.list().isNullOrEmpty()

    // اسم غير مكرر: icon.png ← icon(1).png (مثل مدير الملفات)
    fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.')
        val base = if (dot <= 0) name else name.substring(0, dot)
        val ext = if (dot <= 0) "" else name.substring(dot)
        var i = 1
        while (true) {
            f = File(dir, "$base($i)$ext")
            if (!f.exists()) return f
            i++
        }
    }

    // نسخ الملفات المختارة إلى مجلد المشروع
    fun copyInto(ctx: Context, dir: File, uris: List<Uri>): Int {
        dir.mkdirs()
        var n = 0
        for (uri in uris) {
            val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            } ?: "file_${System.currentTimeMillis()}"
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                uniqueFile(dir, name).outputStream().use { input.copyTo(it) }
                n++
            }
        }
        return n
    }
}
