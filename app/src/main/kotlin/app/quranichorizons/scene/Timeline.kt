package app.quranichorizons.scene

import app.quranichorizons.data.SceneDef

/** حالة كل عنصر: معرّف العنصر ← (اسم المعامل ← قيمته). */
typealias SceneState = Map<String, Map<String, Float>>

/**
 * محرك زمن المشهد (منطق نقي قابل للاختبار على JVM).
 * معنى الآية يحدّد النبضة (beat) الخاصة بها، والنبضة تحرّك عناصر المشهد نفسها (ظهور/حركة/تشكّل)،
 * وحالة العناصر تُحمَل من آية إلى التي تليها داخل المشهد الواحد فيتطور الحدث بدل أن يُعاد رسمه.
 */
object SceneTimeline {

    fun initial(scene: SceneDef): SceneState = scene.actors.associate { it.id to it.init }

    private fun apply(base: SceneState, set: Map<String, Map<String, Float>>): SceneState {
        if (set.isEmpty()) return base
        val out = base.toMutableMap()
        for ((id, st) in set) out[id] = (out[id] ?: emptyMap()) + st
        return out
    }

    /** الحالة قبل بدء الآية: نتيجة كل نبضات الآيات السابقة في المشهد. */
    fun stateBefore(scene: SceneDef, ayah: Int): SceneState {
        var st = initial(scene)
        for (b in scene.beats.filter { it.ayah < ayah }.sortedBy { it.ayah }) {
            for (k in b.keys.sortedBy { it.t }) st = apply(st, k.set)
        }
        return st
    }

    /** الحالة عند تقدّم p (0..1) داخل الآية. */
    fun resolve(scene: SceneDef, ayah: Int, p: Float): SceneState {
        val before = stateBefore(scene, ayah)
        val beat = scene.beats.firstOrNull { it.ayah == ayah } ?: return before
        val pts = ArrayList<Pair<Float, SceneState>>()
        pts.add(0f to before)
        var cur = before
        for (k in beat.keys.sortedBy { it.t }) {
            cur = apply(cur, k.set)
            pts.add(k.t.coerceIn(0f, 1f) to cur)
        }
        val q = p.coerceIn(0f, 1f)
        for (i in 1 until pts.size) {
            if (q < pts[i].first) {
                val (t0, s0) = pts[i - 1]
                val (t1, s1) = pts[i]
                val den = t1 - t0
                val u = if (den <= 0f) 1f else ((q - t0) / den).coerceIn(0f, 1f)
                return lerp(s0, s1, smooth(u))
            }
        }
        return pts.last().second
    }

    /** الحالة النهائية للمشهد (تُستعمل عند المزج مع المشهد التالي). */
    fun finalState(scene: SceneDef): SceneState {
        val last = scene.ayahs.maxOrNull() ?: return initial(scene)
        return resolve(scene, last, 1f)
    }

    private fun smooth(u: Float) = u * u * (3f - 2f * u)

    private fun lerp(a: SceneState, b: SceneState, u: Float): SceneState {
        val out = HashMap<String, Map<String, Float>>()
        for (id in a.keys + b.keys) {
            val x = a[id] ?: emptyMap()
            val y = b[id] ?: emptyMap()
            val m = HashMap<String, Float>()
            for (k in x.keys + y.keys) {
                val v0 = x[k] ?: y[k] ?: 0f
                val v1 = y[k] ?: x[k] ?: 0f
                m[k] = v0 + (v1 - v0) * u
            }
            out[id] = m
        }
        return out
    }
}
