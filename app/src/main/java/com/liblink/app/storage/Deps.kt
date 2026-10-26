package com.liblink.app.storage

import android.content.Context
import com.liblink.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// مكتبة أو إضافة معرّفة بإصدار ثابت داخل ملفات Gradle
data class DepRef(
    val file: String,      // الملف النسبي داخل المشروع
    val kind: String,      // dep أو plugin
    val coord: String,     // group:artifact أو معرّف الإضافة
    val current: String,
    val original: String   // النص الذي سيُعدَّل عند التحديث
)

// level: 0 محدّث، 1 تحديث صغير، 2 تحديث رئيسي، -1 غير موجود على الإنترنت
data class DepStatus(val ref: DepRef, val latest: String?, val level: Int)

// فاحص تحديثات المكتبات: يقرأ ملفات Gradle ويقارن مع مستودعات Google وMaven Central وبوابة الإضافات
object Deps {
    private val dep = Regex("[\"']([A-Za-z][\\w.\\-]*):([\\w.\\-]+):([\\w.\\-+]+)[\"']")
    private val plugin = Regex("id\\s*\\(?\\s*[\"']([^\"']+)[\"']\\s*\\)?\\s*version\\s*[\"']([^\"'$]+)[\"']")
    private val kplugin = Regex("kotlin\\(\\s*[\"']([\\w.\\-]+)[\"']\\s*\\)\\s*version\\s*[\"']([^\"'$]+)[\"']")
    private val preRegex = Regex("alpha|beta|rc|dev|snapshot|-m\\d|preview|eap", RegexOption.IGNORE_CASE)

    fun isPre(v: String) = preRegex.containsMatchIn(v)

    fun scan(dir: File): List<DepRef> {
        val out = mutableListOf<DepRef>()
        val files = mutableListOf<File>()
        for (n in listOf("build.gradle.kts", "build.gradle", "settings.gradle.kts", "settings.gradle")) files += File(dir, n)
        dir.listFiles()?.filter { it.isDirectory && it.name !in setOf(".git", "build", ".gradle", ".github", "gradle") }
            ?.forEach { d -> for (n in listOf("build.gradle.kts", "build.gradle")) files += File(d, n) }
        for (f in files.filter { it.isFile && it.length() < 300_000 }) {
            val rel = f.relativeTo(dir).path.replace('\\', '/')
            val text = f.readText()
            for (m in dep.findAll(text)) {
                val g = m.groupValues[1]; val a = m.groupValues[2]; val v = m.groupValues[3]
                if (g.startsWith("http")) continue
                out += DepRef(rel, "dep", "$g:$a", v, "$g:$a:$v")
            }
            for (m in plugin.findAll(text)) out += DepRef(rel, "plugin", m.groupValues[1], m.groupValues[2], m.value)
            for (m in kplugin.findAll(text)) out += DepRef(rel, "plugin", "org.jetbrains.kotlin." + m.groupValues[1], m.groupValues[2], m.value)
        }
        val toml = File(dir, "gradle/libs.versions.toml")
        if (toml.isFile && toml.length() < 300_000) out += scanCatalog(dir, toml)
        return out.distinctBy { it.file + "|" + it.original }
    }

    // كتالوج الإصدارات gradle/libs.versions.toml
    private fun scanCatalog(dir: File, f: File): List<DepRef> {
        val rel = f.relativeTo(dir).path.replace('\\', '/')
        val versions = HashMap<String, Pair<String, String>>() // مفتاح ← (قيمة، سطر)
        var section = ""
        val lines = f.readLines()
        for (l in lines) {
            val t = l.trim()
            if (t.startsWith("[")) { section = t; continue }
            if (section == "[versions]") {
                Regex("^([\\w\\-.]+)\\s*=\\s*\"([^\"]+)\"").find(t)?.let { versions[it.groupValues[1]] = it.groupValues[2] to l }
            }
        }
        val out = mutableListOf<DepRef>()
        section = ""
        for (l in lines) {
            val t = l.trim()
            if (t.startsWith("[")) { section = t; continue }
            if (section != "[libraries]" && section != "[plugins]") continue
            val coord = if (section == "[plugins]") Regex("id\\s*=\\s*\"([^\"]+)\"").find(t)?.groupValues?.get(1)
            else Regex("module\\s*=\\s*\"([^\"]+)\"").find(t)?.groupValues?.get(1)
                ?: run {
                    val g = Regex("group\\s*=\\s*\"([^\"]+)\"").find(t)?.groupValues?.get(1)
                    val n = Regex("name\\s*=\\s*\"([^\"]+)\"").find(t)?.groupValues?.get(1)
                    if (g != null && n != null) "$g:$n" else null
                }
            if (coord == null) continue
            val kind = if (section == "[plugins]") "plugin" else "dep"
            val ref = Regex("version\\.ref\\s*=\\s*\"([^\"]+)\"").find(t)?.groupValues?.get(1)
            val inline = Regex("version\\s*=\\s*\"([^\"]+)\"").find(t)?.groupValues?.get(1)
            when {
                ref != null && versions[ref] != null -> out += DepRef(rel, kind, coord, versions[ref]!!.first, versions[ref]!!.second)
                inline != null -> out += DepRef(rel, kind, coord, inline, l)
            }
        }
        return out
    }

    // ---------------- الإصدارات الأحدث ----------------

    suspend fun check(refs: List<DepRef>): List<DepStatus> = coroutineScope {
        val keys = refs.map { Triple(it.kind, it.coord, isPre(it.current)) }.distinct()
        val sem = Semaphore(6)
        val results = keys.map { k ->
            async(Dispatchers.IO) { sem.withPermit { k to fetchLatest(k.first, k.second, k.third) } }
        }.awaitAll().toMap()
        refs.map { r ->
            val latest = results[Triple(r.kind, r.coord, isPre(r.current))]
            DepStatus(r, latest, level(r.current, latest))
        }
    }

    private fun fetch(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "LibLink")
        try { if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null } finally { c.disconnect() }
    } catch (_: Exception) { null }

    private fun fetchLatest(kind: String, coord: String, allowPre: Boolean): String? {
        val group = if (kind == "plugin") coord else coord.substringBefore(':')
        val artifact = if (kind == "plugin") "$coord.gradle.plugin" else coord.substringAfter(':')
        val path = group.replace('.', '/') + "/" + artifact
        val google = "https://dl.google.com/dl/android/maven2/$path/maven-metadata.xml"
        val central = "https://repo1.maven.org/maven2/$path/maven-metadata.xml"
        val portal = "https://plugins.gradle.org/m2/$path/maven-metadata.xml"
        val googleFirst = Regex("^(androidx|com\\.android|com\\.google\\.(android|firebase|gms|mlkit|testing))").containsMatchIn(group)
        val urls = when {
            kind == "plugin" -> if (googleFirst) listOf(google, portal, central) else listOf(portal, central, google)
            googleFirst -> listOf(google, central)
            else -> listOf(central, google)
        }
        for (u in urls) {
            val xml = fetch(u) ?: continue
            val versions = Regex("<version>([^<]+)</version>").findAll(xml).map { it.groupValues[1] }.toList()
            val stable = if (allowPre) versions else versions.filter { !isPre(it) }
            val pick = stable.maxWithOrNull { a, b -> compareVersions(a, b) }
            if (pick != null) return pick
        }
        return null
    }

    // مقارنة إصدارين: الأرقام أولًا، والإصدار النهائي أعلى من التجريبي (1.2.0 > 1.2.0-alpha01)
    fun compareVersions(a: String, b: String): Int {
        val ta = a.split('.', '-', '+', '_')
        val tb = b.split('.', '-', '+', '_')
        for (i in 0 until maxOf(ta.size, tb.size)) {
            val x = ta.getOrNull(i)
            val y = tb.getOrNull(i)
            if (x == null) return if (y!!.toIntOrNull() == null) 1 else -1
            if (y == null) return if (x.toIntOrNull() == null) -1 else 1
            val nx = x.toIntOrNull()
            val ny = y.toIntOrNull()
            val c = if (nx != null && ny != null) nx.compareTo(ny) else x.compareTo(y)
            if (c != 0) return c
        }
        return 0
    }

    private fun level(current: String, latest: String?): Int {
        if (latest == null) return -1
        if (compareVersions(latest, current) <= 0) return 0
        val a = current.split('.', '-').firstOrNull()?.toIntOrNull()
        val b = latest.split('.', '-').firstOrNull()?.toIntOrNull()
        return if (a != null && b != null && a != b) 2 else 1
    }

    // استبدال الإصدار داخل النص الأصلي (الإصدار هو آخر ما يظهر في النص)
    private fun swap(original: String, current: String, latest: String) =
        original.substringBeforeLast(current) + latest + original.substringAfterLast(current)

    // تحديث سطر في ملف Gradle: النسخة القديمة من الملف تذهب للسلة ويُسجَّل للتراجع
    fun apply(ctx: Context, projectPath: String, dir: File, st: DepStatus): Boolean {
        val latest = st.latest ?: return false
        val f = File(dir, st.ref.file)
        val text = f.readText()
        val updated = text.replace(st.ref.original, swap(st.ref.original, st.ref.current, latest))
        if (updated == text) return false
        val id = Trash.put(ctx, f, "replaced")
        f.writeText(updated)
        OpJournal.record(ctx, projectPath, ctx.getString(R.string.op_deps, st.ref.coord), listOf(OpAction("REPLACED", f.absolutePath, id)))
        return true
    }

    fun previewLine(st: DepStatus): String = st.ref.coord + ": " + st.ref.current + " → " + (st.latest ?: "")
}
