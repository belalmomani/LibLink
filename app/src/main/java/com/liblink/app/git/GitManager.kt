package com.liblink.app.git

import com.liblink.app.data.Credentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.ByteArrayOutputStream
import java.io.File

data class RepoStatus(val total: Int, val added: Int, val modified: Int, val deleted: Int)

// ملخص حالة المشروع لقائمة المشاريع
data class RepoSummary(
    val changes: Int,        // ملفات متغيرة غير مرفوعة
    val ahead: Int,          // commits لم تُرفع بعد
    val hasRemote: Boolean,
    val lastCommitMs: Long,
    val branch: String
)

data class BranchInfo(val name: String, val remote: Boolean, val current: Boolean)

// عمليات Git عبر JGit (نفس مكتبة MGit)
class GitManager(private val creds: Credentials = Credentials("", "", "", "")) {

    private fun provider() =
        if (creds.token.isBlank()) null
        else UsernamePasswordCredentialsProvider(creds.user.ifBlank { "token" }, creds.token)

    suspend fun clone(url: String, dir: File) = withContext(Dispatchers.IO) {
        Git.cloneRepository().setURI(url).setDirectory(dir)
            .setCredentialsProvider(provider()).call().close()
    }

    suspend fun init(dir: File, remoteUrl: String) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        Git.init().setDirectory(dir).setInitialBranch("main").call().use {
            if (remoteUrl.isNotBlank()) writeRemote(it, remoteUrl)
        }
    }

    suspend fun setRemote(dir: File, url: String) = withContext(Dispatchers.IO) {
        Git.open(dir).use { writeRemote(it, url) }
    }

    private fun writeRemote(git: Git, url: String) {
        val cfg = git.repository.config
        cfg.setString("remote", "origin", "url", url)
        cfg.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
        cfg.save()
    }

    suspend fun status(dir: File): RepoStatus = withContext(Dispatchers.IO) {
        Git.open(dir).use {
            val s = it.status().call()
            val added = s.untracked.size + s.added.size
            val modified = s.modified.size + s.changed.size
            val deleted = s.missing.size + s.removed.size
            RepoStatus(added + modified + deleted, added, modified, deleted)
        }
    }

    // git add للملفات الجديدة والمعدّلة والمحذوفة
    suspend fun stageAll(dir: File) = withContext(Dispatchers.IO) {
        Git.open(dir).use {
            it.add().addFilepattern(".").call()
            it.add().setUpdate(true).addFilepattern(".").call()
        }
    }

    suspend fun commit(dir: File, message: String): String = withContext(Dispatchers.IO) {
        Git.open(dir).use {
            val cfg = it.repository.config
            val name = cfg.getString("user", null, "name")?.takeIf { n -> n.isNotBlank() }
                ?: creds.authorName.ifBlank { creds.user.ifBlank { "LibLink" } }
            val email = cfg.getString("user", null, "email")?.takeIf { m -> m.isNotBlank() }
                ?: creds.authorEmail.ifBlank { "liblink@users.noreply.github.com" }
            val c = it.commit().setMessage(message).setAuthor(name, email)
                .setCommitter(name, email).call()
            c.abbreviate(7).name()
        }
    }

    suspend fun push(dir: File) = withContext(Dispatchers.IO) {
        Git.open(dir).use { git ->
            val branch = git.repository.branch
            val results = git.push().setRemote(Constants.DEFAULT_REMOTE_NAME)
                .setRefSpecs(RefSpec("refs/heads/$branch:refs/heads/$branch"))
                .setCredentialsProvider(provider()).call()
            for (r in results) for (u in r.remoteUpdates) {
                if (u.status != RemoteRefUpdate.Status.OK &&
                    u.status != RemoteRefUpdate.Status.UP_TO_DATE
                ) throw IllegalStateException("${u.status} ${u.message ?: ""}".trim())
            }
        }
    }

    suspend fun pull(dir: File) = withContext(Dispatchers.IO) {
        Git.open(dir).use {
            val r = it.pull().setCredentialsProvider(provider()).call()
            if (!r.isSuccessful) throw IllegalStateException(r.toString())
        }
    }

    // ---- عمليات إضافية مثل MGit ----

    suspend fun resetHard(dir: File, ref: String = "HEAD") = withContext(Dispatchers.IO) {
        Git.open(dir).use { it.reset().setMode(ResetCommand.ResetType.HARD).setRef(ref).call() }
    }

    suspend fun fetch(dir: File) = withContext(Dispatchers.IO) {
        Git.open(dir).use {
            it.fetch().setRemote(Constants.DEFAULT_REMOTE_NAME).setCredentialsProvider(provider()).call()
        }
    }

    suspend fun merge(dir: File, ref: String): String = withContext(Dispatchers.IO) {
        Git.open(dir).use {
            val id = it.repository.resolve(ref.trim()) ?: throw IllegalArgumentException(ref)
            it.merge().include(id).call().mergeStatus.toString()
        }
    }

    suspend fun rebase(dir: File, upstream: String): String = withContext(Dispatchers.IO) {
        Git.open(dir).use { it.rebase().setUpstream(upstream.trim()).call().status.toString() }
    }

    suspend fun cherryPick(dir: File, commitId: String): String = withContext(Dispatchers.IO) {
        Git.open(dir).use {
            val id = it.repository.resolve(commitId.trim()) ?: throw IllegalArgumentException(commitId)
            it.cherryPick().include(id).call().status.toString()
        }
    }

    // الفروقات: المجهّز ثم غير المجهّز
    suspend fun diff(dir: File): String = withContext(Dispatchers.IO) {
        Git.open(dir).use { git ->
            val out = ByteArrayOutputStream()
            runCatching { git.diff().setCached(true).setOutputStream(out).call() }
            git.diff().setOutputStream(out).call()
            out.toString("UTF-8")
        }
    }

    suspend fun removeRemote(dir: File) = withContext(Dispatchers.IO) {
        Git.open(dir).use {
            val cfg = it.repository.config
            cfg.unsetSection("remote", Constants.DEFAULT_REMOTE_NAME)
            cfg.save()
        }
    }

    suspend fun readConfig(dir: File): String = withContext(Dispatchers.IO) {
        File(dir, ".git/config").readText()
    }

    suspend fun writeConfig(dir: File, text: String) = withContext(Dispatchers.IO) {
        File(dir, ".git/config").writeText(text)
    }

    suspend fun getUser(dir: File): Pair<String, String> = withContext(Dispatchers.IO) {
        Git.open(dir).use {
            val c = it.repository.config
            (c.getString("user", null, "name") ?: "") to (c.getString("user", null, "email") ?: "")
        }
    }

    suspend fun setUser(dir: File, name: String, email: String) = withContext(Dispatchers.IO) {
        Git.open(dir).use {
            val c = it.repository.config
            c.setString("user", null, "name", name)
            c.setString("user", null, "email", email)
            c.save()
        }
    }

    // ---- الفروع والملخص ----

    suspend fun currentBranch(dir: File): String = withContext(Dispatchers.IO) {
        Git.open(dir).use { val b = it.repository.branch ?: ""; if (b.length == 40) b.take(7) else b }
    }

    suspend fun branches(dir: File): List<BranchInfo> = withContext(Dispatchers.IO) {
        Git.open(dir).use { git ->
            val cur = git.repository.fullBranch
            git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call()
                .filter { !it.name.endsWith("/HEAD") }
                .map { BranchInfo(Repository.shortenRefName(it.name), it.name.startsWith("refs/remotes/"), it.name == cur) }
        }
    }

    // الانتقال لفرع محلي، أو إنشاء فرع محلي يتتبع الفرع البعيد
    suspend fun checkout(dir: File, b: BranchInfo) = withContext(Dispatchers.IO) {
        Git.open(dir).use { git ->
            if (!b.remote) git.checkout().setName(b.name).call()
            else {
                val local = b.name.substringAfter('/')
                if (git.repository.findRef("refs/heads/$local") != null) git.checkout().setName(local).call()
                else git.checkout().setCreateBranch(true).setName(local).setStartPoint(b.name)
                    .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).call()
            }
        }
    }

    suspend fun createBranch(dir: File, name: String) = withContext(Dispatchers.IO) {
        Git.open(dir).use { it.checkout().setCreateBranch(true).setName(name.trim()).call() }
    }

    suspend fun deleteBranch(dir: File, name: String) = withContext(Dispatchers.IO) {
        Git.open(dir).use { it.branchDelete().setBranchNames(name).setForce(true).call() }
    }

    suspend fun summary(dir: File): RepoSummary? = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(dir).use { git ->
                val repo = git.repository
                val s = git.status().call()
                val changes = s.untracked.size + s.added.size + s.modified.size +
                    s.changed.size + s.missing.size + s.removed.size
                val hasRemote = repo.config.getString("remote", Constants.DEFAULT_REMOTE_NAME, "url") != null
                val head = repo.resolve("HEAD")
                val branch = repo.branch ?: ""
                var ahead = 0
                if (head != null && hasRemote) ahead = BranchTrackingStatus.of(repo, branch)?.aheadCount ?: 1
                val last = if (head != null)
                    (git.log().setMaxCount(1).call().firstOrNull()?.commitTime?.toLong() ?: 0L) * 1000 else 0L
                RepoSummary(changes, ahead, hasRemote, last, branch)
            }
        }.getOrNull()
    }

    // إصلاح مجلد فقد بيانات .git (حُذف أو استُبدل):
    // مجلد فارغ/مفقود ← استنساخ جديد. مجلد فيه ملفات ← git init + ربط origin + fetch
    // ثم تحريك الفرع إلى آخر commit في GitHub مع إبقاء ملفاتك كما هي (تظهر كتغييرات).
    suspend fun repair(dir: File, remoteUrl: String): String = withContext(Dispatchers.IO) {
        if (remoteUrl.isNotBlank() && (!dir.exists() || dir.list().isNullOrEmpty())) {
            dir.parentFile?.mkdirs()
            Git.cloneRepository().setURI(remoteUrl).setDirectory(dir)
                .setCredentialsProvider(provider()).call().close()
            return@withContext "CLONED"
        }
        dir.mkdirs()
        Git.init().setDirectory(dir).setInitialBranch("main").call().use { git ->
            if (remoteUrl.isBlank()) return@use "INIT_ONLY"
            writeRemote(git, remoteUrl)
            try {
                git.fetch().setRemote(Constants.DEFAULT_REMOTE_NAME).setCredentialsProvider(provider()).call()
            } catch (e: Exception) {
                // نحذف .git الجديد ليبقى زر الإصلاح متاحًا لإعادة المحاولة
                File(dir, ".git").deleteRecursively()
                return@use "FETCH_FAILED"
            }
            val repo = git.repository
            val branch = listOf("main", "master").firstOrNull { repo.findRef("refs/remotes/origin/$it") != null }
                ?: repo.refDatabase.getRefsByPrefix("refs/remotes/origin/")
                    .map { it.name.removePrefix("refs/remotes/origin/") }
                    .firstOrNull { it != "HEAD" }
                ?: return@use "INIT_ONLY"
            val id = repo.resolve("refs/remotes/origin/$branch")
            val ru = repo.updateRef("refs/heads/$branch")
            ru.setNewObjectId(id)
            ru.forceUpdate()
            repo.updateRef(Constants.HEAD).link("refs/heads/$branch")
            // MIXED: يعيد بناء الـ index فقط ولا يلمس ملفات العمل
            git.reset().setMode(ResetCommand.ResetType.MIXED).setRef("HEAD").call()
            val cfg = repo.config
            cfg.setString("branch", branch, "remote", Constants.DEFAULT_REMOTE_NAME)
            cfg.setString("branch", branch, "merge", "refs/heads/$branch")
            cfg.save()
            "ATTACHED"
        }
    }

    // استعادة ملفات من commit أو فرع (HEAD أو origin/main) إلى مجلد العمل
    suspend fun restorePaths(dir: File, ref: String, paths: List<String>) = withContext(Dispatchers.IO) {
        Git.open(dir).use { it.checkout().setStartPoint(ref).addPaths(paths).call() }
    }
}
