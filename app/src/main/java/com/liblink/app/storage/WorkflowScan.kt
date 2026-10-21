package com.liblink.app.storage

import java.io.File

// ملف workflow موجود في المشروع مع ما فهمناه من محتواه
data class WorkflowInfo(
    val file: String,       // اسم الملف داخل .github/workflows
    val name: String,       // قيمة name: داخل الملف
    val manual: Boolean,    // هل يحتوي workflow_dispatch (يمكن تشغيله من التطبيق)
    val onPush: Boolean,    // هل يعمل عند Push
    val builds: Boolean,    // هل يبني APK أو يرفع artifact
    val content: String
)

// يقرأ ملفات .github/workflows في المشروع ويقترن كل ملف بمحتواه
object WorkflowScan {
    private val buildRegex = Regex("assemble|bundle|upload-artifact|\\.apk", RegexOption.IGNORE_CASE)
    private val pushRegex = Regex("(^|\\s)push\\s*:", RegexOption.MULTILINE)
    private val nameRegex = Regex("^name:\\s*(.+)$", RegexOption.MULTILINE)

    fun scan(dir: File): List<WorkflowInfo> {
        val d = File(dir, ".github/workflows")
        return (d.listFiles() ?: emptyArray())
            .filter { it.isFile && (it.name.endsWith(".yml") || it.name.endsWith(".yaml")) && it.length() < 200_000 }
            .sortedBy { it.name.lowercase() }
            .map { f ->
                val text = runCatching { f.readText() }.getOrDefault("")
                // نتجاهل أسطر التعليقات عند البحث
                val code = text.lineSequence().filterNot { it.trimStart().startsWith("#") }.joinToString("\n")
                WorkflowInfo(
                    f.name,
                    nameRegex.find(code)?.groupValues?.get(1)?.trim()?.trim('"', '\'').orEmpty(),
                    code.contains("workflow_dispatch"),
                    pushRegex.containsMatchIn(code),
                    buildRegex.containsMatchIn(code),
                    text
                )
            }
    }
}
