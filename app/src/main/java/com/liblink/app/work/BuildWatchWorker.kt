package com.liblink.app.work

import android.content.Context
import androidx.work.*
import com.liblink.app.data.ProjectRepository
import com.liblink.app.doctor.BuildDiagnosis
import com.liblink.app.network.GitHubApi
import kotlinx.coroutines.delay
import java.time.Instant

// يراقب البناء في الخلفية ويرسل إشعارًا عند انتهائه (نجاحًا أو فشلًا)
class BuildWatchWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val pid = inputData.getString("projectId") ?: return Result.success()
        val name = inputData.getString("name") ?: ""
        val owner = inputData.getString("owner") ?: return Result.success()
        val repo = inputData.getString("repo") ?: return Result.success()
        val wf = inputData.getString("workflow") ?: return Result.success()
        val since = inputData.getLong("since", 0L)
        val attempt = inputData.getInt("attempt", 0)
        val token = ProjectRepository(ctx).credentials().token
        if (token.isBlank()) return Result.success()

        val api = GitHubApi(token)
        val deadline = System.currentTimeMillis() + 8 * 60_000L
        while (System.currentTimeMillis() < deadline) {
            // أحدث تنفيذ بدأ بعد لحظة الضغط على زر البناء
            val run = runCatching { api.runs(owner, repo, wf) }.getOrNull()?.firstOrNull {
                runCatching { Instant.parse(it.createdAt).toEpochMilli() }.getOrDefault(0L) >= since - 60_000L
            }
            if (run != null && run.status == "completed") {
                val ok = run.conclusion == "success"
                // عند الفشل: المرحلة والسبب المحتمل داخل الإشعار نفسه
                val detail = if (ok) null else runCatching {
                    val d = BuildDiagnosis.analyze(ctx, api.failureReport(owner, repo, run.id))
                    d.stage + ": " + d.cause
                }.getOrNull()
                Notifier.buildDone(ctx, pid, name, run.number, ok, detail)
                return Result.success()
            }
            delay(15_000)
        }
        // لم ينتهِ بعد: نعيد الجدولة (حتى ~40 دقيقة)
        if (attempt < 4) enqueue(ctx, pid, name, owner, repo, wf, since, attempt + 1)
        return Result.success()
    }

    companion object {
        fun enqueue(
            ctx: Context, projectId: String, name: String, owner: String, repo: String,
            workflow: String, since: Long, attempt: Int = 0
        ) {
            val req = OneTimeWorkRequestBuilder<BuildWatchWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(
                    "projectId" to projectId, "name" to name, "owner" to owner, "repo" to repo,
                    "workflow" to workflow, "since" to since, "attempt" to attempt
                ))
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("build_watch_$projectId", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
