package com.liblink.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
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

// مكتبة محلية: تُحفظ داخل التطبيق فقط
class LibraryRepository(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("liblink_library", Context.MODE_PRIVATE)

    fun items(): List<LibItem> {
        val arr = JSONArray(prefs.getString("items", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            LibItem(
                o.getString("id"), o.optString("name"), o.optString("url"),
                o.optString("category", "other"), o.optString("note"),
                o.optBoolean("favorite"), o.optLong("addedAt")
            )
        }
    }

    private fun save(list: List<LibItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)
                .put("category", it.category).put("note", it.note)
                .put("favorite", it.favorite).put("addedAt", it.addedAt))
        }
        prefs.edit().putString("items", arr.toString()).apply()
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
    }

    fun categories(): List<String> = LibCategories.defaults + customCategories()
}
