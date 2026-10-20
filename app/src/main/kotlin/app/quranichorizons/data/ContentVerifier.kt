package app.quranichorizons.data

import java.security.MessageDigest

/** منطق نقي (بلا أندرويد) للتحقق من بصمات الملفات وبنية الوحدة؛ قابل للاختبار على JVM. */
object ContentVerifier {

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun fileMatches(entry: FileEntry, bytes: ByteArray): Boolean =
        bytes.size.toLong() == entry.bytes && sha256Hex(bytes).equals(entry.sha256, ignoreCase = true)

    /** يعيد قائمة مشكلات البنية؛ فارغة = سليمة. */
    fun structureProblems(info: SurahInfo, module: ModuleFile, scenes: Map<String, SceneDef>): List<String> {
        val out = ArrayList<String>()
        if (module.surah != info.number) out.add("رقم السورة لا يطابق")
        if (module.ayahCount != info.ayahCount || module.ayahs.size != info.ayahCount) out.add("عدد الآيات لا يطابق")
        if (module.ayahs.map { it.number } != (1..info.ayahCount).toList()) out.add("ترتيب الآيات غير صحيح")
        module.ayahs.forEach { a ->
            if (a.text.isBlank()) out.add("آية ${a.number}: نص فارغ")
            if (!scenes.containsKey(a.sceneId)) out.add("آية ${a.number}: مشهد ${a.sceneId} غير موجود")
        }
        scenes.values.forEach { sc ->
            val beatAyahs = sc.beats.map { it.ayah }.sorted()
            if (beatAyahs != sc.ayahs.sorted()) out.add("مشهد ${sc.id}: نبضات المعنى لا تغطي آياته")
            val ids = sc.actors.map { it.id }.toSet()
            sc.beats.forEach { b ->
                if (b.meaning.isBlank() || b.visualEvent.isBlank() || b.rationale.isBlank() || b.tafsirQuote.isBlank()) {
                    out.add("مشهد ${sc.id} آية ${b.ayah}: ربط المعنى بالحدث ناقص")
                }
                if (b.symbolic && b.symbolicNote.isBlank()) out.add("مشهد ${sc.id} آية ${b.ayah}: تمثيل رمزي بلا توضيح")
                b.keys.forEach { k -> k.set.keys.forEach { id -> if (id !in ids) out.add("مشهد ${sc.id}: عنصر غير معرّف $id") } }
            }
        }
        val covered = scenes.values.flatMap { it.ayahs }.toSet()
        if (covered != module.ayahs.map { it.number }.toSet()) out.add("المشاهد لا تغطي الآيات بالضبط")
        val src = module.text.sha256OfSourceLines
        if (src.isNotEmpty()) {
            val lines = module.ayahs.map { it.text }.toMutableList()
            if (module.basmala != null && lines.isNotEmpty()) lines[0] = module.basmala + " " + lines[0]
            if (!sha256Hex(lines.joinToString("\n").toByteArray(Charsets.UTF_8)).equals(src, ignoreCase = true)) {
                out.add("النص لا يطابق بصمة المصدر")
            }
        }
        return out
    }

    /** السورة قابلة للاعتماد فقط إن زالت كل المعوّقات. */
    fun isFullyApproved(module: ModuleFile, scenes: Map<String, SceneDef>, tafsir: List<TafsirRecord>): Boolean =
        module.review.blockers.isEmpty() &&
            scenes.values.all { it.review.status == "approved" } &&
            module.ayahs.all { it.audio.status == "verified" } &&
            tafsir.all { it.attributionReview == "approved" }
}
