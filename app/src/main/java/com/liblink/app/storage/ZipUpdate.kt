package com.liblink.app.storage

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

// خطة التحديث من ملف ZIP: ما سيُضاف وما سيُستبدل وما هو مطابق (يُتجاهل)
data class ZipPlan(
    val topFolder: String?,     // المجلد الأعلى في الـ zip إن وُجد (مثل LibLink/)
    val strip: Boolean,         // هل يُزال هذا المجلد عند الاستخراج
    val added: List<String>,
    val modified: List<String>,
    val same: Int
)

object ZipUpdate {

    // نسخ الـ zip المختار إلى الذاكرة المؤقتة لقراءة فهرسه (الحجم وCRC)
    fun copyToCache(ctx: Context, uri: Uri): File {
        val f = File(ctx.cacheDir, "update_${System.currentTimeMillis()}.zip")
        ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            ?: throw IllegalStateException("cannot open file")
        return f
    }

    private fun relevant(name: String) = !name.endsWith("/") &&
        !name.startsWith("__MACOSX/") && !name.contains("/__MACOSX/") &&
        !name.startsWith(".git/") && !name.contains("/.git/")

    // إن كانت كل الملفات داخل مجلد واحد (مثل LibLink/) نعيده لإزالته
    fun topFolder(zip: ZipFile): String? {
        val names = zip.entries().asSequence().map { it.name }.filter { relevant(it) }.toList()
        if (names.isEmpty() || !names.first().contains('/')) return null
        val first = names.first().substringBefore('/')
        return if (names.all { it.startsWith("$first/") }) "$first/" else null
    }

    private fun crc(f: File): Long {
        val c = CRC32()
        f.inputStream().use { i ->
            val buf = ByteArray(8192)
            while (true) { val n = i.read(buf); if (n < 0) break; c.update(buf, 0, n) }
        }
        return c.value
    }

    private fun mapped(zip: ZipFile, strip: Boolean): List<Pair<ZipEntry, String>> {
        val top = if (strip) topFolder(zip) else null
        return zip.entries().asSequence().filter { relevant(it.name) }
            .map { it to (if (top != null) it.name.removePrefix(top) else it.name) }
            .filter { it.second.isNotBlank() }.toList()
    }

    fun plan(dir: File, zipFile: File, strip: Boolean): ZipPlan = ZipFile(zipFile).use { zip ->
        val top = topFolder(zip)
        val added = mutableListOf<String>()
        val modified = mutableListOf<String>()
        var same = 0
        for ((e, rel) in mapped(zip, strip)) {
            val dest = ProjectStorage.resolveInside(dir, rel) ?: continue
            when {
                !dest.exists() -> added.add(rel)
                dest.isFile && dest.length() == e.size && crc(dest) == e.crc -> same++
                else -> modified.add(rel)
            }
        }
        ZipPlan(top, strip && top != null, added, modified, same)
    }

    // تطبيق التحديث: الجديد يُضاف، والمختلف يُستبدل (القديم يذهب لسلة المحذوفات 3 أيام)
    fun apply(ctx: Context, dir: File, zipFile: File, strip: Boolean): Pair<Int, Int> {
        var added = 0
        var replaced = 0
        ZipFile(zipFile).use { zip ->
            for ((e, rel) in mapped(zip, strip)) {
                val dest = ProjectStorage.resolveInside(dir, rel) ?: continue
                if (dest.exists()) {
                    if (dest.isFile && dest.length() == e.size && crc(dest) == e.crc) continue
                    Trash.put(ctx, dest, "replaced")
                    replaced++
                } else added++
                dest.parentFile?.mkdirs()
                zip.getInputStream(e).use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            }
        }
        zipFile.delete()
        return added to replaced
    }
}
