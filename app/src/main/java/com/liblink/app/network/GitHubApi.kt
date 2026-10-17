package com.liblink.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class GitHubException(val code: Int, message: String) : Exception(message)

// عميل بسيط لـ GitHub Actions عبر REST (بدون مكتبات إضافية)
class GitHubApi(private val token: String) {

    data class Run(
        val id: Long, val number: Int, val status: String, val conclusion: String?,
        val url: String, val branch: String, val createdAt: String
    )

    data class Artifact(val id: Long, val name: String, val size: Long, val expired: Boolean, val downloadUrl: String)

    private fun open(url: String, method: String = "GET", auth: Boolean = true): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.instanceFollowRedirects = false // نتعامل مع التحويل يدويًا حتى لا يُرسل الـ Token لخادم آخر
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        c.setRequestProperty("User-Agent", "LibLink")
        if (auth) c.setRequestProperty("Authorization", "Bearer $token")
        return c
    }

    private fun check(c: HttpURLConnection) {
        val code = c.responseCode
        if (code in 200..299 || code == 302) return
        val body = runCatching { c.errorStream?.bufferedReader()?.readText() ?: "" }.getOrDefault("")
        val msg = runCatching { JSONObject(body).optString("message") }.getOrDefault("")
        throw GitHubException(code, msg)
    }

    private fun getJson(url: String): JSONObject {
        val c = open(url)
        try {
            check(c)
            return JSONObject(c.inputStream.bufferedReader().readText())
        } finally { c.disconnect() }
    }

    // تشغيل الـ workflow يدويًا (يتطلب workflow_dispatch وصلاحية workflow للـ Token)
    suspend fun dispatch(owner: String, repo: String, workflow: String, ref: String) = withContext(Dispatchers.IO) {
        val c = open("https://api.github.com/repos/$owner/$repo/actions/workflows/$workflow/dispatches", "POST")
        try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(JSONObject().put("ref", ref).toString().toByteArray()) }
            check(c)
        } finally { c.disconnect() }
    }

    suspend fun runs(owner: String, repo: String, workflow: String): List<Run> = withContext(Dispatchers.IO) {
        val j = getJson("https://api.github.com/repos/$owner/$repo/actions/workflows/$workflow/runs?per_page=5")
        val arr = j.optJSONArray("workflow_runs") ?: return@withContext emptyList()
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Run(
                o.getLong("id"), o.optInt("run_number"), o.optString("status"),
                if (o.isNull("conclusion")) null else o.optString("conclusion"),
                o.optString("html_url"), o.optString("head_branch"), o.optString("created_at")
            )
        }
    }

    suspend fun artifacts(owner: String, repo: String, runId: Long): List<Artifact> = withContext(Dispatchers.IO) {
        val j = getJson("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/artifacts")
        val arr = j.optJSONArray("artifacts") ?: return@withContext emptyList()
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Artifact(o.getLong("id"), o.optString("name"), o.optLong("size_in_bytes"),
                o.optBoolean("expired"), o.optString("archive_download_url"))
        }
    }

    // تنزيل ملف الـ artifact (zip) ثم استخراج أول APK منه
    suspend fun downloadApk(art: Artifact, zipFile: File, apkFile: File): File = withContext(Dispatchers.IO) {
        var c = open(art.downloadUrl)
        try {
            check(c)
            val stream = if (c.responseCode == 302) {
                val loc = c.getHeaderField("Location") ?: throw GitHubException(0, "no redirect")
                c.disconnect()
                c = open(loc, auth = false) // الرابط الموقّع لا يحتاج Token
                check(c)
                c.inputStream
            } else c.inputStream
            stream.use { input -> zipFile.outputStream().use { input.copyTo(it) } }
        } finally { c.disconnect() }

        var found = false
        ZipInputStream(zipFile.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (!e.isDirectory && e.name.endsWith(".apk")) {
                    apkFile.outputStream().use { z.copyTo(it) }
                    found = true
                    break
                }
            }
        }
        zipFile.delete()
        if (!found) throw GitHubException(0, "NO_APK")
        apkFile
    }

    companion object {
        // https://github.com/user/repo.git أو git@github.com:user/repo.git ← (user, repo)
        fun parse(url: String): Pair<String, String>? {
            val m = Regex("github\\.com[:/]+([^/\\s]+)/([^/\\s]+?)(?:\\.git)?/?$").find(url.trim()) ?: return null
            return m.groupValues[1] to m.groupValues[2]
        }
    }
}
