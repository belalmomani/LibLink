package com.liblink.app.storage

import android.content.Context
import com.liblink.app.network.GitHubApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

// سجل إصدار: رقم النسخة والـ Commit الذي أنتجها وحجم الـ APK وحالة البناء
data class ReleaseRecord(
    val runId: Long,
    val number: Int,          // رقم البناء = versionCode
    val versionName: String,  // 1.0.N
    val sha: String,
    val message: String,
    val sizeBytes: Long,      // حجم ملف الـ artifact (يقارب حجم APK)
    val artifactId: Long,
    val createdAt: String,
    val success: Boolean,
    val runUrl: String,
    val apkPath: String,      // مسار الـ APK بعد تنزيله
    val failReason: String    // سبب الفشل بعد التشخيص
)

fun formatSize(bytes: Long): String =
    if (bytes >= 1_048_576) String.format(Locale.US, "%.2f MB", bytes / 1_048_576.0)
    else String.format(Locale.US, "%.0f KB", bytes / 1024.0)

// الفرق عن النسخة السابقة: +120 KB (+1.9%)
fun formatDelta(size: Long, prev: Long): String {
    val d = size - prev
    val sign = if (d > 0) "+" else if (d < 0) "-" else "±"
    val pct = if (prev > 0) abs(d) * 100.0 / prev else 0.0
    return String.format(Locale.US, "%s%s (%s%.1f%%)", sign, formatSize(abs(d)), sign, pct)
}

// يحفظ سجل الإصدارات محليًا لكل مشروع حتى بعد انتهاء صلاحية الـ artifacts على GitHub
class ReleaseStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("liblink_releases", Context.MODE_PRIVATE)

    fun list(projectId: String): List<ReleaseRecord> {
        val arr = JSONArray(prefs.getString("rel_$projectId", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            ReleaseRecord(
                o.optLong("runId"), o.optInt("number"), o.optString("versionName"), o.optString("sha"),
                o.optString("message"), o.optLong("sizeBytes"), o.optLong("artifactId"), o.optString("createdAt"),
                o.optBoolean("success"), o.optString("runUrl"), o.optString("apkPath"), o.optString("failReason")
            )
        }.sortedByDescending { it.number }
    }

    private fun save(projectId: String, list: List<ReleaseRecord>) {
        val arr = JSONArray()
        list.sortedByDescending { it.number }.take(60).forEach {
            arr.put(JSONObject().put("runId", it.runId).put("number", it.number).put("versionName", it.versionName)
                .put("sha", it.sha).put("message", it.message).put("sizeBytes", it.sizeBytes)
                .put("artifactId", it.artifactId).put("createdAt", it.createdAt).put("success", it.success)
                .put("runUrl", it.runUrl).put("apkPath", it.apkPath).put("failReason", it.failReason))
        }
        prefs.edit().putString("rel_$projectId", arr.toString()).apply()
    }

    // تسجيل عملية بناء منتهية (مع الحفاظ على الحجم ومسار APK وسبب الفشل المحفوظة سابقًا)
    fun upsertRun(projectId: String, r: GitHubApi.Run) {
        if (r.status != "completed") return
        val old = list(projectId).firstOrNull { it.runId == r.id }
        val rec = ReleaseRecord(
            r.id, r.number, "1.0.${r.number}", r.sha.take(7), r.message,
            old?.sizeBytes ?: 0L, old?.artifactId ?: 0L, r.createdAt, r.conclusion == "success",
            r.url, old?.apkPath ?: "", old?.failReason ?: ""
        )
        save(projectId, list(projectId).filterNot { it.runId == r.id } + rec)
    }

    fun update(projectId: String, runId: Long, f: (ReleaseRecord) -> ReleaseRecord) {
        save(projectId, list(projectId).map { if (it.runId == runId) f(it) else it })
    }
}
