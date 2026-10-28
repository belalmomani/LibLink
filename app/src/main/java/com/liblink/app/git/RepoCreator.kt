package com.liblink.app.git

import com.liblink.app.data.Project
import com.liblink.app.data.ProjectRepository
import com.liblink.app.network.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CreateOptions(
    val name: String,
    val description: String,
    val isPrivate: Boolean,
    val org: String,
    val addReadme: Boolean,
    val pushNow: Boolean
)

data class CreateResult(val info: GitHubApi.RepoInfo, val pushed: Boolean, val pushError: String?)

// اسم صالح لمستودع GitHub: حروف إنجليزية وأرقام و - _ . فقط (المسافات تتحول إلى -)
fun sanitizeRepoName(s: String): String =
    s.trim().replace(Regex("\\s+"), "-").replace(Regex("[^A-Za-z0-9._-]"), "").trim('.', '-')

// إنشاء مستودع على GitHub من التطبيق وربطه بالمشروع ورفع الملفات
object RepoCreator {

    suspend fun create(project: Project, repo: ProjectRepository, opt: CreateOptions): CreateResult {
        val creds = repo.credentials()
        val gm = GitManager(creds)
        val dir = File(project.path)
        dir.mkdirs()
        if (!File(dir, ".git").exists()) gm.init(dir, "")

        // 1) إنشاء المستودع على GitHub
        val info = GitHubApi(creds.token).createRepo(opt.name, opt.description, opt.isPrivate, opt.org)

        // 2) ربط المشروع به (origin)
        gm.setRemote(dir, info.cloneUrl)
        repo.setRemote(project.id, info.cloneUrl)
        repo.markUpdated(project.id)

        // 3) ملف README اختياري
        if (opt.addReadme) withContext(Dispatchers.IO) {
            val f = File(dir, "README.md")
            if (!f.exists()) f.writeText("# ${opt.name}\n" + if (opt.description.isNotBlank()) "\n${opt.description}\n" else "")
        }
        if (!opt.pushNow) return CreateResult(info, false, null)

        // 4) Commit أول ثم رفع الملفات. المستودع أُنشئ وربط حتى لو فشل الرفع
        return try {
            gm.stageAll(dir)
            if (gm.status(dir).total > 0) {
                gm.commit(dir, "${project.name} ${repo.versionLabel(project.id)}")
                repo.bumpVersion(project.id)
            }
            gm.push(dir)
            repo.setPushState(project.id, "ok")
            CreateResult(info, true, null)
        } catch (e: Exception) {
            repo.setPushState(project.id, "failed")
            CreateResult(info, false, e.message ?: e.toString())
        }
    }
}
