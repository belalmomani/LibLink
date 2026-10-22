package com.liblink.app.storage

import android.content.Context
import android.net.Uri
import com.liblink.app.R
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.math.abs

// الحكم على الـ ZIP: هل يشبه هذا المشروع أم أن الخطأ في الإصدار أو في التطبيق؟
enum class ReviewVerdict { OK, BIG, OLDER, OTHER_APP }

data class ZipReview(val verdict: ReviewVerdict, val lines: List<String>)

// خطة التحديث من ملف ZIP: ما سيُضاف وما سيُستبدل وما هو مطابق (يُتجاهل) + مراجعة المقارنة
data class ZipPlan(
    val topFolder: String?,     // المجلد الأعلى في الـ zip إن وُجد (مثل LibLink/)
    val strip: Boolean,         // هل يُزال هذا المجلد عند الاستخراج
    val added: List<String>,
    val modified: List<String>,
    val same: Int,
    val review: ZipReview
)

data class ZipResult(val added: Int, val replaced: Int, val actions: List<OpAction>)

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

    // ---------------- مراجعة الإصدار ----------------

    private val ignoredDirs = setOf(".git", "build", ".gradle", ".idea", "node_modules", "vendor", ".cxx", "captures")

    private fun localPaths(dir: File): Set<String> {
        val out = HashSet<String>()
        dir.walkTopDown().onEnter { it == dir || it.name !in ignoredDirs }
            .filter { it.isFile }.take(50_000)
            .forEach { out.add(it.relativeTo(dir).path.replace('\\', '/')) }
        return out
    }

    // هوية المشروع من ملفاته: اسم التطبيق والحزمة والإصدار (من ملفات شائعة في أندرويد وويب)
    private class Ident(val ids: Map<String, String>, val version: String?)

    private fun identOf(read: (String) -> String?): Ident {
        val ids = LinkedHashMap<String, String>()
        val gradle = read("app/build.gradle.kts") ?: read("app/build.gradle")
        gradle?.let { t ->
            (Regex("applicationId\\s*=?\\s*[\"']([^\"']+)[\"']").find(t)
                ?: Regex("namespace\\s*=?\\s*[\"']([^\"']+)[\"']").find(t))?.let { ids["applicationId"] = it.groupValues[1] }
        }
        (read("settings.gradle.kts") ?: read("settings.gradle"))?.let { t ->
            Regex("rootProject\\.name\\s*=\\s*[\"']([^\"']+)[\"']").find(t)?.let { ids["rootProject"] = it.groupValues[1] }
        }
        read("package.json")?.let { t ->
            Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(t)?.let { ids["package.json"] = it.groupValues[1] }
        }
        read("composer.json")?.let { t ->
            Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(t)?.let { ids["composer.json"] = it.groupValues[1] }
        }
        read("pubspec.yaml")?.let { t ->
            Regex("^name:\\s*(\\S+)", RegexOption.MULTILINE).find(t)?.let { ids["pubspec"] = it.groupValues[1] }
        }
        val ver = gradle?.let { Regex("versionName\\s*=?\\s*[\"']([^\"']+)[\"']").find(it)?.groupValues?.get(1) }
            ?: read("package.json")?.let { Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
        return Ident(ids, ver)
    }

    private fun nums(v: String): List<Int>? {
        val parts = v.trim().removePrefix("v").split('.')
        val out = parts.map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: return null }
        return if (out.isEmpty()) null else out
    }

    // هل إصدار الـ zip أقدم من إصدار المشروع؟ (أرقام فقط، وإلا نتجاهل)
    private fun isOlder(zipV: String, localV: String): Boolean {
        val a = nums(zipV) ?: return false
        val b = nums(localV) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x < y
        }
        return false
    }

    private fun review(
        ctx: Context, dir: File, zip: ZipFile, list: List<Pair<ZipEntry, String>>,
        added: List<String>, modified: List<Pair<ZipEntry, String>>, same: Int
    ): ZipReview {
        val zipRels = list.map { it.second }.toSet()
        val local = localPaths(dir)
        val total = list.size
        val inBoth = zipRels.count { it in local }
        val onlyLocal = local.count { it !in zipRels }
        val overlapPct = if (total == 0) 0 else inBoth * 100 / total
        val union = (local + zipRels).size
        val different = added.size + modified.size + onlyLocal
        val diffRatio = if (union == 0) 0.0 else different.toDouble() / union

        // هل ملفات الـ zip أقدم من ملفاتك؟ (وقت التعديل داخل الـ zip مقابل وقت ملفك)
        var older = 0
        for ((e, rel) in modified) {
            val f = File(dir, rel)
            if (e.time > 0 && e.time < f.lastModified() - 2000) older++
        }
        val olderRatio = if (modified.isEmpty()) 0.0 else older.toDouble() / modified.size

        // ملفات تغيّر حجمها كثيرًا
        val heavy = modified.filter { (e, rel) ->
            val a = File(dir, rel).length()
            val b = e.size
            maxOf(a, b) >= 200 && abs(a - b).toDouble() / maxOf(a, b) >= 0.6
        }.map { it.second }

        // هوية التطبيق والإصدار في الطرفين
        val byRel = list.associate { it.second to it.first }
        val zipId = identOf { rel ->
            byRel[rel]?.takeIf { it.size in 1..200_000 }?.let { e -> zip.getInputStream(e).use { it.readBytes().decodeToString() } }
        }
        val localId = identOf { rel -> File(dir, rel).takeIf { it.isFile && it.length() < 200_000 }?.readText() }
        val mismatch = zipId.ids.keys.intersect(localId.ids.keys).filter { !zipId.ids[it]!!.equals(localId.ids[it]!!, true) }
        val versionOlder = zipId.version != null && localId.version != null && isOlder(zipId.version, localId.version)

        val verdict = when {
            mismatch.isNotEmpty() || (local.size >= 10 && total >= 5 && overlapPct < 20) -> ReviewVerdict.OTHER_APP
            versionOlder || (modified.size >= 3 && olderRatio >= 0.7) -> ReviewVerdict.OLDER
            union >= 10 && diffRatio >= 0.5 -> ReviewVerdict.BIG
            else -> ReviewVerdict.OK
        }

        val lines = mutableListOf<String>()
        mismatch.forEach { k -> lines += ctx.getString(R.string.rv_id, k, zipId.ids[k], localId.ids[k]) }
        if (zipId.version != null && localId.version != null && zipId.version != localId.version)
            lines += ctx.getString(R.string.rv_version, zipId.version, localId.version)
        lines += ctx.getString(R.string.rv_overlap, overlapPct)
        lines += ctx.getString(R.string.rv_counts, same, modified.size, added.size, onlyLocal)
        if (modified.isNotEmpty() && older > 0) lines += ctx.getString(R.string.rv_older, older, modified.size)
        if (heavy.isNotEmpty()) lines += ctx.getString(R.string.rv_heavy, heavy.take(5).joinToString(", "))
        return ZipReview(verdict, lines)
    }

    fun plan(ctx: Context, dir: File, zipFile: File, strip: Boolean): ZipPlan = ZipFile(zipFile).use { zip ->
        val top = topFolder(zip)
        val list = mapped(zip, strip)
        val added = mutableListOf<String>()
        val modified = mutableListOf<Pair<ZipEntry, String>>()
        var same = 0
        for ((e, rel) in list) {
            val dest = ProjectStorage.resolveInside(dir, rel) ?: continue
            when {
                !dest.exists() -> added.add(rel)
                dest.isFile && dest.length() == e.size && crc(dest) == e.crc -> same++
                else -> modified.add(e to rel)
            }
        }
        ZipPlan(top, strip && top != null, added, modified.map { it.second }, same,
            review(ctx, dir, zip, list, added, modified, same))
    }

    // تطبيق التحديث: الجديد يُضاف، والمختلف يُستبدل (القديم يذهب لسلة المحذوفات 3 أيام)
    fun apply(ctx: Context, dir: File, zipFile: File, strip: Boolean): ZipResult {
        var added = 0
        var replaced = 0
        val actions = mutableListOf<OpAction>()
        ZipFile(zipFile).use { zip ->
            for ((e, rel) in mapped(zip, strip)) {
                val dest = ProjectStorage.resolveInside(dir, rel) ?: continue
                var trashId = ""
                if (dest.exists()) {
                    if (dest.isFile && dest.length() == e.size && crc(dest) == e.crc) continue
                    trashId = Trash.put(ctx, dest, "replaced")
                    replaced++
                } else added++
                dest.parentFile?.mkdirs()
                zip.getInputStream(e).use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                actions += if (trashId.isNotEmpty()) OpAction("REPLACED", dest.absolutePath, trashId)
                else OpAction("CREATED", dest.absolutePath)
            }
        }
        zipFile.delete()
        return ZipResult(added, replaced, actions)
    }
}
