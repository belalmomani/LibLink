package com.liblink.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.liblink.app.sync.SyncManager
import java.util.UUID

class ProjectRepository(context: Context) {
    private val appCtx = context.applicationContext
    private val prefs = appCtx.getSharedPreferences("liblink", Context.MODE_PRIVATE)

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
    fun setTestAds(v: Boolean) { prefs.edit().putBoolean("test_ads", v).apply(); settingsChanged() }

    // ترقيم تلقائي لرسائل الـ Commit: v1 ثم v1.1 ثم v1.2 ...
    private fun commitCount(id: String) = prefs.getInt("ver_$id", 0)
    fun versionLabel(id: String): String = commitCount(id).let { if (it == 0) "v1" else "v1.$it" }
    fun bumpVersion(id: String) = prefs.edit().putInt("ver_$id", commitCount(id) + 1).apply()

    // ---------------- إعدادات المزامنة السحابية ----------------
    fun settingsUpdatedAt() = prefs.getLong("settings_at", 0L)

    private fun settingsChanged() {
        prefs.edit().putLong("settings_at", System.currentTimeMillis()).apply()
        SyncManager.onLocalChange(appCtx)
    }

    fun language() = prefs.getString("lang", "") ?: ""
    fun setLanguage(l: String) { prefs.edit().putString("lang", l).apply(); settingsChanged() }

    // لا تبدأ المزامنة التلقائية قبل أن يراجع المستخدم المعاينة ويوافق (مرة لكل تسجيل دخول)
    fun syncApproved() = prefs.getBoolean("sync_approved", false)
    fun setSyncApproved(v: Boolean) = prefs.edit().putBoolean("sync_approved", v).apply()

    private fun savePreSyncSettings() {
        val o = JSONObject().put("user", prefs.getString("user", "") ?: "").put("authorName", prefs.getString("authorName", "") ?: "")
            .put("authorEmail", prefs.getString("authorEmail", "") ?: "").put("token", prefs.getString("token", "") ?: "")
            .put("testAds", prefs.getBoolean("test_ads", true)).put("lang", prefs.getString("lang", "") ?: "")
        prefs.edit().putString("presync_settings", o.toString()).apply()
    }

    // التراجع عن إعدادات استبدلتها المزامنة (تُعتبر تعديلًا جديدًا فتتغلب على السحابة)
    fun undoPreSyncSettings(): Boolean {
        val raw = prefs.getString("presync_settings", null) ?: return false
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        prefs.edit().putString("user", o.optString("user")).putString("authorName", o.optString("authorName"))
            .putString("authorEmail", o.optString("authorEmail")).putString("token", o.optString("token"))
            .putBoolean("test_ads", o.optBoolean("testAds", true)).putString("lang", o.optString("lang"))
            .remove("presync_settings").apply()
        settingsChanged()
        return true
    }

    fun syncAuto() = prefs.getBoolean("sync_auto", true)
    fun setSyncAuto(v: Boolean) = prefs.edit().putBoolean("sync_auto", v).apply()

    fun tokenSync() = prefs.getBoolean("sync_token", false)
    fun setTokenSync(v: Boolean) { prefs.edit().putBoolean("sync_token", v).apply(); settingsChanged() }

    fun syncPassphrase() = prefs.getString("sync_pass", "") ?: ""
    fun setSyncPassphrase(p: String) = prefs.edit().putString("sync_pass", p).apply()

    fun lastSync() = prefs.getLong("last_sync", 0L)
    fun setLastSync(t: Long) = prefs.edit().putLong("last_sync", t).apply()

    // تطبيق إعدادات جاءت من جهاز آخر (لا تُطلق مزامنة جديدة)
    fun applyRemoteSettings(user: String, name: String, email: String, testAds: Boolean, lang: String, token: String?, at: Long) {
        savePreSyncSettings()
        val e = prefs.edit().putString("user", user).putString("authorName", name).putString("authorEmail", email)
            .putBoolean("test_ads", testAds).putString("lang", lang).putLong("settings_at", at)
        if (token != null) e.putString("token", token)
        e.apply()
    }

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
        settingsChanged()
    }
}
