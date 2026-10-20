package app.quranichorizons

import app.quranichorizons.data.OnlineAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineAudioTest {
    private fun m(url: String, key: String = "114:1", source: String = "Quran.Foundation") =
        """{"source":"$source","reciterName":"X","recitationId":7,"files":[{"verseKey":"$key","url":"$url"}]}"""

    @Test fun acceptsHttpsFromAllowedHost() {
        val r = OnlineAudio.parse(m("https://audio.qurancdn.com/a/b.mp3"), 114)
        assertNotNull(r); assertEquals("Quran.Foundation", r!!.source)
    }
    @Test fun rejectsHttpUnknownHostWrongSurahOrSource() {
        assertNull(OnlineAudio.parse(m("http://audio.qurancdn.com/a.mp3"), 114))
        assertNull(OnlineAudio.parse(m("https://evil.example.com/a.mp3"), 114))
        assertNull(OnlineAudio.parse(m("https://notqurancdn.com/a.mp3"), 114))
        assertNull(OnlineAudio.parse(m("https://audio.qurancdn.com/a.mp3", key = "113:1"), 114))
        assertNull(OnlineAudio.parse(m("https://audio.qurancdn.com/a.mp3", source = "EveryAyah"), 114))
        assertNull(OnlineAudio.parse("{}", 114))
    }
}
