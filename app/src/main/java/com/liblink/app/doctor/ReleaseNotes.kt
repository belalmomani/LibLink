package com.liblink.app.doctor

import android.content.Context
import com.liblink.app.R
import com.liblink.app.network.GitHubApi
import com.liblink.app.storage.ReleaseRecord
import com.liblink.app.storage.ReleaseStore

// ملاحظات الإصدار التلقائية: ما تغيّر منذ الإصدار الناجح السابق (رسائل الـ commits وأسماء الملفات)
object ReleaseNotes {

    // يولّد الملاحظات للإصدارات الناجحة التي ليس لها ملاحظات بعد
    suspend fun ensure(
        ctx: Context, api: GitHubApi, owner: String, repo: String,
        store: ReleaseStore, projectId: String, limit: Int = 6
    ) {
        val all = store.list(projectId) // الأحدث أولًا
        var made = 0
        for ((i, rec) in all.withIndex()) {
            if (!rec.success || rec.notes.isNotBlank() || rec.fullSha.isBlank()) continue
            if (made >= limit) break
            val prev = all.drop(i + 1).firstOrNull { it.success && it.fullSha.isNotBlank() }
            val text = try { build(ctx, api, owner, repo, rec, prev) } catch (_: Exception) { continue }
            store.update(projectId, rec.runId) { it.copy(notes = text) }
            made++
        }
    }

    private suspend fun build(
        ctx: Context, api: GitHubApi, owner: String, repo: String, rec: ReleaseRecord, prev: ReleaseRecord?
    ): String {
        val sb = StringBuilder()
        if (prev == null) {
            sb.append("v").append(rec.versionName).append(" · ").append(ctx.getString(R.string.rn_first))
            if (rec.message.isNotBlank()) sb.append("\n• ").append(rec.message)
            return sb.toString()
        }
        val cmp = api.compare(owner, repo, prev.fullSha, rec.fullSha)
        sb.append(ctx.getString(R.string.rn_header, rec.versionName, prev.versionName))
        if (cmp.commits.isEmpty() && cmp.files.isEmpty()) return sb.append("\n").append(ctx.getString(R.string.rn_none)).toString()
        if (cmp.commits.isNotEmpty()) {
            sb.append("\n\n").append(ctx.getString(R.string.rn_commits, cmp.totalCommits))
            cmp.commits.take(15).forEach { sb.append("\n• ").append(it) }
            if (cmp.commits.size > 15) sb.append("\n…")
        }
        if (cmp.files.isNotEmpty()) {
            val added = cmp.files.count { it.status == "added" }
            val removed = cmp.files.count { it.status == "removed" }
            val modified = cmp.files.size - added - removed
            sb.append("\n\n").append(ctx.getString(R.string.rn_files, added, modified, removed))
            cmp.files.take(12).forEach {
                val mark = when (it.status) { "added" -> "+"; "removed" -> "-"; else -> "~" }
                sb.append("\n").append(mark).append(" ").append(it.name).append(" (+").append(it.add).append("/-").append(it.del).append(")")
            }
            if (cmp.files.size > 12) sb.append("\n…")
        }
        return sb.toString()
    }
}
