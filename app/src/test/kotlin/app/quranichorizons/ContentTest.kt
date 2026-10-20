package app.quranichorizons

import app.quranichorizons.data.Catalog
import app.quranichorizons.data.ContentVerifier
import app.quranichorizons.data.ModuleFile
import app.quranichorizons.data.SceneFile
import app.quranichorizons.data.SurahsFile
import app.quranichorizons.data.TafsirFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** اختبارات JVM على محتوى content/bootstrap (يُشغَّل الاختبار من مجلد app). */
class ContentTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val root = File("../content/bootstrap")
    private fun read(p: String) = File(root, p).readText(Charsets.UTF_8)

    private val surahs = json.decodeFromString(SurahsFile.serializer(), read("surahs.json"))
    private val catalog = json.decodeFromString(Catalog.serializer(), read("catalog.json"))

    @Test fun surahListIs114With6236Ayahs() {
        assertEquals(114, surahs.surahs.size)
        assertEquals(6236, surahs.surahs.sumOf { it.ayahCount })
        assertEquals((1..114).toList(), surahs.surahs.map { it.number })
    }

    @Test fun deliveryOrderIsReverseMushaf() {
        assertEquals((114 downTo 1).toList(), surahs.deliveryOrder)
    }

    @Test fun catalogHashesMatchFiles() {
        for (m in catalog.modules) for (f in m.files) {
            val b = File(root, f.path).readBytes()
            assertTrue("${f.path} بصمة/حجم", ContentVerifier.fileMatches(f, b))
        }
    }

    @Test fun tamperedFileIsRejected() {
        val f = catalog.modules.first().files.first()
        val b = File(root, f.path).readBytes().copyOf()
        b[b.size / 2] = (b[b.size / 2] + 1).toByte()
        assertFalse(ContentVerifier.fileMatches(f, b))
    }

    @Test fun anNasModuleIsStructurallySoundAndNotApproved() {
        val m = catalog.modules.first { it.surah == 114 }
        val info = surahs.surahs.first { it.number == 114 }
        val module = json.decodeFromString(ModuleFile.serializer(), read("${m.dir}/module.json"))
        val scenes = json.decodeFromString(SceneFile.serializer(), read("${m.dir}/scenes.json")).scenes.associateBy { it.id }
        val tafsir = json.decodeFromString(TafsirFile.serializer(), read("${m.dir}/tafsir.json")).records
        assertEquals(emptyList<String>(), ContentVerifier.structureProblems(info, module, scenes))
        assertEquals(6, module.ayahs.size)
        // لا اعتماد قبل زوال المعوّقات
        assertFalse(ContentVerifier.isFullyApproved(module, scenes, tafsir))
        assertTrue(m.status != "approved")
        assertTrue(tafsir.all { it.attributionReview != "approved" })
    }

    @Test fun structureDetectsMissingScene() {
        val m = catalog.modules.first { it.surah == 114 }
        val info = surahs.surahs.first { it.number == 114 }
        val module = json.decodeFromString(ModuleFile.serializer(), read("${m.dir}/module.json"))
        val scenes = json.decodeFromString(SceneFile.serializer(), read("${m.dir}/scenes.json")).scenes.associateBy { it.id }
        val broken = scenes - scenes.keys.first()
        assertTrue(ContentVerifier.structureProblems(info, module, broken).isNotEmpty())
    }

    @Test fun beatQuotesAreVerbatimFromTheirTafsirRecord() {
        val m = catalog.modules.first { it.surah == 114 }
        val tafsir = json.decodeFromString(TafsirFile.serializer(), read("${m.dir}/tafsir.json")).records.associateBy { it.id }
        val scenes = json.decodeFromString(SceneFile.serializer(), read("${m.dir}/scenes.json")).scenes
        for (sc in scenes) for (b in sc.beats) {
            val rec = tafsir.getValue(b.tafsirRecord)
            assertTrue("آية ${b.ayah}", b.ayah in rec.ayahs && rec.text.contains(b.tafsirQuote))
        }
    }
}
