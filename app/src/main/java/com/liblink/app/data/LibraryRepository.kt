package com.liblink.app.data

import android.content.Context
import android.os.Environment
import com.liblink.app.network.GitHubApi
import com.liblink.app.storage.ProjectStorage
import com.liblink.app.sync.SyncManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

// عنصر في المكتبة الشخصية: مستودع أو مكتبة أو سطر dependency
data class LibItem(
    val id: String,
    val name: String,
    val url: String,        // رابط (GitHub/موقع) أو نص dependency
    val category: String,   // مفتاح تصنيف افتراضي أو اسم تصنيف مخصص
    val note: String,
    val favorite: Boolean,
    val addedAt: Long,
    val updatedAt: Long = addedAt   // آخر تعديل: أساس الدمج بين الأجهزة
)

object LibCategories {
    // التصنيفات الافتراضية (تُترجم أسماؤها في الواجهة)
    val defaults = listOf("android", "java", "python", "ui", "web", "other")
}

// لقطة من المكتبة للمزامنة: العناصر + شواهد الحذف + التصنيفات المخصصة
data class LibSnapshot(val items: List<LibItem>, val tombs: Map<String, Long>, val categories: List<String>)

// مفتاح العنصر عند الدمج: الرابط بعد التطبيع
fun libKey(url: String) = url.trim().lowercase().trimEnd('/')

// دمج مكتبتين من جهازين: الأحدث تعديلًا يفوز، والحذف يُحترم ما لم يُعدَّل العنصر بعده
fun mergeLibrary(a: LibSnapshot, b: LibSnapshot): LibSnapshot {
    val best = LinkedHashMap<String, LibItem>()
    for (i in a.items + b.items) {
        val k = libKey(i.url)
        val cur = best[k]
        if (cur == null || i.updatedAt > cur.updatedAt) best[k] = i
    }
    val tombs = HashMap<String, Long>()
    for (m in listOf(a.tombs, b.tombs)) for ((k, t) in m) tombs[k] = maxOf(tombs[k] ?: 0L, t)
    val keep = best.values.filter { it.updatedAt > (tombs[libKey(it.url)] ?: 0L) }
    val keptKeys = keep.map { libKey(it.url) }.toSet()
    val cutoff = System.currentTimeMillis() - 90L * 24 * 3600 * 1000 // تُنظَّف الشواهد بعد 90 يومًا
    val tombsOut = tombs.filter { (k, t) -> k !in keptKeys && t > cutoff }.toSortedMap()
    return LibSnapshot(
        keep.sortedWith(compareBy({ it.addedAt }, { libKey(it.url) })),
        tombsOut,
        (a.categories + b.categories).distinct().sorted()
    )
}

// اسم مقترح من آخر الرابط: github.com/user/repo.git ← repo، و group:artifact:1.0 ← artifact
fun suggestLibName(input: String): String {
    val t = input.trim()
    if (t.isEmpty()) return ""
    GitHubApi.parse(t)?.let { return it.second }
    Regex("([\\w.-]+):([\\w.-]+):([\\w.+-]+)").find(t)?.let { return it.groupValues[2] }
    return t.substringBefore('?').substringBefore('#').trimEnd('/')
        .substringAfterLast('/').removeSuffix(".git").ifBlank { t }
}

// مكتبة محلية + نسخة احتياطية في Download/LibLink + مزامنة سحابية
class LibraryRepository(context: Context) {
    private val ctx = context.applicationContext
    private val prefs = ctx.getSharedPreferences("liblink_library", Context.MODE_PRIVATE)

    fun items(): List<LibItem> = itemsFromJson(JSONArray(prefs.getString("items", "[]")))
    fun tombs(): Map<String, Long> = tombsFromJson(JSONArray(prefs.getString("tombs", "[]")))

    private fun save(list: List<LibItem>) {
        prefs.edit().putString("items", itemsToJson(list).toString()).apply()
        writeBackup()
        SyncManager.onLocalChange(ctx)
    }

    private fun saveTombs(m: Map<String, Long>) {
        prefs.edit().putString("tombs", tombsToJson(m).toString()).apply()
    }

    fun add(name: String, url: String, category: String, note: String): LibItem {
        val now = System.currentTimeMillis()
        val item = LibItem(UUID.randomUUID().toString(), name, url, category, note, false, now, now)
        saveTombs(tombs() - libKey(url)) // إعادة إضافة رابط محذوف سابقًا تلغي شاهد الحذف
        save(items() + item)
        return item
    }

    fun update(item: LibItem) {
        val old = items().firstOrNull { it.id == item.id }
        val now = System.currentTimeMillis()
        // تغيير الرابط يُعامل كحذف القديم وإضافة الجديد عند الدمج
        if (old != null && libKey(old.url) != libKey(item.url)) saveTombs(tombs() + (libKey(old.url) to now))
        save(items().map { if (it.id == item.id) item.copy(updatedAt = now) else it })
    }

    fun remove(id: String) {
        val victim = items().firstOrNull { it.id == id } ?: return
        saveTombs(tombs() + (libKey(victim.url) to System.currentTimeMillis()))
        save(items().filterNot { it.id == id })
    }

    fun toggleFavorite(id: String) = save(items().map {
        if (it.id == id) it.copy(favorite = !it.favorite, updatedAt = System.currentTimeMillis()) else it
    })

    fun exists(url: String) = items().any { it.url.trim().equals(url.trim(), ignoreCase = true) }

    // تصنيفات مخصصة يضيفها المستخدم
    fun customCategories(): List<String> {
        val arr = JSONArray(prefs.getString("categories", "[]"))
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun addCategory(name: String) {
        val n = name.trim()
        if (n.isEmpty() || n in LibCategories.defaults || n in customCategories()) return
        val arr = JSONArray()
        (customCategories() + n).forEach { arr.put(it) }
        prefs.edit().putString("categories", arr.toString()).apply()
        writeBackup()
        SyncManager.onLocalChange(ctx)
    }

    fun categories(): List<String> = LibCategories.defaults + customCategories()

    // ---------------- المزامنة بين الأجهزة ----------------

    fun snapshot() = LibSnapshot(items(), tombs(), customCategories())

    // تطبيق نتيجة الدمج محليًا (لا تُطلق مزامنة جديدة)
    fun replaceAll(s: LibSnapshot) {
        prefs.edit()
            .putString("items", itemsToJson(s.items).toString())
            .putString("tombs", tombsToJson(s.tombs).toString())
            .putString("categories", JSONArray(s.categories.filter { it !in LibCategories.defaults }).toString())
            .apply()
        writeBackup()
    }

    // نسخة من المكتبة قبل أي دمج يغيّرها، للتراجع عنه إن لم يعجبك
    fun savePreSync() {
        val snap = snapshot()
        prefs.edit().putString("presync", JSONObject().put("at", System.currentTimeMillis())
            .put("items", itemsToJson(snap.items)).put("categories", JSONArray(snap.categories)).toString()).apply()
    }

    fun preSyncAt(): Long = prefs.getString("presync", null)?.let { runCatching { JSONObject(it).getLong("at") }.getOrNull() } ?: 0L

    // استرجاع العناصر التي أزالها الدمج؛ تُعطى وقت تعديل جديدًا لتتغلب على شواهد الحذف في السحابة
    fun undoLastSync(): Int {
        val o = prefs.getString("presync", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return 0
        val old = itemsFromJson(o.optJSONArray("items") ?: JSONArray())
        val cur = items()
        val have = cur.map { libKey(it.url) }.toSet()
        val now = System.currentTimeMillis()
        val missing = old.filter { libKey(it.url) !in have }.map { it.copy(updatedAt = now) }
        val missingKeys = missing.map { libKey(it.url) }.toSet()
        saveTombs(tombs().filterKeys { it !in missingKeys })
        o.optJSONArray("categories")?.let { a -> (0 until a.length()).forEach { addCategory(a.getString(it)) } }
        prefs.edit().remove("presync").apply()
        save(cur + missing)
        return missing.size
    }

    // ---------------- النسخة الاحتياطية ----------------

    fun backupFile(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "LibLink/liblink-library.json"
    )

    // حالة قرار المستخدم بخصوص نسخة قديمة (استعادة أو تجاهل)
    fun backupHandled() = prefs.getBoolean("backup_handled", false)
    fun markBackupHandled() = prefs.edit().putBoolean("backup_handled", true).apply()
    fun restoreAsked() = prefs.getBoolean("restore_asked", false)
    fun setRestoreAsked() = prefs.edit().putBoolean("restore_asked", true).apply()

    // كتابة النسخة الاحتياطية. لا نكتب فوق نسخة قديمة قبل أن يقرر المستخدم استعادتها
    fun writeBackup() {
        if (!ProjectStorage.hasAllFilesAccess(ctx)) return
        try {
            val f = backupFile()
            if (!backupHandled() && f.exists()) return
            f.parentFile?.mkdirs()
            f.writeText(JSONObject().put("version", 1).put("items", itemsToJson(items()))
                .put("categories", JSONArray(customCategories())).toString())
            markBackupHandled()
        } catch (_: Exception) { }
    }

    private fun readBackup(): Pair<List<LibItem>, List<String>>? {
        if (!ProjectStorage.hasAllFilesAccess(ctx)) return null
        return try {
            val f = backupFile()
            if (!f.exists()) return null
            val o = JSONObject(f.readText())
            val cats = o.optJSONArray("categories")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            itemsFromJson(o.optJSONArray("items") ?: JSONArray()) to cats
        } catch (_: Exception) { null }
    }

    // عدد العناصر الموجودة في النسخة الاحتياطية وغير الموجودة محليًا
    fun backupMissingCount(): Int {
        val b = readBackup() ?: return 0
        val have = items().map { it.url.trim().lowercase() }.toSet()
        return b.first.count { it.url.trim().lowercase() !in have }
    }

    // استعادة العناصر الناقصة بأسمائها وروابطها وتصنيفاتها ومفضلتها
    fun restoreFromBackup(): Int {
        val b = readBackup() ?: return 0
        val have = items().map { it.url.trim().lowercase() }.toSet()
        val missing = b.first.filter { it.url.trim().lowercase() !in have }
        b.second.forEach { addCategory(it) }
        markBackupHandled()
        save(items() + missing)
        return missing.size
    }

    companion object {
        fun itemsFromJson(arr: JSONArray): List<LibItem> = (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val added = o.optLong("addedAt")
            LibItem(
                o.optString("id").ifBlank { UUID.randomUUID().toString() }, o.optString("name"), o.optString("url"),
                o.optString("category", "other"), o.optString("note"),
                o.optBoolean("favorite"), added, o.optLong("updatedAt", added)
            )
        }

        // ترتيب ثابت ليكون ناتج الـ JSON متطابقًا بين الأجهزة
        fun itemsToJson(list: List<LibItem>): JSONArray {
            val arr = JSONArray()
            list.sortedWith(compareBy({ it.addedAt }, { libKey(it.url) })).forEach {
                arr.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)
                    .put("category", it.category).put("note", it.note).put("favorite", it.favorite)
                    .put("addedAt", it.addedAt).put("updatedAt", it.updatedAt))
            }
            return arr
        }

        fun tombsFromJson(arr: JSONArray): Map<String, Long> =
            (0 until arr.length()).associate { val o = arr.getJSONObject(it); o.getString("k") to o.getLong("at") }

        fun tombsToJson(m: Map<String, Long>): JSONArray {
            val arr = JSONArray()
            m.toSortedMap().forEach { (k, t) -> arr.put(JSONObject().put("k", k).put("at", t)) }
            return arr
        }
    }
}
