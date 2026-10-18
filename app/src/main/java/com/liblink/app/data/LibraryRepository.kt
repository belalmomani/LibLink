package com.liblink.app.data

import android.content.Context
import android.os.Environment
import com.liblink.app.network.GitHubApi
import com.liblink.app.storage.ProjectStorage
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
    val addedAt: Long
)

object LibCategories {
    // التصنيفات الافتراضية (تُترجم أسماؤها في الواجهة)
    val defaults = listOf("android", "java", "python", "ui", "web", "other")
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

// مكتبة محلية + نسخة احتياطية في Download/LibLink تبقى بعد حذف التطبيق
class LibraryRepository(context: Context) {
    private val ctx = context.applicationContext
    private val prefs = ctx.getSharedPreferences("liblink_library", Context.MODE_PRIVATE)

    fun items(): List<LibItem> = parse(JSONArray(prefs.getString("items", "[]")))

    private fun parse(arr: JSONArray): List<LibItem> = (0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        LibItem(
            o.optString("id").ifBlank { UUID.randomUUID().toString() }, o.optString("name"), o.optString("url"),
            o.optString("category", "other"), o.optString("note"),
            o.optBoolean("favorite"), o.optLong("addedAt")
        )
    }

    private fun toJson(list: List<LibItem>): JSONArray {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)
                .put("category", it.category).put("note", it.note)
                .put("favorite", it.favorite).put("addedAt", it.addedAt))
        }
        return arr
    }

    private fun save(list: List<LibItem>) {
        prefs.edit().putString("items", toJson(list).toString()).apply()
        writeBackup()
    }

    fun add(name: String, url: String, category: String, note: String): LibItem {
        val item = LibItem(UUID.randomUUID().toString(), name, url, category, note, false, System.currentTimeMillis())
        save(items() + item)
        return item
    }

    fun update(item: LibItem) = save(items().map { if (it.id == item.id) item else it })
    fun remove(id: String) = save(items().filterNot { it.id == id })
    fun toggleFavorite(id: String) = save(items().map { if (it.id == id) it.copy(favorite = !it.favorite) else it })
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
    }

    fun categories(): List<String> = LibCategories.defaults + customCategories()

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
            f.writeText(JSONObject().put("version", 1).put("items", toJson(items()))
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
            parse(o.optJSONArray("items") ?: JSONArray()) to cats
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
}
