package com.liblink.app.storage

import android.content.Context
import com.liblink.app.data.Project
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ceil

// سلة محذوفات مؤقتة: الملفات المحذوفة أو المستبدلة تبقى 3 أيام ثم تُحذف تلقائيًا
object Trash {
    const val KEEP_MS = 3L * 24 * 60 * 60 * 1000

    data class Entry(val dir: File, val item: File, val orig: File, val at: Long, val reason: String)

    private fun root(ctx: Context) = File(ctx.filesDir, "trash").apply { mkdirs() }

    // نقل ملف أو مجلد إلى السلة مع حفظ مساره الأصلي ووقت الحذف
    fun put(ctx: Context, file: File, reason: String) {
        if (!file.exists()) return
        val d = File(root(ctx), "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}")
        val target = File(File(d, "item"), file.name)
        target.parentFile?.mkdirs()
        if (file.isDirectory) file.copyRecursively(target, overwrite = true) else file.copyTo(target, overwrite = true)
        File(d, "meta.json").writeText(
            JSONObject().put("orig", file.absolutePath).put("at", System.currentTimeMillis())
                .put("reason", reason).toString()
        )
        file.deleteRecursively()
    }

    fun list(ctx: Context): List<Entry> =
        (root(ctx).listFiles() ?: emptyArray()).mapNotNull { d ->
            runCatching {
                val m = JSONObject(File(d, "meta.json").readText())
                val orig = File(m.getString("orig"))
                val item = File(File(d, "item"), orig.name)
                if (!item.exists()) null else Entry(d, item, orig, m.getLong("at"), m.optString("reason"))
            }.getOrNull()
        }.sortedByDescending { it.at }

    // الاستعادة إلى المسار الأصلي، وباسم جديد (1) إن وُجد ملف بنفس الاسم
    fun restore(e: Entry): File {
        var dest = e.orig
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest = ProjectStorage.uniqueFile(dest.parentFile ?: dest, dest.name)
        if (e.item.isDirectory) e.item.copyRecursively(dest) else e.item.copyTo(dest)
        e.dir.deleteRecursively()
        return dest
    }

    fun delete(e: Entry) = e.dir.deleteRecursively()

    fun emptyAll(ctx: Context) = root(ctx).deleteRecursively()

    // حذف ما مضى عليه أكثر من 3 أيام
    fun purge(ctx: Context): Int {
        val limit = System.currentTimeMillis() - KEEP_MS
        var n = 0
        root(ctx).listFiles()?.forEach { d ->
            val at = runCatching { JSONObject(File(d, "meta.json").readText()).getLong("at") }.getOrNull()
                ?: d.lastModified()
            if (at < limit) { d.deleteRecursively(); n++ }
        }
        return n
    }

    fun daysLeft(e: Entry): Int =
        ceil((e.at + KEEP_MS - System.currentTimeMillis()) / 86_400_000.0).toInt().coerceAtLeast(0)

    // الاستعادة كملف ZIP: يحفظ المحذوف كله بمجلداته الأصلية داخل ملف واحد.
    // الملفات تُوضع تحت مجلد باسم المشروع، فيمكن استيرادها بـ "استيراد تحديث ZIP" لإعادة الكل دفعة واحدة
    fun exportZip(entries: List<Entry>, projects: List<Project>, out: File): Int {
        out.parentFile?.mkdirs()
        val used = HashSet<String>()
        // اسم غير مكرر عند وجود عدة نسخ محذوفة لنفس الملف: a.txt ثم a(2).txt
        fun unique(path: String): String {
            if (used.add(path)) return path
            val slash = path.lastIndexOf('/')
            val dot = path.lastIndexOf('.')
            val hasExt = dot > slash + 1
            val base = if (hasExt) path.substring(0, dot) else path
            val ext = if (hasExt) path.substring(dot) else ""
            var i = 2
            while (true) {
                val c = "$base($i)$ext"
                if (used.add(c)) return c
                i++
            }
        }
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            for (e in entries.sortedByDescending { it.at }) {
                val abs = e.orig.absolutePath
                val proj = projects.firstOrNull { abs.startsWith(it.path.trimEnd('/') + "/") }
                val rel = if (proj != null)
                    ProjectStorage.safeName(proj.name) + "/" + abs.removePrefix(proj.path.trimEnd('/') + "/")
                else "restored/" + e.orig.name
                val base = unique(rel)
                if (e.item.isDirectory) {
                    zip.putNextEntry(ZipEntry("$base/")); zip.closeEntry()
                    e.item.walkTopDown().forEach { f ->
                        val r = f.relativeTo(e.item).path.replace('\\', '/')
                        if (r.isEmpty()) return@forEach
                        if (f.isDirectory) { zip.putNextEntry(ZipEntry("$base/$r/")); zip.closeEntry() }
                        else { zip.putNextEntry(ZipEntry("$base/$r")); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
                    }
                } else {
                    zip.putNextEntry(ZipEntry(base))
                    e.item.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return entries.size
    }
}
