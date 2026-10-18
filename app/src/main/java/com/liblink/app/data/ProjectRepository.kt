package com.liblink.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ProjectRepository(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("liblink", Context.MODE_PRIVATE)

    fun projects(): List<Project> {
        val arr = JSONArray(prefs.getString("projects", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Project(
                o.getString("id"), o.getString("name"), o.getString("path"),
                o.optString("remoteUrl"), StorageMode.valueOf(o.getString("mode")),
                o.optLong("updatedAt", 0L), o.optString("pushState", "")
            )
        }
    }

    private fun save(list: List<Project>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("path", it.path)
                .put("remoteUrl", it.remoteUrl).put("mode", it.mode.name)
                .put("updatedAt", it.updatedAt).put("pushState", it.pushState))
        }
        prefs.edit().putString("projects", arr.toString()).apply()
    }

    fun add(name: String, path: String, remoteUrl: String, mode: StorageMode): Project {
        val p = Project(UUID.randomUUID().toString(), name, path, remoteUrl, mode, System.currentTimeMillis())
        save(projects() + p)
        return p
    }

    fun update(p: Project) = save(projects().map { if (it.id == p.id) p else it })

    // تعديلات موجهة بالـ id حتى لا نكتب فوق قيم أحدث
    private fun change(id: String, f: (Project) -> Project) =
        save(projects().map { if (it.id == id) f(it) else it })

    fun setRemote(id: String, url: String) = change(id) { it.copy(remoteUrl = url) }
    fun markUpdated(id: String) = change(id) { it.copy(updatedAt = System.currentTimeMillis()) }
    fun setPushState(id: String, state: String) =
        change(id) { it.copy(pushState = state, updatedAt = System.currentTimeMillis()) }

    // اسم ملف workflow المستخدم للبناء على GitHub لكل مشروع
    fun workflow(id: String): String = prefs.getString("wf_$id", "build.yml") ?: "build.yml"
    fun setWorkflow(id: String, file: String) = prefs.edit().putString("wf_$id", file).apply()

    // إعلانات Start.io التجريبية (تُطبَّق عند إعادة تشغيل التطبيق)
    fun testAds(): Boolean = prefs.getBoolean("test_ads", true)
    fun setTestAds(v: Boolean) = prefs.edit().putBoolean("test_ads", v).apply()

    // ترقيم تلقائي لرسائل الـ Commit: v1 ثم v1.1 ثم v1.2 ...
    private fun commitCount(id: String) = prefs.getInt("ver_$id", 0)
    fun versionLabel(id: String): String = commitCount(id).let { if (it == 0) "v1" else "v1.$it" }
    fun bumpVersion(id: String) = prefs.edit().putInt("ver_$id", commitCount(id) + 1).apply()

    fun remove(id: String) = save(projects().filterNot { it.id == id })

    fun find(id: String) = projects().firstOrNull { it.id == id }

    // بيانات GitHub (محفوظة محليًا داخل التطبيق)
    fun credentials() = Credentials(
        prefs.getString("user", "") ?: "",
        prefs.getString("token", "") ?: "",
        prefs.getString("authorName", "") ?: "",
        prefs.getString("authorEmail", "") ?: ""
    )

    fun saveCredentials(c: Credentials) {
        prefs.edit().putString("user", c.user).putString("token", c.token)
            .putString("authorName", c.authorName).putString("authorEmail", c.authorEmail).apply()
    }
}
