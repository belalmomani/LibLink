package com.liblink.app.storage

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.liblink.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

// إجراء واحد يمكن عكسه: CREATED(path) أو REPLACED(path, trashId) أو DELETED(path, trashId) أو RENAMED(from, to)
data class OpAction(val type: String, val path: String, val extra: String = "")

data class Operation(val id: String, val at: Long, val label: String, val actions: List<OpAction>)

data class UndoResult(val done: Int, val skipped: Int)

// سجل العمليات على ملفات كل مشروع: يسمح بالتراجع عن آخر عملية.
// النسخ القديمة المستبدلة أو المحذوفة محفوظة في سلة المحذوفات، والجديدة تُنقل إليها عند التراجع
object OpJournal {
    // تتغير عند أي تسجيل أو تراجع فتتحدث الشاشات التي تقرأها
    var version by mutableIntStateOf(0)

    private const val KEEP = 20
    private const val MAX_AGE_MS = 7L * 24 * 3600 * 1000

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("liblink_journal", Context.MODE_PRIVATE)
    private fun key(projectPath: String) = "j_" + projectPath.hashCode()

    fun list(ctx: Context, projectPath: String): List<Operation> {
        val arr = runCatching { JSONArray(prefs(ctx).getString(key(projectPath), "[]")) }.getOrDefault(JSONArray())
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        val out = mutableListOf<Operation>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val a = o.optJSONArray("actions") ?: JSONArray()
            val actions = (0 until a.length()).map { j ->
                val x = a.getJSONObject(j)
                OpAction(x.getString("t"), x.getString("p"), x.optString("x"))
            }
            val op = Operation(o.getString("id"), o.getLong("at"), o.optString("label"), actions)
            if (op.at >= cutoff) out.add(op)
        }
        return out
    }

    private fun save(ctx: Context, projectPath: String, ops: List<Operation>) {
        val arr = JSONArray()
        ops.takeLast(KEEP).forEach { op ->
            val a = JSONArray()
            op.actions.forEach { a.put(JSONObject().put("t", it.type).put("p", it.path).put("x", it.extra)) }
            arr.put(JSONObject().put("id", op.id).put("at", op.at).put("label", op.label).put("actions", a))
        }
        prefs(ctx).edit().putString(key(projectPath), arr.toString()).apply()
    }

    fun record(ctx: Context, projectPath: String, label: String, actions: List<OpAction>) {
        if (actions.isEmpty()) return
        val ops = list(ctx, projectPath) + Operation(UUID.randomUUID().toString(), System.currentTimeMillis(), label, actions)
        save(ctx, projectPath, ops)
        version++
    }

    fun last(ctx: Context, projectPath: String): Operation? = list(ctx, projectPath).lastOrNull()

    private fun rel(projectPath: String, p: String) = p.removePrefix(projectPath.trimEnd('/') + "/")

    // ما سيتغير عند التراجع (للمعاينة قبل التنفيذ)
    fun preview(ctx: Context, op: Operation, projectPath: String): List<String> = op.actions.asReversed().map {
        when (it.type) {
            "CREATED" -> "- " + rel(projectPath, it.path)
            "REPLACED" -> "~ " + rel(projectPath, it.path) + " ← " + ctx.getString(R.string.undo_pv_prev)
            "DELETED" -> "+ " + rel(projectPath, it.path)
            else -> "~ " + rel(projectPath, it.extra) + " → " + rel(projectPath, it.path)
        }
    }

    // التراجع عن آخر عملية: عكس إجراءاتها بالترتيب المعاكس
    fun undo(ctx: Context, projectPath: String): UndoResult? {
        val ops = list(ctx, projectPath)
        val op = ops.lastOrNull() ?: return null
        var done = 0
        var skipped = 0
        for (a in op.actions.asReversed()) {
            try {
                when (a.type) {
                    // ملف أنشأته العملية: ينتقل إلى السلة (ليس حذفًا نهائيًا)
                    "CREATED" -> {
                        val f = File(a.path)
                        if (f.exists()) { Trash.put(ctx, f, "undone"); done++ } else skipped++
                    }
                    // استُبدل أو حُذف: نعيد النسخة القديمة من السلة، والحالية تنتقل إلى السلة
                    "REPLACED", "DELETED" -> {
                        val entry = Trash.find(ctx, a.extra)
                        if (entry == null) skipped++
                        else {
                            val cur = File(a.path)
                            if (cur.exists()) Trash.put(ctx, cur, "undone")
                            Trash.restoreTo(entry, cur)
                            done++
                        }
                    }
                    "RENAMED" -> {
                        val from = File(a.path)
                        val to = File(a.extra)
                        if (to.exists() && !from.exists() && to.renameTo(from)) done++ else skipped++
                    }
                    else -> skipped++
                }
            } catch (_: Exception) { skipped++ }
        }
        save(ctx, projectPath, ops.dropLast(1))
        version++
        return UndoResult(done, skipped)
    }
}
