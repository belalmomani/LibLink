package com.liblink.app.git

import com.liblink.app.data.Credentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.ByteArrayOutputStream
import java.io.File

data class RepoStatus(val total: Int, val added: Int, val modified: Int, val deleted: Int)

// عمليات Git عبر JGit (نفس مكتبة MGit)
class GitManager(private val creds: Credentials) {

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
}
