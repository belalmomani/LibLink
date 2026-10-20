package app.quranichorizons

import app.quranichorizons.data.SceneFile
import app.quranichorizons.scene.SceneTimeline
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** اختبارات محرك الزمن: المعنى يغيّر عناصر المشهد نفسها، والحالة تُحمَل بين الآيات. */
class TimelineTest {
    private val json = Json { ignoreUnknownKeys = true }
    // المشاهد الإجرائية البديلة (المحرك نفسه) في content/drafts، والمشهد الحالي بالصور في الوحدة
    private val scenes = json.decodeFromString(
        SceneFile.serializer(), File("../content/drafts/an-nas-procedural-scenes.json").readText(Charsets.UTF_8),
    ).scenes.associateBy { it.id }
    private val imageScene = json.decodeFromString(
        SceneFile.serializer(), File("../content/bootstrap/modules/114/scenes.json").readText(Charsets.UTF_8),
    ).scenes.first()
    private val refuge = scenes.getValue("n-refuge")
    private val whisper = scenes.getValue("n-whisper")

    private fun v(st: Map<String, Map<String, Float>>, id: String, k: String) = st.getValue(id).getValue(k)

    @Test fun ayah1EvilsApproachThenAreRepelledByProtectiveDome() {
        val mid = SceneTimeline.resolve(refuge, 1, 0.45f)
        assertTrue(v(mid, "shade", "r") > 0.5f)
        val end = SceneTimeline.resolve(refuge, 1, 1f)
        assertEquals(1f, v(end, "dome", "draw"), 0.001f)
        assertEquals(0f, v(end, "shade", "r"), 0.001f)
        assertEquals(1f, v(end, "man", "a"), 0.001f)
        assertEquals(0f, v(end, "crowd", "a"), 0.001f)
    }

    @Test fun ayah2OnePersonBecomesAllPeopleBoundToOneSource() {
        val s0 = SceneTimeline.resolve(refuge, 2, 0f)
        assertEquals(1f, v(s0, "man", "a"), 0.001f)       // الحالة تُحمَل من الآية 1
        val end = SceneTimeline.resolve(refuge, 2, 1f)
        assertEquals(0f, v(end, "man", "a"), 0.001f)
        assertEquals(1f, v(end, "crowd", "a"), 0.001f)
        assertEquals(1f, v(end, "crowd", "bind"), 0.001f)
    }

    @Test fun ayah3OtherLightsGoOutOneBeamRemainsPeopleFaceIt() {
        val s0 = SceneTimeline.resolve(refuge, 3, 0f)
        assertEquals(1f, v(s0, "crowd", "bind"), 0.001f)
        val mid = SceneTimeline.resolve(refuge, 3, 0.45f)
        assertTrue(v(mid, "dim", "a") > 0.9f)
        val end = SceneTimeline.resolve(refuge, 3, 1f)
        assertEquals(0f, v(end, "dim", "a"), 0.001f)
        assertEquals(1f, v(end, "beam", "h"), 0.001f)
        assertEquals(1f, v(end, "crowd", "rows"), 0.001f)
        assertEquals(0f, v(end, "crowd", "bind"), 0.001f)
    }

    @Test fun ayah4WispsApproachThenRetreatWhenLanternFlares() {
        val mid = SceneTimeline.resolve(whisper, 4, 0.45f)
        assertTrue(v(mid, "w1", "r") > 0.5f)
        val end = SceneTimeline.resolve(whisper, 4, 1f)
        assertEquals(0f, v(end, "w1", "r"), 0.001f)
        assertEquals(1f, v(end, "lantern", "g"), 0.001f)
    }

    @Test fun ayah5WhisperReachesChest() {
        val end = SceneTimeline.resolve(whisper, 5, 1f)
        assertEquals(1f, v(end, "w2", "r"), 0.001f)
        assertEquals(1f, v(end, "man", "chest"), 0.001f)
    }

    @Test fun ayah6TwoNewSourcesAppear() {
        assertEquals(0f, v(SceneTimeline.resolve(whisper, 6, 0f), "smoke", "a"), 0.001f)
        val end = SceneTimeline.resolve(whisper, 6, 1f)
        assertEquals(1f, v(end, "smoke", "a"), 0.001f)
        assertEquals(1f, v(end, "other", "a"), 0.001f)
        assertEquals(1f, v(end, "wU", "r"), 0.001f)
        assertEquals(1f, v(end, "wP", "r"), 0.001f)
    }

    @Test fun everyBeatHasMeaningEventRationaleAndRealQuote() {
        for (sc in scenes.values) for (b in sc.beats) {
            assertTrue(b.meaning.isNotBlank() && b.visualEvent.isNotBlank() && b.rationale.isNotBlank() && b.tafsirQuote.isNotBlank())
            if (b.symbolic) assertTrue(b.symbolicNote.isNotBlank())
        }
    }

    @Test fun imageSceneCrossfadesOneShotPerAyah() {
        assertEquals(6, imageScene.actors.count { it.kind == "image" })
        // بداية الآية 1: لا لقطة ظاهرة بعد، ونهايتها: لقطة الآية 1 وحدها
        assertEquals(0f, v(SceneTimeline.resolve(imageScene, 1, 0f), "img1", "a"), 0.001f)
        val e1 = SceneTimeline.resolve(imageScene, 1, 1f)
        assertEquals(1f, v(e1, "img1", "a"), 0.001f)
        for (k in 2..6) {
            val start = SceneTimeline.resolve(imageScene, k, 0f)
            assertEquals(1f, v(start, "img${k - 1}", "a"), 0.001f)   // تُحمَل لقطة الآية السابقة
            assertEquals(0f, v(start, "img$k", "a"), 0.001f)
            val end = SceneTimeline.resolve(imageScene, k, 1f)
            assertEquals(0f, v(end, "img${k - 1}", "a"), 0.001f)
            assertEquals(1f, v(end, "img$k", "a"), 0.001f)
        }
    }
}
