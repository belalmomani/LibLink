package com.liblink.app.doctor

import android.content.Context
import com.liblink.app.R
import com.liblink.app.data.Credentials
import com.liblink.app.data.Project
import com.liblink.app.data.ProjectRepository
import com.liblink.app.git.GitManager
import com.liblink.app.network.GitHubApi
import com.liblink.app.storage.Templates
import com.liblink.app.storage.Trash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.util.io.NullOutputStream
import java.io.File

// الأهم أولًا
enum class Severity { ERROR, WARN, INFO, OK }

enum class FixKind {
    REPAIR_REPO, RESET_GIT, REMOVE_LOCK, SET_REMOTE, SYNC_APP_REMOTE, FIX_REFSPEC,
    COMMIT_ALL, PUSH, PULL, FILES
}

// إصلاح مقترح: معاينة لما سيتغير قبل التنفيذ
data class Fix(
    val kind: FixKind,
    val label: String,
    val preview: List<String>,
    val arg: String = "",
    val items: List<Pair<String, String>> = emptyList()   // (مسار، مصدر: HEAD أو origin/x أو template:key)
)

data class Finding(val severity: Severity, val title: String, val detail: String, val fix: Fix? = null)

private class Reporter(val ctx: Context) {
    val out = mutableListOf<Finding>()
    fun s(id: Int, vararg a: Any): String = ctx.getString(id, *a)
    fun add(sev: Severity, title: String, detail: String, fix: Fix? = null) { out.add(Finding(sev, title, detail, fix)) }
}

// مركز التشخيص: يفحص المشروع ويقدّم تشخيصًا مفهومًا وخطة إنقاذ بدل رسائل الخطأ الغامضة
object Doctor {

    suspend fun diagnose(
        ctx: Context, project: Project, creds: Credentials, nextCommitMsg: String
    ): List<Finding> = withContext(Dispatchers.IO) {
        val r = Reporter(ctx)
        val dir = File(project.path)
        val appUrl = project.remoteUrl.trim()

        // 1) مجلد المشروع
        if (!dir.exists()) {
            r.add(Severity.ERROR, r.s(R.string.doc_folder_missing_t), r.s(R.string.doc_folder_missing_d, project.path),
                if (appUrl.isNotBlank()) Fix(FixKind.REPAIR_REPO, r.s(R.string.doc_fix_reclone),
                    listOf("+ " + r.s(R.string.pv_clone, appUrl), "+ " + project.path), appUrl) else null)
            return@withContext sorted(r.out)
        }

        // 2) بيانات .git
        val gitDir = File(dir, ".git")
        if (!gitDir.exists()) {
            val pv = mutableListOf("+ " + r.s(R.string.pv_git_new))
            if (appUrl.isNotBlank()) {
                pv += "+ " + r.s(R.string.pv_origin, appUrl)
                pv += "~ " + r.s(R.string.pv_vs_github)
            } else pv += "+ " + r.s(R.string.pv_empty_repo)
            r.add(Severity.ERROR, r.s(R.string.doc_nogit_t), r.s(R.string.doc_nogit_d),
                Fix(FixKind.REPAIR_REPO, r.s(R.string.doc_fix_repair), pv, appUrl))
            androidChecks(r, project, dir, null, "", null, null)
            return@withContext sorted(r.out)
        }

        // 3) ملف قفل عالق
        if (File(gitDir, "index.lock").exists()) {
            r.add(Severity.ERROR, r.s(R.string.doc_lock_t), r.s(R.string.doc_lock_d),
                Fix(FixKind.REMOVE_LOCK, r.s(R.string.doc_fix_lock), listOf("- " + r.s(R.string.pv_lock, ".git/index.lock"))))
        }

        // 4) هل يفتح المستودع؟
        val git = try { Git.open(dir) } catch (e: Exception) {
            val pv = mutableListOf("~ " + r.s(R.string.pv_trash_git), "+ " + r.s(R.string.pv_git_new))
            if (appUrl.isNotBlank()) pv += "+ " + r.s(R.string.pv_origin, appUrl)
            r.add(Severity.ERROR, r.s(R.string.doc_corrupt_t), r.s(R.string.doc_corrupt_d, e.message ?: e.toString()),
                Fix(FixKind.RESET_GIT, r.s(R.string.doc_fix_reset_git), pv, appUrl))
            return@withContext sorted(r.out)
        }
        try {
            inspect(r, project, creds, git, dir, appUrl, nextCommitMsg)
        } catch (e: Exception) {
            r.add(Severity.ERROR, r.s(R.string.doc_corrupt_t), r.s(R.string.doc_corrupt_d, e.message ?: e.toString()))
        } finally { git.close() }

        if (r.out.none { it.severity == Severity.ERROR || it.severity == Severity.WARN } &&
            r.out.none { it.severity == Severity.OK }) {
            r.add(Severity.OK, r.s(R.string.doc_healthy_t), "")
        }
        sorted(r.out)
    }

    private fun sorted(l: List<Finding>) = l.sortedBy { it.severity.ordinal }

    private fun friendly(ctx: Context, e: Exception): String {
        val m = e.message ?: e.toString()
        return when {
            m.contains("not authorized", true) || m.contains("Authentication", true) ||
                m.contains("401") || m.contains("403") -> ctx.getString(R.string.doc_err_auth)
            m.contains("UnknownHost", true) || m.contains("timed out", true) || m.contains("Connection", true) ||
                m.contains("connect", true) -> ctx.getString(R.string.doc_err_network)
            m.contains("not found", true) -> ctx.getString(R.string.doc_err_notfound)
            else -> m
        }
    }

    private fun limited(r: Reporter, lines: List<String>, max: Int = 12): List<String> =
        if (lines.size <= max) lines else lines.take(max) + r.s(R.string.pv_more, lines.size - max)

    private fun mark(e: DiffEntry): String = when (e.changeType) {
        DiffEntry.ChangeType.ADD -> "+ " + e.newPath
        DiffEntry.ChangeType.DELETE -> "- " + e.oldPath
        else -> "~ " + e.newPath
    }

    // فروقات الملفات بين commit قديم وآخر جديد (بدون المرور على مجلد العمل)
    private fun treeDiff(repo: Repository, oldId: ObjectId, newId: ObjectId): List<DiffEntry> {
        val a = repo.resolve(oldId.name + "^{tree}") ?: return emptyList()
        val b = repo.resolve(newId.name + "^{tree}") ?: return emptyList()
        return repo.newObjectReader().use { reader ->
            DiffFormatter(NullOutputStream.INSTANCE).use { df ->
                df.setRepository(repo)
                df.scan(CanonicalTreeParser(null, reader, a), CanonicalTreeParser(null, reader, b))
            }
        }
    }

    private fun inspect(
        r: Reporter, project: Project, creds: Credentials, g: Git, dir: File, appUrl: String, nextCommitMsg: String
    ) {
        val repo = g.repository
        val cfg = repo.config
        val gitUrl = cfg.getString("remote", "origin", "url")?.trim().orEmpty()
        val branch = repo.branch ?: ""
        val head = repo.resolve("HEAD")
        val provider = if (creds.token.isBlank()) null
        else UsernamePasswordCredentialsProvider(creds.user.ifBlank { "token" }, creds.token)
        val parsedGit = if (gitUrl.isBlank()) null else GitHubApi.parse(gitUrl)
        val appParsed = if (appUrl.isBlank()) null else GitHubApi.parse(appUrl)
        var remoteOk = false

        // ---- إعدادات Remote
        when {
            gitUrl.isBlank() -> r.add(Severity.ERROR, r.s(R.string.doc_remote_missing_t), r.s(R.string.doc_remote_missing_d),
                if (appParsed != null) Fix(FixKind.SET_REMOTE, r.s(R.string.doc_fix_set_remote),
                    listOf("+ " + r.s(R.string.pv_origin, appUrl)), appUrl) else null)
            parsedGit == null -> r.add(Severity.ERROR, r.s(R.string.doc_remote_invalid_t), r.s(R.string.doc_remote_invalid_d, gitUrl),
                if (appParsed != null) Fix(FixKind.SET_REMOTE, r.s(R.string.doc_fix_set_remote),
                    listOf("~ " + r.s(R.string.pv_origin_change, gitUrl, appUrl)), appUrl) else null)
            gitUrl.startsWith("git@") || gitUrl.startsWith("ssh://") -> {
                val https = "https://github.com/${parsedGit.first}/${parsedGit.second}.git"
                r.add(Severity.WARN, r.s(R.string.doc_remote_ssh_t), r.s(R.string.doc_remote_ssh_d, gitUrl),
                    Fix(FixKind.SET_REMOTE, r.s(R.string.doc_fix_https),
                        listOf("~ " + r.s(R.string.pv_origin_change, gitUrl, https)), https))
            }
            else -> {
                remoteOk = true
                if (appUrl.isBlank()) {
                    r.add(Severity.INFO, r.s(R.string.doc_remote_appblank_t), r.s(R.string.doc_remote_appblank_d, gitUrl),
                        Fix(FixKind.SYNC_APP_REMOTE, r.s(R.string.doc_fix_sync),
                            listOf("~ " + r.s(R.string.pv_app_url, "—", gitUrl)), gitUrl))
                } else if (appParsed == null || !appParsed.first.equals(parsedGit.first, true) ||
                    !appParsed.second.equals(parsedGit.second, true)) {
                    r.add(Severity.WARN, r.s(R.string.doc_remote_mismatch_t), r.s(R.string.doc_remote_mismatch_d, appUrl, gitUrl),
                        Fix(FixKind.SYNC_APP_REMOTE, r.s(R.string.doc_fix_sync),
                            listOf("~ " + r.s(R.string.pv_app_url, appUrl, gitUrl)), gitUrl))
                }
                if (cfg.getString("remote", "origin", "fetch") == null) {
                    r.add(Severity.WARN, r.s(R.string.doc_refspec_t), r.s(R.string.doc_refspec_d),
                        Fix(FixKind.FIX_REFSPEC, r.s(R.string.doc_fix_refspec),
                            listOf("+ " + r.s(R.string.pv_refspec)), gitUrl))
                }
            }
        }
        if (creds.token.isBlank()) r.add(Severity.INFO, r.s(R.string.doc_token_t), r.s(R.string.doc_token_d))

        // ---- حالة الفرع
        if (head == null) r.add(Severity.INFO, r.s(R.string.doc_nocommit_t), r.s(R.string.doc_nocommit_d))
        else if (repo.fullBranch?.startsWith("refs/heads/") != true) {
            r.add(Severity.WARN, r.s(R.string.doc_detached_t), r.s(R.string.doc_detached_d))
        }

        // ---- جلب أحدث بيانات GitHub للمقارنة (مهلة 15 ثانية)
        var fetched = false
        if (remoteOk) {
            try {
                g.fetch().setRemote("origin").setTimeout(15).setCredentialsProvider(provider).call()
                fetched = true
            } catch (e: Exception) {
                r.add(Severity.INFO, r.s(R.string.doc_fetch_failed_t), r.s(R.string.doc_fetch_failed_d, friendly(r.ctx, e)))
            }
        }
        val remoteId: ObjectId? = if (branch.isNotBlank()) repo.findRef("refs/remotes/origin/$branch")?.objectId else null

        // ---- ملفات Android الأساسية
        val covered = androidChecks(r, project, dir, repo, branch, head, remoteId)

        // ---- حالة مجلد العمل
        val st = g.status().call()
        val deletedTracked = st.missing.sorted().filter { it !in covered }
        if (deletedTracked.isNotEmpty()) {
            r.add(Severity.WARN, r.s(R.string.doc_deleted_t, deletedTracked.size),
                r.s(R.string.doc_deleted_d, deletedTracked.take(5).joinToString(", ") + if (deletedTracked.size > 5) "…" else ""),
                Fix(FixKind.FILES, r.s(R.string.doc_fix_files, deletedTracked.size),
                    limited(r, deletedTracked.map { "~ $it ← " + r.s(R.string.pv_restore_head) }),
                    items = deletedTracked.map { it to "HEAD" }))
        }
        val changes = mutableListOf<String>()
        st.untracked.sorted().forEach { changes += "+ $it" }
        st.added.sorted().forEach { changes += "+ $it" }
        (st.modified + st.changed).toSortedSet().forEach { changes += "~ $it" }
        (st.missing + st.removed).toSortedSet().forEach { changes += "- $it" }
        val uncommitted = changes.size
        if (uncommitted > 0) {
            r.add(Severity.WARN, r.s(R.string.doc_uncommitted_t, uncommitted), r.s(R.string.doc_uncommitted_d),
                Fix(FixKind.COMMIT_ALL, r.s(R.string.doc_fix_commit, uncommitted),
                    limited(r, changes) + ("Commit: " + nextCommitMsg), arg = nextCommitMsg))
        }

        // ---- المقارنة مع GitHub
        if (remoteOk && fetched && head != null) {
            if (remoteId == null) {
                val ahead = g.log().add(head).call().toList()
                r.add(Severity.INFO, r.s(R.string.doc_nobranch_t), r.s(R.string.doc_nobranch_d, branch, ahead.size),
                    Fix(FixKind.PUSH, r.s(R.string.doc_fix_push, ahead.size),
                        listOf("+ " + r.s(R.string.pv_push_branch, branch)) +
                            limited(r, ahead.map { "↑ " + it.shortMessage }, 8)))
            } else {
                val ahead = g.log().add(head).not(remoteId).call().toList()
                val behind = g.log().add(remoteId).not(head).call().toList()
                val files = if (behind.isNotEmpty()) treeDiff(repo, head, remoteId).map { mark(it) } else emptyList()
                when {
                    ahead.isNotEmpty() && behind.isEmpty() ->
                        r.add(Severity.WARN, r.s(R.string.doc_ahead_t, ahead.size), r.s(R.string.doc_ahead_d),
                            Fix(FixKind.PUSH, r.s(R.string.doc_fix_push, ahead.size),
                                limited(r, ahead.map { "↑ " + it.shortMessage }, 10)))
                    behind.isNotEmpty() && ahead.isEmpty() -> {
                        val pv = limited(r, files).toMutableList()
                        if (uncommitted > 0) pv += "! " + r.s(R.string.pv_uncommitted_warn)
                        r.add(Severity.WARN, r.s(R.string.doc_behind_t, behind.size), r.s(R.string.doc_behind_d),
                            Fix(FixKind.PULL, r.s(R.string.doc_fix_pull, behind.size), pv))
                    }
                    behind.isNotEmpty() && ahead.isNotEmpty() ->
                        r.add(Severity.WARN, r.s(R.string.doc_diverged_t), r.s(R.string.doc_diverged_d, ahead.size, behind.size),
                            Fix(FixKind.PULL, r.s(R.string.doc_fix_pull, behind.size),
                                listOf("~ " + r.s(R.string.pv_merge)) + limited(r, files)))
                    uncommitted == 0 -> r.add(Severity.OK, r.s(R.string.doc_sync_ok_t), r.s(R.string.doc_sync_ok_d))
                }

                // ملخص اختلاف الملفات المحلية عن البعيدة
                val entries = treeDiff(repo, remoteId, head)
                val onlyLocal = entries.filter { it.changeType == DiffEntry.ChangeType.ADD }.map { it.newPath }
                val onlyRemote = entries.filter { it.changeType == DiffEntry.ChangeType.DELETE }.map { it.oldPath }
                val differ = entries.filter {
                    it.changeType != DiffEntry.ChangeType.ADD && it.changeType != DiffEntry.ChangeType.DELETE
                }.map { it.newPath }
                if (entries.isNotEmpty() || uncommitted > 0) {
                    val sample = (onlyLocal.take(3).map { "+ $it" } + differ.take(3).map { "~ $it" } +
                        onlyRemote.take(3).map { "- $it" }).joinToString("\n")
                    r.add(Severity.INFO, r.s(R.string.doc_diff_t),
                        r.s(R.string.doc_diff_d, onlyLocal.size, differ.size, onlyRemote.size, uncommitted) +
                            if (sample.isNotBlank()) "\n$sample" else "")
                }
            }
        }
    }

    // فحص ملفات Android الأساسية. يرجع مسارات الملفات التي سيعالجها إصلاح "الملفات الأساسية"
    private fun androidChecks(
        r: Reporter, project: Project, dir: File, repo: Repository?, branch: String,
        head: ObjectId?, remote: ObjectId?
    ): Set<String> {
        fun has(vararg rels: String) = rels.any { File(dir, it).exists() }
        fun inRev(rev: ObjectId?, path: String): Boolean {
            if (repo == null || rev == null) return false
            val tree = repo.resolve(rev.name + "^{tree}") ?: return false
            return try {
                val tw = TreeWalk.forPath(repo, path, tree)
                val ok = tw != null
                tw?.close()
                ok
            } catch (_: Exception) { false }
        }

        val markers = listOf("settings.gradle.kts", "settings.gradle", "build.gradle.kts", "build.gradle",
            "app/build.gradle.kts", "app/build.gradle", "app/src/main/AndroidManifest.xml")
        val isAndroid = has(*markers.toTypedArray()) || File(dir, "app/src/main").exists() ||
            listOf(head, remote).any { rev -> markers.any { inRev(rev, it) } }
        if (!isAndroid) {
            r.add(Severity.INFO, r.s(R.string.doc_not_android_t), r.s(R.string.doc_not_android_d))
            return emptySet()
        }

        class Ess(val label: String, val candidates: List<String>, val template: String?, val critical: Boolean)
        val essentials = listOf(
            Ess("settings.gradle.kts", listOf("settings.gradle.kts", "settings.gradle"), "settings", true),
            Ess("build.gradle.kts", listOf("build.gradle.kts", "build.gradle"), null, true),
            Ess("app/build.gradle.kts", listOf("app/build.gradle.kts", "app/build.gradle"), null, true),
            Ess("app/src/main/AndroidManifest.xml", listOf("app/src/main/AndroidManifest.xml"), null, true),
            Ess("gradle.properties", listOf("gradle.properties"), "gradleprops", false),
            Ess(".gitignore", listOf(".gitignore"), "gitignore", false)
        )
        val items = mutableListOf<Pair<String, String>>()
        val restorePaths = mutableSetOf<String>()
        val manual = mutableListOf<String>()
        val missing = mutableListOf<String>()
        var critical = false
        for (e in essentials) {
            if (has(*e.candidates.toTypedArray())) continue
            missing.add(e.label)
            if (e.critical) critical = true
            var found: Pair<String, String>? = null
            for (c in e.candidates) {
                if (inRev(head, c)) { found = c to "HEAD"; break }
                if (inRev(remote, c)) { found = c to "origin/$branch"; break }
            }
            when {
                found != null -> { items.add(found); restorePaths.add(found.first) }
                e.template != null -> items.add(e.label to "template:" + e.template)
                else -> manual.add(e.label)
            }
        }
        if (missing.isNotEmpty()) {
            val fromGit = items.filter { !it.second.startsWith("template:") }.map { it.first }
            val generated = items.filter { it.second.startsWith("template:") }.map { it.first }
            val sb = StringBuilder(r.s(R.string.doc_ess_d, missing.joinToString(", ")))
            if (fromGit.isNotEmpty()) sb.append("\n").append(r.s(R.string.doc_ess_restore, fromGit.joinToString(", ")))
            if (generated.isNotEmpty()) sb.append("\n").append(r.s(R.string.doc_ess_template, generated.joinToString(", ")))
            if (manual.isNotEmpty()) sb.append("\n").append(r.s(R.string.doc_ess_manual, manual.joinToString(", ")))
            val pv = items.map { (p, src) ->
                if (src.startsWith("template:")) "+ $p ← " + r.s(R.string.pv_template)
                else "~ $p ← " + r.s(if (src == "HEAD") R.string.pv_restore_head else R.string.pv_restore_remote)
            }
            r.add(if (critical) Severity.ERROR else Severity.WARN, r.s(R.string.doc_ess_t), sb.toString(),
                if (items.isNotEmpty()) Fix(FixKind.FILES, r.s(R.string.doc_fix_files, items.size), pv, items = items.toList())
                else null)
        }

        // ملف workflow للبناء على GitHub
        val hasWorkflow = File(dir, ".github/workflows").listFiles()
            ?.any { it.name.endsWith(".yml") || it.name.endsWith(".yaml") } == true
        if (!hasWorkflow) {
            r.add(Severity.INFO, r.s(R.string.doc_workflow_missing_t), r.s(R.string.doc_workflow_missing_d),
                Fix(FixKind.FILES, r.s(R.string.doc_fix_workflow),
                    listOf("+ .github/workflows/build.yml ← " + r.s(R.string.pv_template)),
                    items = listOf(".github/workflows/build.yml" to "template:workflow")))
        }
        // أيقونة التطبيق ومصادر الكود
        val res = File(dir, "app/src/main/res")
        if (res.exists() && res.walkTopDown().maxDepth(2).none { it.isFile && it.name.startsWith("ic_launcher") }) {
            r.add(Severity.INFO, r.s(R.string.doc_icon_missing_t), r.s(R.string.doc_icon_missing_d))
        }
        val srcDir = File(dir, "app/src/main")
        if (srcDir.exists() && srcDir.walkTopDown().maxDepth(8).none { it.isFile && (it.extension == "kt" || it.extension == "java") }) {
            r.add(Severity.WARN, r.s(R.string.doc_sources_missing_t), r.s(R.string.doc_sources_missing_d))
        }
        return restorePaths
    }

    private fun repairMessage(ctx: Context, code: String): String = ctx.getString(when (code) {
        "CLONED" -> R.string.repair_cloned
        "ATTACHED" -> R.string.repair_attached
        "FETCH_FAILED" -> R.string.repair_fetch_failed
        else -> R.string.repair_init
    })

    // تنفيذ الإصلاح الذي وافق عليه المستخدم بعد معاينته
    suspend fun apply(ctx: Context, project: Project, repo: ProjectRepository, fix: Fix): String =
        withContext(Dispatchers.IO) {
            val dir = File(project.path)
            val gm = GitManager(repo.credentials())
            when (fix.kind) {
                FixKind.REPAIR_REPO -> repairMessage(ctx, gm.repair(dir, fix.arg.ifBlank { project.remoteUrl.trim() }))
                FixKind.RESET_GIT -> {
                    Trash.put(ctx, File(dir, ".git"), "deleted") // نُقل إلى السلة 3 أيام تحسبًا
                    repairMessage(ctx, gm.repair(dir, fix.arg.ifBlank { project.remoteUrl.trim() }))
                }
                FixKind.REMOVE_LOCK -> { File(dir, ".git/index.lock").delete(); ctx.getString(R.string.doc_done_lock) }
                FixKind.SET_REMOTE -> {
                    gm.setRemote(dir, fix.arg); repo.setRemote(project.id, fix.arg)
                    ctx.getString(R.string.remote_added)
                }
                FixKind.SYNC_APP_REMOTE -> { repo.setRemote(project.id, fix.arg); ctx.getString(R.string.remote_added) }
                FixKind.FIX_REFSPEC -> { gm.setRemote(dir, fix.arg); ctx.getString(R.string.remote_added) }
                FixKind.COMMIT_ALL -> {
                    gm.stageAll(dir)
                    gm.commit(dir, fix.arg)
                    repo.bumpVersion(project.id); repo.markUpdated(project.id)
                    ctx.getString(R.string.commit_ok, fix.arg)
                }
                FixKind.PUSH -> {
                    try { gm.push(dir) } catch (e: Exception) { repo.setPushState(project.id, "failed"); throw e }
                    repo.setPushState(project.id, "ok")
                    ctx.getString(R.string.push_ok)
                }
                FixKind.PULL -> { gm.pull(dir); repo.markUpdated(project.id); ctx.getString(R.string.pull_ok) }
                FixKind.FILES -> {
                    val templates = fix.items.filter { it.second.startsWith("template:") }
                    val fromGit = fix.items.filter { !it.second.startsWith("template:") }
                    templates.forEach { (path, src) ->
                        Templates.content(src.removePrefix("template:"), project.name)?.let { content ->
                            val f = File(dir, path)
                            f.parentFile?.mkdirs()
                            if (!f.exists()) f.writeText(content)
                        }
                    }
                    fromGit.groupBy({ it.second }, { it.first }).forEach { (ref, paths) -> gm.restorePaths(dir, ref, paths) }
                    ctx.getString(R.string.doc_done_files, fix.items.size)
                }
            }
        }
}
