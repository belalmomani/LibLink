package com.liblink.app.data

import org.json.JSONArray
import org.json.JSONObject

// مشروع في قائمة السحابة: الاسم والعنوان فقط (لا ملفات ولا مسارات)
data class CloudProject(val name: String, val url: String, val at: Long)

fun cloudProjectsFromJson(arr: JSONArray): List<CloudProject> = (0 until arr.length()).map {
    val o = arr.getJSONObject(it)
    CloudProject(o.optString("name"), o.optString("url"), o.optLong("at"))
}

fun cloudProjectsToJson(list: List<CloudProject>): JSONArray {
    val arr = JSONArray()
    list.sortedWith(compareBy({ it.name.lowercase() }, { libKey(it.url) })).forEach {
        arr.put(JSONObject().put("name", it.name).put("url", it.url).put("at", it.at))
    }
    return arr
}

// دمج قوائم المشاريع من جهازين: اتحاد بالرابط، والحذف (الإخفاء) يُحترم ما لم يُضف بعده
fun mergeProjects(
    local: List<CloudProject>, remote: List<CloudProject>,
    tombsA: Map<String, Long>, tombsB: Map<String, Long>
): Pair<List<CloudProject>, Map<String, Long>> {
    val tombs = HashMap<String, Long>()
    for (m in listOf(tombsA, tombsB)) for ((k, t) in m) tombs[k] = maxOf(tombs[k] ?: 0L, t)
    val best = LinkedHashMap<String, CloudProject>()
    for (p in remote) {
        val k = libKey(p.url)
        val cur = best[k]
        if (cur == null || p.at > cur.at) best[k] = p
    }
    // مشاريع هذا الجهاز لا تُعاد إلى القائمة إن أخفاها المستخدم عمدًا
    for (p in local) {
        val k = libKey(p.url)
        if (k in tombs) continue
        val cur = best[k]
        if (cur == null || p.at > cur.at) best[k] = p
    }
    val keep = best.values.filter { it.at > (tombs[libKey(it.url)] ?: 0L) }
    val cutoff = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
    val tombsOut = tombs.filter { (_, t) -> t > cutoff }.toSortedMap()
    return keep to tombsOut
}
