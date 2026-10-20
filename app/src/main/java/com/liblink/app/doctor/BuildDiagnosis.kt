package com.liblink.app.doctor

import android.content.Context
import com.liblink.app.R
import com.liblink.app.network.GitHubApi

// تشخيص فشل البناء: المرحلة + السبب المحتمل + الإجراء المقترح + أهم الأدلة من السجل
data class Diagnosis(
    val stage: String,
    val step: String,
    val cause: String,
    val action: String,
    val evidence: List<String>,
    val allLines: List<String>
)

object BuildDiagnosis {
    private class Rule(val pattern: Regex, val cause: Int, val action: Int)

    private val opts = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    private fun rule(p: String, c: Int, a: Int) = Rule(Regex(p, opts), c, a)

    // الترتيب مهم: الأخص أولًا
    private val rules = listOf(
        rule("google-services\\.json is missing", R.string.bd_gs_c, R.string.bd_gs_a),
        rule("No matching client found for package name", R.string.bd_gspkg_c, R.string.bd_gspkg_a),
        rule("Unresolved reference", R.string.bd_unres_c, R.string.bd_unres_a),
        rule("(^|\\s)e: |compile\\w*Kotlin", R.string.bd_kotlin_c, R.string.bd_kotlin_a),
        rule("cannot find symbol|compile\\w*JavaWithJavac|error: .*\\.java", R.string.bd_java_c, R.string.bd_java_a),
        rule("AAPT|resource .* not found|resource (linking|compilation) failed|aapt2", R.string.bd_aapt_c, R.string.bd_aapt_a),
        rule("Manifest merger failed", R.string.bd_manifest_c, R.string.bd_manifest_a),
        rule("Duplicate (class|resources|entry|files?)", R.string.bd_dup_c, R.string.bd_dup_a),
        rule("Plugin .* was not found|plugin with id", R.string.bd_plugin_c, R.string.bd_plugin_a),
        rule("Could not (resolve|find|GET)|Failed to resolve", R.string.bd_dep_c, R.string.bd_dep_a),
        rule("Project '.*' not found|Task '.*' not found|not found in root project", R.string.bd_module_c, R.string.bd_module_a),
        rule("Keystore|keystore was tampered|Failed to read key", R.string.bd_sign_c, R.string.bd_sign_a),
        rule("OutOfMemoryError|Java heap space|GC overhead|Metaspace", R.string.bd_mem_c, R.string.bd_mem_a),
        rule("Unsupported class file major version|invalid source release|requires Java [0-9]+|Unsupported Java", R.string.bd_jver_c, R.string.bd_jver_a),
        rule("SDK location not found|ANDROID_HOME|Failed to find Build Tools|Failed to install the following Android SDK|licenses? (have not|not) been accepted", R.string.bd_sdk_c, R.string.bd_sdk_a),
        rule("gradlew|GradleWrapperMain", R.string.bd_wrapper_c, R.string.bd_wrapper_a),
        rule("No files were found with the provided path|Path does not exist", R.string.bd_upload_c, R.string.bd_upload_a),
        rule("Resource not accessible by integration|Forbidden|HTTP.*403", R.string.bd_perm_c, R.string.bd_perm_a),
        rule("Unable to resolve action|Missing download info", R.string.bd_action_c, R.string.bd_action_a)
    )

    private enum class Stage { CHECKOUT, SETUP, BUILD, UPLOAD, PUBLISH, UNKNOWN }

    // المرحلة من اسم الخطوة الفاشلة في الـ workflow
    private fun stageOf(step: String?): Stage {
        val s = (step ?: "").lowercase()
        return when {
            s.contains("checkout") -> Stage.CHECKOUT
            s.contains("java") || s.contains("setup") || s.contains("set up") -> Stage.SETUP
            s.contains("upload") -> Stage.UPLOAD
            s.contains("release") || s.contains("publish") -> Stage.PUBLISH
            s.contains("build") || s.contains("gradle") || s.contains("assemble") -> Stage.BUILD
            else -> Stage.UNKNOWN
        }
    }

    private val pathPrefix = Regex("file:///home/runner/work/[^/]+/[^/]+/")
    private fun clean(s: String) = s.replace(pathPrefix, "").replace("##[error]", "").trim()

    fun analyze(ctx: Context, r: GitHubApi.FailureReport): Diagnosis {
        val step = r.step.orEmpty()
        // لا توجد مهمة فاشلة: غالبًا ملف الـ workflow نفسه غير صالح فلم تبدأ أي مهمة
        if (r.job == null) {
            return Diagnosis(ctx.getString(R.string.stage_workflow), "", ctx.getString(R.string.bd_yaml_c),
                ctx.getString(R.string.bd_yaml_a), emptyList(), emptyList())
        }
        val stage = stageOf(r.step)
        val stageName = ctx.getString(when (stage) {
            Stage.CHECKOUT -> R.string.stage_checkout
            Stage.SETUP -> R.string.stage_setup
            Stage.BUILD -> R.string.stage_build
            Stage.UPLOAD -> R.string.stage_upload
            Stage.PUBLISH -> R.string.stage_publish
            Stage.UNKNOWN -> R.string.stage_unknown
        })
        val all = r.lines.map { clean(it) }
        val text = r.lines.joinToString("\n")
        for (rule in rules) {
            if (rule.pattern.containsMatchIn(text)) {
                val evidence = r.lines.filter { rule.pattern.containsMatchIn(it) }.take(3).map { clean(it) }
                    .ifEmpty { all.take(3) }
                return Diagnosis(stageName, step, ctx.getString(rule.cause), ctx.getString(rule.action), evidence, all)
            }
        }
        // لا يوجد نمط معروف: سبب عام بحسب المرحلة
        val (c, a) = when (stage) {
            Stage.CHECKOUT -> R.string.gen_checkout_c to R.string.gen_checkout_a
            Stage.SETUP -> R.string.gen_setup_c to R.string.gen_setup_a
            Stage.BUILD -> R.string.gen_build_c to R.string.gen_build_a
            Stage.UPLOAD -> R.string.gen_upload_c to R.string.gen_upload_a
            Stage.PUBLISH -> R.string.gen_publish_c to R.string.gen_publish_a
            Stage.UNKNOWN -> R.string.gen_other_c to R.string.gen_other_a
        }
        return Diagnosis(stageName, step, ctx.getString(c), ctx.getString(a), all.take(3), all)
    }
}
